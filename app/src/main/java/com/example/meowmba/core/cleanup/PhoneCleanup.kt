package com.example.meowmba.core.cleanup

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.meowmba.core.agent.tools.InstalledApps
import com.example.meowmba.ui.companion.DrawerApp
import com.example.meowmba.ui.companion.DrawerCatalog
import com.example.meowmba.ui.companion.DrawerFolder
import java.util.concurrent.atomic.AtomicReference

/**
 * Inventories junk on the phone and prepares a tidy plan.
 *
 * Deletes never happen silently: media goes through [MediaStore.createDeleteRequest]
 * (system confirm sheet). Apps open the uninstall prompt. Messages open Messages.
 */
object PhoneCleanup {

    data class Item(
        val kind: String,
        val label: String,
        val bytes: Long,
        val why: String,
        val uri: String? = null,
        val packageName: String? = null,
    )

    data class Plan(
        val kind: String,
        val items: List<Item>,
        val notes: List<String>,
        val atMs: Long = System.currentTimeMillis(),
    ) {
        val bytes: Long get() = items.sumOf { it.bytes }
        fun fresh(now: Long = System.currentTimeMillis()): Boolean =
            now - atMs < 5 * 60_000L
    }

    private val lastPlan = AtomicReference<Plan?>(null)

    fun pendingFresh(): Plan? = lastPlan.get()?.takeIf { it.fresh() }

    fun remember(plan: Plan) = lastPlan.set(plan)

    fun clearPending() = lastPlan.set(null)

    fun hasMediaPermission(context: Context): Boolean {
        val perms = mediaPermissions()
        return perms.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun mediaPermissions(): Array<String> = buildList {
        add(android.Manifest.permission.READ_MEDIA_IMAGES)
        add(android.Manifest.permission.READ_MEDIA_VIDEO)
        add(android.Manifest.permission.READ_MEDIA_AUDIO)
        if (Build.VERSION.SDK_INT >= 34) {
            add(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
    }.toTypedArray()

    fun scan(context: Context, kind: String): Plan {
        val notes = mutableListOf<String>()
        val items = mutableListOf<Item>()
        val want = kind.lowercase()
        val mediaOk = hasMediaPermission(context)
        if (!mediaOk && want in setOf("all", "photos", "videos", "music", "documents", "files")) {
            notes += "Need photo/file access to scan media. Allow it and ask again."
        }
        if (mediaOk) {
            if (want in setOf("all", "photos")) items += scanImages(context)
            if (want in setOf("all", "videos")) items += scanVideos(context)
            if (want in setOf("all", "music")) items += scanAudio(context)
            if (want in setOf("all", "documents", "files")) items += scanDownloads(context)
        }
        if (want in setOf("all", "apps")) items += scanApps(context, notes)
        if (want in setOf("all", "messages")) {
            notes += "I can't delete texts unless I'm the default Messages app. I can open Messages so you can archive old threads."
        }
        storageNote(context)?.let { notes += it }
        val plan = Plan(kind = want, items = items.sortedByDescending { it.bytes }, notes = notes)
        remember(plan)
        return plan
    }

    fun format(plan: Plan): String {
        if (plan.items.isEmpty() && plan.notes.isEmpty()) {
            return "Nothing obvious to clean in ${plan.kind}."
        }
        val head = if (plan.items.isEmpty()) {
            "Looked through ${plan.kind}."
        } else {
            "Found ${plan.items.size} thing${if (plan.items.size == 1) "" else "s"} " +
                "(${DrawerCatalog.formatSize(plan.bytes)}) worth a look."
        }
        val top = plan.items.take(8).joinToString("\n") { item ->
            val size = if (item.bytes > 0) " · ${DrawerCatalog.formatSize(item.bytes)}" else ""
            "• ${item.label}$size — ${item.why}"
        }
        val more = if (plan.items.size > 8) "\n…and ${plan.items.size - 8} more." else ""
        val extra = if (plan.notes.isEmpty()) "" else "\n" + plan.notes.joinToString("\n")
        val ask = if (plan.items.any { it.uri != null || it.packageName != null }) {
            "\nSay \"clean them\" and I'll ask Android to confirm each delete."
        } else ""
        return "$head\n$top$more$extra$ask".trim()
    }

    fun organizeApps(existing: List<DrawerFolder>, apps: List<DrawerApp>): Pair<List<DrawerFolder>, String> {
        val (next, added) = DrawerCatalog.organizeByType(existing, apps) {
            "org-${System.currentTimeMillis().toString(36)}-${it}"
        }
        val msg = if (added == 0) {
            "Apps are already sorted into folders."
        } else {
            "Sorted $added app${if (added == 1) "" else "s"} into type folders (Games, Social, and the rest)."
        }
        return next to msg
    }

    fun deleteUris(plan: Plan): List<Uri> =
        plan.items.mapNotNull { it.uri?.let(Uri::parse) }.take(40)

    fun unusedPackages(plan: Plan): List<Pair<String, String>> =
        plan.items.mapNotNull { item ->
            val pkg = item.packageName ?: return@mapNotNull null
            pkg to item.label
        }.take(5)

    private fun scanImages(context: Context): List<Item> {
        val out = mutableListOf<Item>()
        val cutoff = (System.currentTimeMillis() / 1000L) - 60L * 24 * 3600
        query(
            context,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.RELATIVE_PATH,
            ),
        ) { c, id, name, size, added, path ->
            val shot = name.contains("screenshot", true) ||
                path.contains("screenshot", true)
            val big = size >= 8L * 1024 * 1024
            val oldShot = shot && added in 1 until cutoff
            if (!oldShot && !big) return@query
            val why = when {
                oldShot -> "old screenshot"
                else -> "large photo"
            }
            out += Item(
                    kind = "photos",
                    label = name.ifBlank { "Photo" },
                    bytes = size,
                    why = why,
                    uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id,
                    ).toString(),
                )
        }
        return out
    }

    private fun scanVideos(context: Context): List<Item> {
        val out = mutableListOf<Item>()
        query(
            context,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
            ),
        ) { _, id, name, size, _, _ ->
            if (size < 200L * 1024 * 1024) return@query
            out += Item(
                kind = "videos",
                label = name.ifBlank { "Video" },
                bytes = size,
                why = "large video",
                uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id,
                ).toString(),
            )
        }
        return out
    }

    private fun scanAudio(context: Context): List<Item> {
        val seen = mutableMapOf<String, Item>()
        val dups = mutableListOf<Item>()
        query(
            context,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
            ),
        ) { _, id, name, size, _, _ ->
            val key = name.lowercase()
            val item = Item(
                kind = "music",
                label = name.ifBlank { "Track" },
                bytes = size,
                why = "duplicate title",
                uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id,
                ).toString(),
            )
            val first = seen[key]
            if (first != null) {
                dups += item
            } else {
                seen[key] = item
            }
        }
        return dups.take(20)
    }

    private fun scanDownloads(context: Context): List<Item> {
        val out = mutableListOf<Item>()
        val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val cutoff = (System.currentTimeMillis() / 1000L) - 14L * 24 * 3600
        query(
            context,
            uri,
            arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_ADDED,
                MediaStore.Downloads.RELATIVE_PATH,
            ),
        ) { _, id, name, size, added, _ ->
            val lower = name.lowercase()
            val junkExt = lower.endsWith(".apk") || lower.endsWith(".zip") ||
                lower.endsWith(".tmp") || lower.endsWith(".crdownload")
            val old = added in 1 until cutoff
            val big = size >= 50L * 1024 * 1024
            if (!junkExt && !big) return@query
            val why = when {
                lower.endsWith(".apk") -> "downloaded APK"
                junkExt && old -> "old download"
                big -> "large download"
                else -> "download"
            }
            out += Item(
                kind = "documents",
                label = name.ifBlank { "Download" },
                bytes = size,
                why = why,
                uri = android.content.ContentUris.withAppendedId(uri, id).toString(),
            )
        }
        return out
    }

    private fun scanApps(context: Context, notes: MutableList<String>): List<Item> {
        val usage = lastUsedMap(context)
        if (usage == null) {
            notes += "For unused-app ages, allow Usage access in Settings."
        }
        val now = System.currentTimeMillis()
        val self = context.packageName
        return InstalledApps.all(context).mapNotNull { app ->
            if (app.packageName == self) return@mapNotNull null
            val ai = runCatching {
                context.packageManager.getApplicationInfo(app.packageName, 0)
            }.getOrNull() ?: return@mapNotNull null
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) return@mapNotNull null
            val size = runCatching { java.io.File(ai.sourceDir).length() }.getOrDefault(0L)
            val last = usage?.get(app.packageName) ?: 0L
            val unused = usage != null && (last == 0L || now - last > 60L * 24 * 3600 * 1000)
            val huge = size >= 400L * 1024 * 1024
            if (!unused && !huge) return@mapNotNull null
            Item(
                kind = "apps",
                label = app.label,
                bytes = size,
                why = if (unused) "not opened in 60 days" else "large app",
                packageName = app.packageName,
            )
        }.sortedByDescending { it.bytes }.take(12)
    }

    private fun lastUsedMap(context: Context): Map<String, Long>? {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return null
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        if (mode != AppOpsManager.MODE_ALLOWED) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val end = System.currentTimeMillis()
        val start = end - 90L * 24 * 3600 * 1000
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
            .associate { it.packageName to it.lastTimeUsed }
    }

    private fun storageNote(context: Context): String? {
        val free = runCatching {
            android.os.StatFs(context.filesDir.path).availableBytes
        }.getOrNull() ?: return null
        return if (free < 2L * 1024 * 1024 * 1024) "Free space looks tight." else null
    }

    private fun query(
        context: Context,
        uri: Uri,
        projection: Array<String>,
        visit: (c: android.database.Cursor, id: Long, name: String, size: Long, added: Long, path: String) -> Unit,
    ) {
        val resolver = context.contentResolver
        runCatching {
            resolver.query(uri, projection, null, null, "${projection[2]} DESC")
        }.getOrNull()?.use { c ->
            val idIx = c.getColumnIndex(projection[0])
            val nameIx = c.getColumnIndex(projection[1])
            val sizeIx = c.getColumnIndex(projection[2])
            val addedIx = if (projection.size > 3) c.getColumnIndex(projection[3]) else -1
            val pathIx = if (projection.size > 4) c.getColumnIndex(projection[4]) else -1
            var n = 0
            while (c.moveToNext() && n < 400) {
                n++
                val id = if (idIx >= 0) c.getLong(idIx) else continue
                val name = if (nameIx >= 0) c.getString(nameIx).orEmpty() else ""
                val size = if (sizeIx >= 0) c.getLong(sizeIx) else 0L
                val added = if (addedIx >= 0) c.getLong(addedIx) else 0L
                val path = if (pathIx >= 0) c.getString(pathIx).orEmpty() else ""
                visit(c, id, name, size, added, path)
            }
        }
    }

    fun openMessages(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(
            Telephony.Sms.getDefaultSmsPackage(context) ?: "com.google.android.apps.messaging",
        ) ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openDownloads(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "*/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            context.startActivity(
                Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Ask the default compatible browser first; fall back to Android's picker. */
    fun openSuggestions(context: Context, plan: Plan): String {
        val count = plan.items.count { it.uri != null }
        if (count == 0) return "No file suggestions to open."
        val authority = "${context.packageName}.cleanup.documents"
        val root = android.provider.DocumentsContract.buildRootUri(authority, "suggestions")
        val folder = android.provider.DocumentsContract.buildDocumentUri(authority, "suggestions")
        context.contentResolver.notifyChange(
            android.provider.DocumentsContract.buildChildDocumentsUri(authority, "suggestions"), null)
        val browse = Intent(Intent.ACTION_VIEW).setDataAndType(root, "vnd.android.document/root")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(browse) }.isSuccess) {
            return "Opened Cleanup suggestions in the file browser with $count files for review. Nothing was deleted."
        }
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, folder)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (runCatching { context.startActivity(picker) }.isSuccess) {
            "Opened Android's file picker. Choose Cleanup suggestions if it opens elsewhere. Review $count files, then return to Memba to confirm cleanup. Nothing was deleted."
        } else "Could not open a compatible file browser. Your suggestions are listed here; nothing was deleted."
    }

    fun openUsageAccess(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun uninstall(context: Context, packageName: String) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Hands media-delete URIs to [com.example.meowmba.MainActivity]. */
object CleanupGate {
    @Volatile var pendingUris: List<Uri> = emptyList()
        private set
    val tick = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun offer(uris: List<Uri>) {
        pendingUris = uris
        tick.value = tick.value + 1
    }

    fun take(): List<Uri> {
        val u = pendingUris
        pendingUris = emptyList()
        return u
    }
}
