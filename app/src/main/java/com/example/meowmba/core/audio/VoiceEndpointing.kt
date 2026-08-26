package com.example.meowmba.core.audio

/**
 * Lightweight semantic endpointing for conversational speech.
 *
 * Android's recognizer has already observed a short acoustic pause when it emits a
 * final result. Waiting a fixed additional 2.5 seconds makes every reply feel slow;
 * immediately submitting that result cuts people off after "can you...". This policy
 * adds a short semantic hold: complete-looking turns land quickly, while fillers and
 * trailing connectors get enough time for the speaker to continue.
 */
object VoiceEndpointing {
    const val COMPLETE_TURN_MS = 900L
    const val DEFAULT_TURN_MS = 1_200L
    const val SHORT_TURN_MS = 1_500L
    const val INCOMPLETE_TURN_MS = 2_300L

    private val incompleteEndings = setOf(
        "a", "an", "and", "because", "but", "for", "from", "if", "in", "of",
        "or", "so", "than", "that", "the", "then", "to", "uh", "um", "with",
    )

    private val questionOpeners = setOf(
        "are", "can", "could", "did", "do", "does", "how", "is", "should",
        "what", "when", "where", "which", "who", "why", "will", "would",
    )

    fun silenceAfter(text: String): Long {
        val normalized = text.trim().lowercase()
        if (normalized.isEmpty()) return DEFAULT_TURN_MS
        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        val last = words.last().trimEnd('.', ',', '?', '!')

        return when {
            last in incompleteEndings || normalized.endsWith("...") -> INCOMPLETE_TURN_MS
            normalized.endsWith('?') || normalized.endsWith('!') -> COMPLETE_TURN_MS
            words.first() in questionOpeners && words.size >= 3 -> COMPLETE_TURN_MS
            words.size <= 2 -> SHORT_TURN_MS
            else -> DEFAULT_TURN_MS
        }
    }
}
