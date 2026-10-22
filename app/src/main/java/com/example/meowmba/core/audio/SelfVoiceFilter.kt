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
class SelfVoiceFilter(private val nowMs: () -> Long = { System.currentTimeMillis() }) {
    private var finishedAtMs: Long? = null

    /** Full text of the utterance currently being spoken. */
    private var utterance: String = ""

    /** Character offset of how much of [utterance] has been spoken so far. */
    private var spokenCharIndex: Int = 0

    /** Begin tracking a new utterance. Call when speech starts. */
    fun begin(text: String) {
        finishedAtMs = null
        utterance = text
        spokenCharIndex = 0
    }

    fun append(text: String) {
        utterance = (utterance + " " + text).takeLast(12_000)
        finishedAtMs = null
    }

    /** Retain the whole reply through delayed recognizer results and room echo. */
    fun finish() { finishedAtMs = nowMs() }

    /** TTS word boundary callback: [start, end) char range just spoken. */
    fun onWordSpoken(start: Int, end: Int) {
        if (end > spokenCharIndex) spokenCharIndex = end
    }

    /** Stop tracking (speech finished or cancelled). */
    fun clear() {
        finishedAtMs = null
        utterance = ""
        spokenCharIndex = 0
    }

    /**
     * Decide whether a transcript heard during playback is the companion's
     * own voice (true = suppress it) or a genuine user interruption
     * (false = let it through as barge-in).
     */
    fun isOwnVoice(partial: String, filterShortNoise: Boolean = true): Boolean {
        if (finishedAtMs?.let { nowMs() - it > 8_000L } == true) return false
        val normalizedPartial = WakePhraseMatcher.normalize(partial)

        // Silence, mic noise, and ultra-short blips ("uh", "hm") never interrupt.
        if (filterShortNoise && normalizedPartial.length < MIN_BARGE_IN_CHARS) return true

        val remaining = remainingSpeech()
        // Brief confirmations such as yes/no/cancel must remain usable after
        // a longer reply that happened to contain the same word.
        if (!filterShortNoise && normalizedPartial.split(' ').size < 3) {
            return remaining.isNotBlank() && normalizedPartial == remaining
        }
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
        return WakePhraseMatcher.normalize(utterance)
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
