package com.example.meowmba.core.audio

/**
 * Echo-tolerant barge-in filter.
 *
 * The problem: to make the companion interruptible, the recognizer must stay
 * LIVE while TTS audio is playing — but then the microphone hears the
 * companion's own voice, and naive interruption logic would constantly
 * trigger on itself.
 *
 * The solution: we know exactly what the companion is saying. TTS word
 * boundary callbacks tell us how far through the utterance playback is, so a
 * heard transcript whose words overwhelmingly match the companion's own
 * (remaining) speech is echo and is suppressed; a transcript with novel
 * content is a real user barging in and is allowed through.
 *
 * Pure Kotlin, unit-testable, no Android dependencies.
 */
class SelfVoiceFilter {

    /** Full text of the utterance currently being spoken. */
    private var utterance: String = ""

    /** Character offset of how much of [utterance] has been spoken so far. */
    private var spokenCharIndex: Int = 0

    /** Begin tracking a new utterance. Call when speech starts. */
    fun begin(text: String) {
        utterance = text
        spokenCharIndex = 0
    }

    /** TTS word boundary callback: [start, end) char range just spoken. */
    fun onWordSpoken(start: Int, end: Int) {
        if (end > spokenCharIndex) spokenCharIndex = end
    }

    /** Stop tracking (speech finished or cancelled). */
    fun clear() {
        utterance = ""
        spokenCharIndex = 0
    }

    /**
     * Decide whether a transcript heard during playback is the companion's
     * own voice (true = suppress it) or a genuine user interruption
     * (false = let it through as barge-in).
     */
    fun isOwnVoice(partial: String): Boolean {
        val normalizedPartial = WakePhraseMatcher.normalize(partial)

        // Silence, mic noise, and ultra-short blips ("uh", "hm") never interrupt.
        if (normalizedPartial.length < MIN_BARGE_IN_CHARS) return true

        val remaining = remainingSpeech()
        if (remaining.isBlank()) {
            // Nothing is being spoken (or tracking was reset): any real speech
            // must be the user.
            return false
        }

        // Echo lags slightly behind playback, so compare against a window that
        // starts a little before the current word position.
        val remainingTokens = remaining.split(' ').filter { it.isNotBlank() }.toSet()
        if (remainingTokens.isEmpty()) return false

        val partialTokens = normalizedPartial.split(' ').filter { it.isNotBlank() }
        val echoTokens = partialTokens.count { it in remainingTokens }
        val echoRatio = echoTokens.toFloat() / partialTokens.size

        return echoRatio >= ECHO_RATIO_THRESHOLD
    }

    /** Normalized companion speech from just before the playhead onwards. */
    private fun remainingSpeech(): String {
        if (utterance.isBlank()) return ""
        val from = (spokenCharIndex - ECHO_LOOKBACK_CHARS).coerceIn(0, utterance.length)
        return WakePhraseMatcher.normalize(utterance.substring(from))
    }

    companion object {
        /** Minimum normalized length before a transcript may interrupt at all. */
        private const val MIN_BARGE_IN_CHARS = 3

        /** Fraction of heard words matching the companion's speech to call it echo. */
        private const val ECHO_RATIO_THRESHOLD = 0.8f

        /** How far behind the playhead echo may lag, in characters. */
        private const val ECHO_LOOKBACK_CHARS = 24
    }
}
