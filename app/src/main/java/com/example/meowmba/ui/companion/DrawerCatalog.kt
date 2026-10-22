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

    /** Compiled once: matchesSearch runs per app per keystroke in the drawer. */
    private val WHITESPACE = Regex("\\s+")

    /**
     * Common spoken/typed shortcuts → tokens that should boost real targets.
     * Kept small and explicit so "messages" does not flood the grid.
     */
    private val QUERY_ALIASES: Map<String, Set<String>> = mapOf(
        "messages" to setOf("messages", "message", "sms", "mms", "texting", "samsung messages", "google messages"),
        "message" to setOf("messages", "message", "sms", "mms", "texting"),
        "sms" to setOf("sms", "mms", "messages", "message", "texting"),
        "texts" to setOf("messages", "message", "sms", "texting"),
        "text" to setOf("messages", "message", "sms", "texting"),
        "chrome" to setOf("chrome", "browser"),
        "photos" to setOf("photos", "gallery", "photo", "images"),
        "gallery" to setOf("gallery", "photos", "photo"),
        "camera" to setOf("camera", "photo"),
        "maps" to setOf("maps", "map", "navigation", "gps"),
        "mail" to setOf("mail", "gmail", "email", "outlook"),
        "email" to setOf("email", "mail", "gmail", "outlook"),
        "settings" to setOf("settings", "setting"),
        "phone" to setOf("phone", "dialer", "call", "telephone"),
        "clock" to setOf("clock", "alarm", "timer"),
        "calendar" to setOf("calendar", "agenda"),
        "play" to setOf("play store", "google play", "play"),
        "store" to setOf("play store", "galaxy store", "app store"),
    )

    /** Packages that are the real messaging/SMS apps people mean by "messages". */
    private val MESSAGING_PACKAGES = setOf(
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.google.android.apps.dynamite", // Chat — weak, still below Messages
        "com.facebook.orca",
        "org.telegram.messenger",
        "com.whatsapp",
        "com.discord",
        "com.instagram.android",
        "com.snapchat.android",
        "com.viber.voip",
        "com.skype.raider",
        "com.tencent.mm",
    )

    enum class MatchKind(val rank: Int) {
        NONE(0),
        SUBSEQUENCE(1),
        CONTAINS(2),
        ALIAS(3),
        WORD_START(4),
        PREFIX(5),
        EXACT(6),
    }

    data class MatchScore(
        val kind: MatchKind,
        val score: Int,
        val messagingBoost: Boolean = false,
    )

    fun matchesSearch(app: DrawerApp, query: String): Boolean =
        rankMatch(app, query).kind != MatchKind.NONE

    /**
     * Rank how well [app] matches [query]. Letter-subsequence is a weak signal
     * only — exact / prefix / word-start / alias / contains beat it.
     */
    fun rankMatch(
        app: DrawerApp,
        query: String,
        launchCount: Int = 0,
    ): MatchScore {
        val words = query.lowercase().trim().split(WHITESPACE).filter { it.isNotBlank() }
        if (words.isEmpty()) return MatchScore(MatchKind.EXACT, 0)
        val label = app.label.lowercase().trim()
        val compactLabel = label.replace(" ", "")
        val labelWords = label.split(WHITESPACE).filter { it.isNotBlank() }
        val tokens = app.searchText.split(" ").filter { it.isNotBlank() }
        val haystacks = (tokens + labelWords + listOf(label, compactLabel)).distinct()

        var worst = MatchKind.EXACT
        var points = 0
        for (w in words) {
            val wordKind = bestKindForWord(w, label, compactLabel, labelWords, tokens, haystacks)
            if (wordKind == MatchKind.NONE) return MatchScore(MatchKind.NONE, Int.MIN_VALUE)
            if (wordKind.rank < worst.rank) worst = wordKind
            points += wordKind.rank * 100
        }

        val aliasHit = words.any { w ->
            val aliases = QUERY_ALIASES[w] ?: return@any false
            aliases.any { alias ->
                label == alias ||
                    label.startsWith(alias) ||
                    labelWords.any { it == alias || it.startsWith(alias) } ||
                    tokens.any { it == alias || it.startsWith(alias) } ||
                    haystacks.any { it.contains(alias) }
            }
        }
        if (aliasHit && worst.rank < MatchKind.ALIAS.rank) {
            worst = MatchKind.ALIAS
            points += 80
        } else if (aliasHit) {
            points += 40
        }

        val messagingQuery = words.any { it in setOf("messages", "message", "sms", "mms", "texts", "text") }
        val messagingBoost = messagingQuery && isMessagingApp(app)
        if (messagingBoost) points += 220
        // Frequency / recency proxy: launches the user actually made.
        points += launchCount.coerceIn(0, 50) * 3
        // Prefer shorter labels among equal kinds (Messages before Messages+something).
        points += (40 - label.length).coerceIn(-20, 40)
        return MatchScore(worst, points, messagingBoost)
    }

    private fun bestKindForWord(
        w: String,
        label: String,
        compactLabel: String,
        labelWords: List<String>,
        tokens: List<String>,
        haystacks: List<String>,
    ): MatchKind {
        if (label == w || compactLabel == w || labelWords.any { it == w } || tokens.any { it == w }) {
            return MatchKind.EXACT
        }
        if (label.startsWith(w) || compactLabel.startsWith(w)) return MatchKind.PREFIX
        if (w.length >= 2 && (labelWords.any { it.startsWith(w) } || tokens.any { it.startsWith(w) })) {
            return MatchKind.WORD_START
        }
        val aliases = QUERY_ALIASES[w]
        if (aliases != null && aliases.any { alias ->
                label == alias || label.startsWith(alias) ||
                    labelWords.any { it == alias || it.startsWith(alias) } ||
                    tokens.any { it == alias || it.startsWith(alias) }
            }
        ) {
            return MatchKind.ALIAS
        }
        if (w.length >= 3 && (label.contains(w) || compactLabel.contains(w) || tokens.any { it.contains(w) })) {
            return MatchKind.CONTAINS
        }
        // Subsequence only for needles long enough to be intentional (typo-ish),
        // and never for very short queries that match almost everything.
        if (w.length >= 4 && haystacks.any { subsequenceLetters(it, w) }) {
            return MatchKind.SUBSEQUENCE
        }
        return MatchKind.NONE
    }

    fun isMessagingApp(app: DrawerApp): Boolean {
        val pkg = app.packageName.lowercase()
        if (pkg in MESSAGING_PACKAGES) return true
        if (pkg.contains("messaging") || pkg.endsWith(".mms") || pkg.contains(".sms")) return true
        val label = app.label.lowercase()
        if (label == "messages" || label == "message" || label.endsWith(" messages") ||
            label == "samsung messages" || label == "google messages" || label == "android messages"
        ) return true
        val tokens = app.searchText.split(" ")
        return app.type == DrawerAppType.SOCIAL && tokens.any { it in setOf("sms", "mms", "messages", "messaging") }
    }

    /**
     * True when every letter of [needle] appears in order inside [haystack]
     * (not necessarily contiguous). Weak signal only — see [rankMatch].
     */
    fun subsequenceLetters(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return true
        if (needle.length > haystack.length) return false
        var i = 0
        for (c in haystack) {
            if (c == needle[i]) {
                i++
                if (i == needle.length) return true
            }
        }
        return false
    }

    fun visible(
        apps: List<DrawerApp>,
        query: String,
        sort: DrawerSort,
        type: DrawerAppType?,
        launchCounts: Map<String, Int> = emptyMap(),
    ): List<DrawerApp> {
        val typed = if (type == null) apps else apps.filter { it.type == type }
        if (query.isBlank()) return sortApps(typed, sort)

        val ranked = typed.mapNotNull { app ->
            val score = rankMatch(app, query, launchCounts[app.packageName] ?: 0)
            if (score.kind == MatchKind.NONE) null else app to score
        }
        if (ranked.isEmpty()) return emptyList()

        val bestKind = ranked.maxOf { it.second.kind.rank }
        // When real matches exist, hide pure letter-subsequence noise.
        val filtered = if (bestKind >= MatchKind.CONTAINS.rank) {
            ranked.filter { it.second.kind.rank >= MatchKind.CONTAINS.rank || it.second.messagingBoost }
        } else {
            ranked
        }

        // Rank by match score first; only then apply the user's chosen sort as a tiebreak.
        return filtered
            .sortedWith(
                compareByDescending<Pair<DrawerApp, MatchScore>> { it.second.score }
                    .thenByDescending { it.second.kind.rank }
                    .thenBy { it.first.label.lowercase() },
            )
            .map { it.first }
            .let { rankedApps ->
                // Preserve relative order from score ranking; sortApps would destroy it.
                // For blank query we already returned. For non-blank, score wins.
                rankedApps
            }
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
