package com.example.meowmba.core.vault

/**
 * Voice commands that start, continue, or stop a live note session.
 *
 * "remember this" is deliberately not a start — that stays one-shot memory.
 */
sealed class NoteSessionIntent {
    data class Start(val topic: String?) : NoteSessionIntent()
    data class Continue(val topic: String?) : NoteSessionIntent()
    data object Stop : NoteSessionIntent()
}

object NoteSessionParser {

    private val STOP = listOf(
        "stop taking notes",
        "stop transcribing",
        "stop noting",
        "stop notes",
        "that's all for notes",
        "thats all for notes",
        "done taking notes",
        "end transcription",
        "end notes",
        "stop jotting",
    )

    private val CONTINUE = listOf(
        "continue taking notes",
        "continue transcribing",
        "continue noting",
        "keep taking notes",
        "continue notes",
        "add to the notes",
        "add to notes",
    )

    private val START = listOf(
        "start taking notes",
        "start taking note",
        "start transcribing",
        "start noting",
        "begin taking notes",
        "begin transcribing",
        "begin noting",
        "jot this down",
        "take notes",
        "take note",
        "start notes",
    )

    private val TOPIC_PREFIXES = listOf(" on ", " about ", " for ", " regarding ")

    fun parse(normalized: String): NoteSessionIntent? {
        val t = normalized.trim()
        if (t.isEmpty()) return null
        if (STOP.any { t == it || t.startsWith("$it ") }) return NoteSessionIntent.Stop
        match(CONTINUE, t)?.let { return NoteSessionIntent.Continue(it.ifBlank { null }) }
        match(START, t)?.let { return NoteSessionIntent.Start(it.ifBlank { null }) }
        return null
    }

    fun vaultTitle(topic: String): String = "Notes/${topic.trim()}"

    /** Null = this family did not match. Empty topic = matched, no topic. */
    private fun match(prefixes: List<String>, text: String): String? {
        for (prefix in prefixes.sortedByDescending { it.length }) {
            if (text == prefix) return ""
            if (text.startsWith("$prefix ")) {
                return topicOf(text.removePrefix(prefix).trim()) ?: ""
            }
        }
        return null
    }

    private fun topicOf(rest: String): String? {
        if (rest.isBlank()) return null
        var t = rest
        for (p in TOPIC_PREFIXES) {
            if (t.startsWith(p.trimStart())) {
                t = t.removePrefix(p.trimStart()).trim()
                break
            }
        }
        return t.ifBlank { null }
    }
}
