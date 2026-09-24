package com.example.meowmba.ui.companion

/**
 * How much Meow speaks aloud. Owner (2026-09-23): "it talks a lot, and when it's not necessary."
 * Everything Meow says is always on screen and in the conversation; this only decides the voice.
 */
enum class SpeechMode(val key: String, val label: String, val detail: String) {
    REPLIES("replies", "When I talk", "Answers out loud only when you spoke to it. Typed questions get typed answers."),
    IMPORTANT("important", "Important", "Also says urgent things on its own, but only while driving or with headphones."),
    ALWAYS("always", "Always", "Speaks every reply and reminder in full.");

    companion object {
        const val PREF = "speech_mode"
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: REPLIES

        /** First two sentences, at most ~240 characters: the rest stays on screen. */
        fun shorten(text: String, maxSentences: Int = 2, maxChars: Int = 240): String {
            val t = text.trim()
            val ends = Regex("(?<=[.!?])\\s+").findAll(t).map { it.range.first }.toList()
            val cut = ends.getOrNull(maxSentences - 1)?.let { t.substring(0, it) } ?: t
            if (cut.length <= maxChars) return cut
            val space = cut.lastIndexOf(' ', maxChars)
            return cut.substring(0, if (space > 80) space else maxChars).trimEnd(',', ';', ':') + "…"
        }
    }
}
