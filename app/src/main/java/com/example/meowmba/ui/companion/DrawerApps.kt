package com.example.meowmba.ui.companion

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File

/**
 * Launcher list for the swipe-up drawer.
 *
 * The PackageManager scan is slow on a loaded Fold (hundreds of apps, and
 * it competes with camera + STT). Disk cache makes the first paint instant;
 * [warm] runs at process start so swipe-up does not wait at all.
 */
object DrawerApps {

    @Volatile
    var cached: List<DrawerApp> = emptyList()
        private set

    fun snapshot(context: Context? = null): List<DrawerApp> {
        if (cached.isEmpty() && context != null) {
            val disk = readDisk(context.applicationContext)
            if (disk.isNotEmpty()) cached = disk
        }
        return cached
    }

    fun warm(context: Context) {
        val appCtx = context.applicationContext
        if (cached.isEmpty()) {
            val disk = readDisk(appCtx)
            if (disk.isNotEmpty()) cached = disk
        }
        val fast = loadFast(appCtx)
        val merged = merge(cached, fast)
        cached = merged
        writeDisk(appCtx, merged)
        val needSize = merged.any { it.sizeBytes == 0L && it.installedAtMs == 0L }
        if (needSize) {
            val rich = enrich(appCtx, merged)
            cached = rich
            writeDisk(appCtx, rich)
        }
    }

    fun loadFast(context: Context): List<DrawerApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val label = info.loadLabel(pm)?.toString() ?: return@mapNotNull null
                val pkg = info.activityInfo.packageName
                val search = searchTextFor(label, pkg, info)
                DrawerApp(
                    label = label,
                    packageName = pkg,
                    searchText = search,
                    type = drawerTypeOf(info.activityInfo.applicationInfo.category, search),
                )
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun enrich(context: Context, apps: List<DrawerApp>): List<DrawerApp> {
        if (apps.isEmpty()) return apps
        val pm = context.packageManager
        val installedAt = runCatching {
            pm.getInstalledPackages(0).associate { it.packageName to it.firstInstallTime }
        }.getOrDefault(emptyMap())
        val infos = runCatching {
            pm.getInstalledApplications(0).associateBy { it.packageName }
        }.getOrDefault(emptyMap())
        return apps.map { app ->
            val ai = infos[app.packageName]
            app.copy(
                installedAtMs = installedAt[app.packageName] ?: 0L,
                sizeBytes = ai?.let { apkSize(it) } ?: 0L,
            )
        }
    }

    fun merge(cached: List<DrawerApp>, fast: List<DrawerApp>): List<DrawerApp> {
        if (cached.isEmpty()) return fast
        val old = cached.associateBy { it.packageName }
        return fast.map { fresh ->
            val prev = old[fresh.packageName] ?: return@map fresh
            fresh.copy(
                installedAtMs = prev.installedAtMs,
                sizeBytes = prev.sizeBytes,
            )
        }
    }

    private fun readDisk(context: Context): List<DrawerApp> {
        val file = File(context.filesDir, DISK_NAME)
        if (!file.exists()) return emptyList()
        return runCatching { DrawerCatalog.parseApps(file.readText()) }.getOrDefault(emptyList())
    }

    private fun writeDisk(context: Context, apps: List<DrawerApp>) {
        // A successful empty scan must replace the old list too. Otherwise snapshot()
        // revives removed launchers from disk the next time the drawer opens.
        runCatching {
            File(context.filesDir, DISK_NAME).writeText(DrawerCatalog.encodeApps(apps))
        }
    }

    private fun apkSize(ai: ApplicationInfo): Long {
        val paths = buildSet {
            ai.sourceDir?.let { add(it) }
            ai.publicSourceDir?.let { add(it) }
            ai.splitSourceDirs?.forEach { add(it) }
        }
        return paths.sumOf { runCatching { java.io.File(it).length() }.getOrDefault(0L) }
    }

    private const val DISK_NAME = "drawer_apps.json"
}

private val GAME_ALIASES = listOf("game", "games", "gaming")
private val AI_MARKERS = listOf(
    "ai", "gpt", "chatgpt", "chatbot", "copilot", "gemini", "assistant",
    "openai", "anthropic", "claude", "llama", "llm", "mistral", "neural",
)

private fun searchTextFor(
    label: String,
    packageName: String,
    info: android.content.pm.ResolveInfo,
): String {
    val tokens = buildList {
        addAll(label.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() })
        addAll(packageName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() })
    }.distinct()

    val categoryWords = when (info.activityInfo.applicationInfo.category) {
        ApplicationInfo.CATEGORY_GAME -> "game games gaming"
        ApplicationInfo.CATEGORY_AUDIO -> "music audio sound"
        ApplicationInfo.CATEGORY_VIDEO -> "video movie movies film tv streaming"
        ApplicationInfo.CATEGORY_IMAGE -> "photo photos camera gallery image"
        ApplicationInfo.CATEGORY_SOCIAL -> "social chat messaging messages"
        ApplicationInfo.CATEGORY_NEWS -> "news magazine media"
        ApplicationInfo.CATEGORY_MAPS -> "map maps navigation gps"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity work office docs"
        else -> ""
    }

    val extras = mutableListOf<String>()
    if (info.activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_GAME ||
        tokens.any { it in GAME_ALIASES }
    ) {
        extras += GAME_ALIASES
    }
    if (tokens.any { it in AI_MARKERS }) {
        extras += AI_MARKERS
    }
    return (tokens + categoryWords.split(" ").filter { it.isNotBlank() } + extras)
        .distinct()
        .joinToString(" ")
}

private fun drawerTypeOf(category: Int, searchText: String): DrawerAppType {
    val tokens = searchText.split(" ")
    return when (category) {
        ApplicationInfo.CATEGORY_GAME -> DrawerAppType.GAME
        ApplicationInfo.CATEGORY_AUDIO -> DrawerAppType.MUSIC
        ApplicationInfo.CATEGORY_VIDEO -> DrawerAppType.VIDEO
        ApplicationInfo.CATEGORY_IMAGE -> DrawerAppType.PHOTO
        ApplicationInfo.CATEGORY_SOCIAL -> DrawerAppType.SOCIAL
        ApplicationInfo.CATEGORY_NEWS -> DrawerAppType.NEWS
        ApplicationInfo.CATEGORY_MAPS -> DrawerAppType.MAPS
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> DrawerAppType.WORK
        else -> when {
            tokens.any { it in GAME_ALIASES } -> DrawerAppType.GAME
            else -> DrawerAppType.OTHER
        }
    }
}
