package com.example.meowmba.core.audio

/**
 * Makes spoken-turn admission idempotent across overlapping Android callbacks.
 *
 * Always-on, stitched continuation, foreground wake, and background handoff can all
 * deliver through the same ViewModel boundary. Some recognizers emit the same final
 * twice while a session is stopping; without a gate that starts two model jobs and the
 * companion answers twice. Only an exact normalized duplicate in the short transport
 * window is rejected—repeating a question later remains a legitimate new turn.
 */
class TurnDeduplicator(
    private val duplicateWindowMs: Long = DUPLICATE_WINDOW_MS,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private var lastText = ""
    private var lastAcceptedAt = Long.MIN_VALUE

    @Synchronized
    fun accept(text: String): Boolean {
        // Recognition providers disagree on whether contractions contain an
        // apostrophe ("what's" vs "whats"). They are the same transport turn.
        val normalized = WakePhraseMatcher.normalize(text.replace(Regex("['’]"), ""))
        if (normalized.isBlank()) return false
        val at = now()
        val duplicate = normalized == lastText &&
            lastAcceptedAt != Long.MIN_VALUE &&
            at - lastAcceptedAt in 0..duplicateWindowMs
        if (duplicate) return false
        lastText = normalized
        lastAcceptedAt = at
        return true
    }

    companion object {
        const val DUPLICATE_WINDOW_MS = 3_000L
    }
}
