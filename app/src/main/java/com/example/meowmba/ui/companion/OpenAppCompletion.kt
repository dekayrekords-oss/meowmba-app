package com.example.meowmba.ui.companion

/** Suggestions only: selection edits the draft; Send remains the execution boundary. */
object OpenAppCompletion {
    fun suggest(draft: String, apps: List<DrawerApp>): List<String> {
        val match = Regex("^\\s*open\\s+(.*)$", RegexOption.IGNORE_CASE).matchEntire(draft) ?: return emptyList()
        val query = match.groupValues[1].trim()
        if (query.isEmpty() || query.length > 60) return emptyList()
        return apps.map { it to DrawerCatalog.rankMatch(it, query) }
            .filter { it.second.kind != DrawerCatalog.MatchKind.NONE }
            .sortedWith(compareByDescending<Pair<DrawerApp, DrawerCatalog.MatchScore>> { it.second.score }
                .thenBy { it.first.label })
            .map { "open ${it.first.label}" }.distinct().take(4)
    }
}
