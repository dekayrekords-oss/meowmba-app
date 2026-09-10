package com.example.meowmba.core.vault

/** Offline extractive digest. Recorded speech is data, never executable instructions. */
object NoteSessionSummary {
    private val entry = Regex("(?m)^\\*\\*([^\\n*]{1,32})\\*\\*  ?\\n")
    private val action = Regex("(?i)\\b(remind|need to|follow up|follow-up|due|deadline|by tomorrow|todo|to-do|please|must)\\b")
    private val decision = Regex("(?i)\\b(decided|agreed|decision|approved|chose|we will|we'll)\\b")
    fun render(note: VaultNote, stopped: String): String {
        val matches = entry.findAll(note.body).take(2_001).toList()
        val lines = matches.take(2_000).mapIndexedNotNull { index, match ->
            val end = matches.getOrNull(index + 1)?.range?.first ?: note.body.length
            val text = note.body.substring(match.range.last + 1, end).trim()
                .lineSequence().filterNot { it.startsWith("_Started ") }.joinToString(" ").trim()
            text.takeIf { it.isNotBlank() }?.let { match.groupValues[1] to it }
        }
        fun List<Pair<String, String>>.bullets() = joinToString("\n") { (time, text) ->
            "- $time — ${text.take(320)}${if (text.length > 320) "…" else ""}"
        }
        val decisions = lines.filter { decision.containsMatchIn(it.second) }.distinctBy { it.second }.take(8)
        val actions = lines.filter { action.containsMatchIn(it.second) }.distinctBy { it.second }.take(8)
        val highlights = (lines.take(3) + lines.takeLast(5)).distinctBy { it.second }.take(8)
        return buildString {
            appendLine("# Local notes summary")
            appendLine("Source: [[${note.title}]]")
            appendLine("Stopped: $stopped")
            appendLine("${lines.size} recorded segment(s). This digest quotes selected transcript passages; the full notes remain in the source.")
            appendLine()
            if (lines.isEmpty()) appendLine("No speech segments were saved.")
            else {
                if (decisions.isNotEmpty()) { appendLine("## Decisions mentioned"); appendLine(decisions.bullets()); appendLine() }
                if (actions.isNotEmpty()) { appendLine("## Possible follow-ups to review"); appendLine(actions.bullets()); appendLine() }
                appendLine("## Conversation excerpts"); appendLine(highlights.bullets())
                if (matches.size > 2_000) appendLine("Only the first 2,000 segments were included in this digest; see the full notes for the rest.")
            }
            appendLine(); appendLine("No reminders or external actions were created from this recording.")
        }
    }
}
