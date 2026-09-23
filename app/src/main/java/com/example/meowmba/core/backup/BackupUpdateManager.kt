package com.example.meowmba.core.backup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/** All disk-heavy methods run on Dispatchers.IO. State is never included in exported preferences. */
class BackupUpdateManager(private val context: Context) {
    private val pm = context.packageManager
    private val state = context.getSharedPreferences("backup_update_state", Context.MODE_PRIVATE)
    private val work get() = File(context.noBackupFilesDir, "backup_work").apply { mkdirs() }
    private val stagedApk get() = File(context.cacheDir, "meow_update/candidate.apk")
    private fun installed(name: String): PackageInfo? = try { pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES) } catch (_: PackageManager.NameNotFoundException) { null }
    private fun current() = requireNotNull(installed(context.packageName))
    private fun metadata() = current().let { BackupMetadata(it.packageName, it.longVersionCode, it.versionName.orEmpty(), System.currentTimeMillis()) }
    fun otherEdition(): PackageInfo? = BackupUpdatePolicy.counterpart(context.packageName)?.let(::installed)
    fun label(pkg: String): String = if (pkg == BackupUpdatePolicy.PLAY) "Meow from Play Store" else "Meow preview"
    val allowsApkUpdates: Boolean get() = context.packageName == BackupUpdatePolicy.PREVIEW

    fun exportBackup(uri: Uri, password: CharArray): BackupDataSummary {
        synchronized(OPERATION_LOCK) {
        val dir = File(work, "export-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        // A failed attempt invalidates the previous update authorization.
        state.edit().remove("export_uri").remove("export_hash").commit()
        try {
            val summary = UserDataBackupStore(context).snapshot(dir)
            val info = metadata()
            val manifest = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use {
                EncryptedBackupArchive.write(dir, it, password, info)
            }
            val initialDigest = documentHash(uri)
            // Re-open the external document: success writing is not proof that it can be restored.
            val verified = requireNotNull(context.contentResolver.openInputStream(uri)).use {
                EncryptedBackupArchive.read(it, password, work)
            }
            try { require(verified.manifest == manifest) { "Backup verification failed" } }
            finally { verified.directory.deleteRecursively() }
            val digest = documentHash(uri)
            require(digest == initialDigest) { "Backup changed during verification" }
            check(state.edit().putString("export_uri", uri.toString()).putString("export_hash", digest)
                .putLong("export_version", info.versionCode).putLong("export_time", System.currentTimeMillis()).commit())
            return summary
        } finally { dir.deleteRecursively() }
        }
    }

    fun importBackup(uri: Uri, password: CharArray): BackupRestoreResult {
        synchronized(OPERATION_LOCK) {
        state.edit().remove("import_source").remove("import_hash").remove("import_uri")
            .remove("import_complete").remove("import_version").remove("import_created").commit()
        val beforeHash = documentHash(uri)
        val unpacked = requireNotNull(context.contentResolver.openInputStream(uri)).use {
            EncryptedBackupArchive.read(it, password, work)
        }
        try {
            val info = unpacked.manifest.metadata
            require(info.versionCode <= current().longVersionCode) { "Update Meow before restoring a backup from a newer version" }
            require(documentHash(uri) == beforeHash) { "The backup changed while being read" }
            val result = UserDataBackupStore(context).restore(unpacked.directory)
            if (result.complete) {
                check(state.edit().putString("import_source", info.sourcePackage).putLong("import_version", info.versionCode)
                    .putLong("import_created", info.createdAt).putString("import_uri", uri.toString())
                    .putString("import_hash", beforeHash).putBoolean("import_complete", true).commit())
            }
            return result
        } finally { unpacked.directory.deleteRecursively() }
        }
    }

    fun stageUpdate(uri: Uri): String {
        synchronized(OPERATION_LOCK) {
        check(allowsApkUpdates)
        state.edit().remove("apk_hash").commit()
        stagedApk.parentFile?.mkdirs()
        try {
            var bytes = 0L
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                stagedApk.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        bytes += n; require(bytes <= MAX_APK_BYTES) { "APK is too large" }
                        output.write(buffer, 0, n)
                    }
                }
            }
            val candidate = validateStagedApk()
            check(state.edit().putString("apk_hash", fileHash(stagedApk)).putLong("apk_version", candidate.longVersionCode).commit())
            return "Version ${candidate.versionName.orEmpty()} (${candidate.longVersionCode}) selected. Save a verified backup before installing."
        } catch (e: Exception) { stagedApk.delete(); throw e }
        }
    }

    private fun validateStagedApk(): PackageInfo {
        val candidate = requireNotNull(pm.getPackageArchiveInfo(stagedApk.path, PackageManager.GET_SIGNING_CERTIFICATES)) { "This file is not a readable APK" }
        val now = current()
        BackupUpdatePolicy.updateProblem(now.packageName, now.longVersionCode, signers(now), candidate.packageName,
            candidate.longVersionCode, signers(candidate))?.let { throw IllegalArgumentException(it) }
        return candidate
    }

    fun hasSelectedApk(): Boolean = stagedApk.isFile && state.contains("apk_hash") && state.getLong("apk_version", 0) > current().longVersionCode
    fun canRequestInstalls(): Boolean = pm.canRequestPackageInstalls()

    fun prepareInstall(): Intent {
        synchronized(OPERATION_LOCK) {
        check(allowsApkUpdates)
        validateStagedApk()
        require(state.getString("apk_hash", null) == fileHash(stagedApk)) { "Choose the APK again; it changed" }
        require(state.getLong("export_version", -1) == current().longVersionCode &&
            System.currentTimeMillis() - state.getLong("export_time", 0) in 0..15 * 60 * 1000L) { "Save a fresh backup before installing" }
        require(verifiedDocument("export")) { "The saved backup is missing or changed. Make another backup" }
        require(canRequestInstalls()) { "Allow Meow to install this update in Android settings, then return here" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", stagedApk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun cleanupProblem(confirmed: Boolean, verifyDocument: Boolean = false): String? {
        val other = otherEdition()
        val readable = if (verifyDocument) verifiedDocument("import") else state.contains("import_hash")
        val problem = BackupUpdatePolicy.cleanupProblem(context.packageName, current().longVersionCode, other?.packageName,
            other?.longVersionCode, state.getString("import_source", null), state.getLong("import_version", -1),
            state.getBoolean("import_complete", false), readable, confirmed)
        if (problem != null) return problem
        return if (other!!.lastUpdateTime > state.getLong("import_created", 0)) "The other edition was updated after this backup. Back it up and restore again first." else null
    }

    fun prepareCleanup(confirmed: Boolean): Intent {
        synchronized(OPERATION_LOCK) {
        cleanupProblem(confirmed, verifyDocument = true)?.let { throw IllegalStateException(it) }
        return Intent(Intent.ACTION_DELETE, Uri.parse("package:${requireNotNull(otherEdition()).packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun clearFinishedInstaller() {
        if (state.getLong("apk_version", Long.MAX_VALUE) <= current().longVersionCode) {
            stagedApk.delete()
            state.edit().remove("apk_hash").remove("apk_version").apply()
        }
    }

    private fun verifiedDocument(prefix: String): Boolean = try {
        val saved = state.getString("${prefix}_uri", null)
        saved != null && documentHash(Uri.parse(saved)) == state.getString("${prefix}_hash", null)
    } catch (_: Exception) { false }

    private fun documentHash(uri: Uri): String = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val b = ByteArray(64 * 1024); var size = 0L
        while (true) { val n = input.read(b); if (n < 0) break; size += n; require(size <= MAX_BACKUP_BYTES); digest.update(b, 0, n) }
        digest.digest().hex()
    }
    private fun fileHash(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val b = ByteArray(64 * 1024)
        while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) }; digest.digest().hex()
    }
    private fun signers(info: PackageInfo): Set<String> = info.signingInfo?.apkContentsSigners.orEmpty()
        .map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    companion object { private val OPERATION_LOCK = Any(); private const val MAX_APK_BYTES = 512L * 1024 * 1024; private const val MAX_BACKUP_BYTES = 1100L * 1024 * 1024 }
}
