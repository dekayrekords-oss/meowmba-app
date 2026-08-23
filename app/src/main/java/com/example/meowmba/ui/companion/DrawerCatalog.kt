package com.example.meowmba.ui.companion

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DrawerAppType(val label: String) {
    GAME("Games"),
    MUSIC("Music"),
    VIDEO("Video"),
    PHOTO("Photos"),
    SOCIAL("Social"),
    NEWS("News"),
    MAPS("Maps"),
    WORK("Work"),
    OTHER("Other"),
}

enum class DrawerSort(val label: String) {
    NAME("Name"),
    DATE_ADDED("Date"),
    SIZE("Size"),
    TYPE("Type"),
}

data class DrawerApp(
    val label: String,
    val packageName: String,
    val searchText: String,
    val installedAtMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val type: DrawerAppType = DrawerAppType.OTHER,
)

data class DrawerFolder(
    val id: String,
    val name: String,
    val packages: List<String>,
)

/** Sort, type filter, and folder JSON for the app drawer. Pure — no Android. */
object DrawerCatalog {

    fun matchesSearch(app: DrawerApp, query: String): Boolean {
        val words = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val tokens = app.searchText.split(" ")
        return words.all { w ->
            tokens.any { it == w || (w.length >= 3 && it.startsWith(w)) } ||
                app.label.lowercase().startsWith(w)
        }
    }

    fun visible(
        apps: List<DrawerApp>,
        query: String,
        sort: DrawerSort,
        type: DrawerAppType?,
    ): List<DrawerApp> {
        val filtered = apps.filter { app ->
            (type == null || app.type == type) && matchesSearch(app, query)
        }
        return sortApps(filtered, sort)
    }

    fun sortApps(apps: List<DrawerApp>, sort: DrawerSort): List<DrawerApp> = when (sort) {
        DrawerSort.NAME -> apps.sortedBy { it.label.lowercase() }
        DrawerSort.DATE_ADDED -> apps.sortedByDescending { it.installedAtMs }
        DrawerSort.SIZE -> apps.sortedByDescending { it.sizeBytes }
        DrawerSort.TYPE -> apps.sortedWith(
            compareBy<DrawerApp> { it.type.ordinal }.thenBy { it.label.lowercase() },
        )
    }

    fun matchingFolders(folders: List<DrawerFolder>, query: String): List<DrawerFolder> {
        if (query.isBlank()) return folders
        val q = query.lowercase().trim()
        return folders.filter { it.name.lowercase().contains(q) }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        if (kb < 1024) return "${kb.toInt().coerceAtLeast(1)} KB"
        val mb = kb / 1024.0
        return if (mb < 1024) "${mb.toInt()} MB" else "${"%.1f".format(mb / 1024.0)} GB"
    }

    fun formatAdded(ms: Long): String {
        if (ms <= 0L) return ""
        return SimpleDateFormat("MMM yyyy", Locale.US).format(Date(ms))
    }

    fun subtitle(app: DrawerApp, sort: DrawerSort): String = when (sort) {
        DrawerSort.DATE_ADDED -> formatAdded(app.installedAtMs)
        DrawerSort.SIZE -> formatSize(app.sizeBytes)
        DrawerSort.TYPE -> app.type.label
        DrawerSort.NAME -> ""
    }

    fun parseFolders(raw: String?): List<DrawerFolder> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank()) continue
                    val name = o.optString("name").ifBlank { "Folder" }
                    val pkgs = o.optJSONArray("packages")
                    val packages = buildList {
                        if (pkgs != null) {
                            for (j in 0 until pkgs.length()) {
                                pkgs.optString(j).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    }.distinct()
                    add(DrawerFolder(id, name, packages))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeFolders(folders: List<DrawerFolder>): String {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("packages", JSONArray(f.packages)),
            )
        }
        return arr.toString()
    }

    fun createFolder(
        folders: List<DrawerFolder>,
        name: String,
        firstPackage: String? = null,
        id: String,
    ): List<DrawerFolder> {
        val clean = name.trim().take(24).ifBlank { "Folder" }
        val pkgs = listOfNotNull(firstPackage?.takeIf { it.isNotBlank() })
        return folders + DrawerFolder(id, clean, pkgs)
    }

    fun addToFolder(
        folders: List<DrawerFolder>,
        folderId: String,
        packageName: String,
    ): List<DrawerFolder> = folders.map { f ->
        if (f.id != folderId || packageName in f.packages) f
        else f.copy(packages = f.packages + packageName)
    }

    fun removeFromFolder(
        folders: List<DrawerFolder>,
        folderId: String,
        packageName: String,
    ): List<DrawerFolder> = folders.map { f ->
        if (f.id != folderId) f else f.copy(packages = f.packages - packageName)
    }

    fun renameFolder(
        folders: List<DrawerFolder>,
        folderId: String,
        name: String,
    ): List<DrawerFolder> {
        val clean = name.trim().take(24)
        if (clean.isBlank()) return folders
        return folders.map { if (it.id == folderId) it.copy(name = clean) else it }
    }

    fun deleteFolder(folders: List<DrawerFolder>, folderId: String): List<DrawerFolder> =
        folders.filterNot { it.id == folderId }

    /**
     * Put unfiled apps into type folders (Games, Social, …). Types with
     * fewer than two apps stay in the grid. Returns the new folder list
     * and how many apps were filed.
     */
    fun organizeByType(
        folders: List<DrawerFolder>,
        apps: List<DrawerApp>,
        newId: (Int) -> String,
    ): Pair<List<DrawerFolder>, Int> {
        val already = folders.flatMap { it.packages }.toSet()
        val grouped = apps
            .filter { it.packageName !in already && it.type != DrawerAppType.OTHER }
            .groupBy { it.type }
        var added = 0
        val next = folders.toMutableList()
        var seq = 0
        for (type in DrawerAppType.entries) {
            if (type == DrawerAppType.OTHER) continue
            val members = grouped[type] ?: continue
            if (members.size < 2) continue
            val pkgs = members.map { it.packageName }
            val existing = next.indexOfFirst { it.name.equals(type.label, ignoreCase = true) }
            if (existing >= 0) {
                val folder = next[existing]
                val extra = pkgs.filter { it !in folder.packages }
                if (extra.isEmpty()) continue
                next[existing] = folder.copy(packages = folder.packages + extra)
                added += extra.size
            } else {
                next += DrawerFolder(id = newId(seq++), name = type.label, packages = pkgs)
                added += pkgs.size
            }
        }
        return next to added
    }

    fun encodeApps(apps: List<DrawerApp>): String {
        val arr = JSONArray()
        apps.forEach { a ->
            arr.put(
                JSONObject()
                    .put("label", a.label)
                    .put("pkg", a.packageName)
                    .put("search", a.searchText)
                    .put("added", a.installedAtMs)
                    .put("size", a.sizeBytes)
                    .put("type", a.type.name),
            )
        }
        return arr.toString()
    }

    fun parseApps(raw: String?): List<DrawerApp> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val pkg = o.optString("pkg")
                    if (pkg.isBlank()) continue
                    val type = runCatching {
                        DrawerAppType.valueOf(o.optString("type"))
                    }.getOrDefault(DrawerAppType.OTHER)
                    add(
                        DrawerApp(
                            label = o.optString("label").ifBlank { pkg },
                            packageName = pkg,
                            searchText = o.optString("search").ifBlank { pkg.lowercase() },
                            installedAtMs = o.optLong("added"),
                            sizeBytes = o.optLong("size"),
                            type = type,
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
