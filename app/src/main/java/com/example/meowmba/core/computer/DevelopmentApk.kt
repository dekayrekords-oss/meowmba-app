package com.example.meowmba.core.computer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class DevelopmentApkReceipt(val token: String, val sha256: String, val bytes: Long) {
    val line get() = "MEOW_APK $token $sha256 $bytes"
    companion object {
        const val MAX_BYTES = 512L * 1024 * 1024
        private val pattern = Regex("MEOW_APK ([0-9a-f]{48}) ([0-9a-f]{64}) ([0-9]{1,10})")
        fun parse(line: String): DevelopmentApkReceipt? {
            val match = pattern.matchEntire(line.trim()) ?: return null
            val bytes = match.groupValues[3].toLongOrNull()?.takeIf { it in 1..MAX_BYTES } ?: return null
            return DevelopmentApkReceipt(match.groupValues[1], match.groupValues[2], bytes)
        }
        fun latest(transcript: String) = transcript.lineSequence().mapNotNull(::parse).lastOrNull()
    }
}

/** Explicit, hash-verified transfer from the temporary build export. No remote URL input.
 * The receipt is provenance for transfer, NOT proof an app is safe or permission to install. */
internal object DevelopmentApk {
    data class Prepared(val file: File, val packageName: String, val version: String, val sha256: String, val bytes: Long)

    suspend fun fetch(context: Context, receipt: DevelopmentApkReceipt): Prepared = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "development-apks")
        check(directory.mkdirs() || directory.isDirectory) { "APK cache is unavailable." }
        check(directory.listFiles().orEmpty().sumOf { it.length() } + receipt.bytes <= 1024L * 1024 * 1024) {
            "Developer APK cache reached 1 GiB. Remove old developer downloads before fetching another."
        }
        val pending = File.createTempFile("build-", ".apk", directory)
        val connection = URL("http://127.0.0.1:8766/${receipt.token}").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Cache-Control", "no-store")
            check(connection.responseCode == 200) { "Build export unavailable. Keep Development Studio open and rebuild/export the APK." }
            check(connection.contentLengthLong == receipt.bytes) { "APK size differs from the build receipt." }
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            connection.inputStream.use { input ->
                pending.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        check(count <= receipt.bytes) { "APK exceeds the expected size." }
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            check(count == receipt.bytes && hash == receipt.sha256) { "APK verification failed. Nothing was installed." }
            val info = context.packageManager.getPackageArchiveInfo(pending.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            check(info != null && info.signingInfo != null) { "The downloaded file is not a signed Android APK." }
            check(info.packageName !in setOf(context.packageName, "com.meow.meowmba", "com.example.meowmba")) {
                "Development Studio cannot replace Meowmba itself. Use the separately verified app-update workflow."
            }
            Prepared(pending, info.packageName, "${info.versionName ?: "unknown"} (${info.longVersionCode})", hash, count)
        } catch (error: Throwable) {
            pending.delete() // Only this operation's temporary download, never project files.
            throw error
        } finally { connection.disconnect() }
    }

    fun allowInstallIntent(context: Context) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun discardDownloads(context: Context): Int = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "development-apks")
        directory.listFiles().orEmpty().count { file ->
            file.name.matches(Regex("build-[0-9]+\\.apk")) && file.canonicalFile.parentFile == directory.canonicalFile && file.delete()
        }
    }

    /** Opens Android's confirmation screen; never claims installation completed. */
    suspend fun requestInstall(context: Context, prepared: Prepared) = withContext(Dispatchers.IO) {
        check(context.packageName == "com.example.meowmba") { "Developer APK installation is available in the preview edition only." }
        check(context.packageManager.canRequestPackageInstalls()) { "Enable installation from Meow in Android settings, then tap Install again." }
        check(prepared.file.isFile && prepared.file.length() == prepared.bytes) { "APK download is no longer available. Fetch it again." }
        val hash = prepared.file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        check(hash == prepared.sha256) { "APK changed after review. Fetch it again." }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", prepared.file)
        withContext(Dispatchers.Main) {
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
}
