package com.example.meowmba.core.audio

/**
 * Matches wake phrases in recognized speech.
 *
 * Pure Kotlin with no Android dependencies so the matching rules are fully
 * unit-testable on the JVM. Supported phrases (per product spec):
 *   - "Hey Memba"
 *   - "Hey Meow"
 *   - "Hey [custom companion name]"
 *
 * Matching is case-insensitive, punctuation-tolerant, and honors word
 * boundaries ("hey meowington" must not match). Any words spoken after the
 * wake phrase are returned as the immediate command.
 */
class WakePhraseMatcher(companionName: String = DEFAULT_NAME) {

    data class Match(
        /** The exact phrase that matched, normalized (lowercase). */
        val phrase: String,
        /** Command spoken right after the wake phrase; null if none yet. */
        val command: String?
    )

    /** Normalized wake phrases, longest first so the longest match wins. */
    val phrases: List<String>

    init {
        phrases = buildSet {
            add("hey memba")
            add("hey meow")
            val custom = normalize(companionName)
            if (custom.isNotBlank()) add("hey $custom")
        }.sortedByDescending { it.length }
    }

    /**
     * Try to find a wake phrase in [text]. Returns null when no phrase is
     * present at a word boundary.
     */
    fun match(text: String): Match? {
        val normalized = normalize(text)
        if (normalized.isBlank()) return null

        for (phrase in phrases) {
            val idx = indexOfPhrase(normalized, phrase)
            if (idx >= 0) {
                val remainder = normalized.substring(idx + phrase.length)
                    .trimStart { it == ' ' || it == ',' || it == '.' }
                    .ifBlank { null }
                return Match(phrase, remainder)
            }
        }
        return null
    }

    companion object {
        const val DEFAULT_NAME = "Meow"

        /** Lowercase, strip punctuation to spaces, collapse whitespace. */
        internal fun normalize(text: String): String =
            text.lowercase()
                .replace(Regex("[^a-z0-9' ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        /** Word-boundary-aware indexOf: not preceded or followed by a letter. */
        private fun indexOfPhrase(text: String, phrase: String): Int {
            var idx = text.indexOf(phrase)
            while (idx >= 0) {
                val beforeOk = idx == 0 || !text[idx - 1].isLetterOrDigit()
                val afterIdx = idx + phrase.length
                val afterOk = afterIdx >= text.length || !text[afterIdx].isLetterOrDigit()
                if (beforeOk && afterOk) return idx
                idx = text.indexOf(phrase, idx + 1)
            }
            return -1
        }
    }
}
