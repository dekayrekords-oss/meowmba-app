package com.example.meowmba.core.audio

/**
 * Makes spoken-turn admission idempotent across overlapping Android callbacks.
 *
 * Always-on, stitched continuation, foreground wake, and background handoff can all
 * deliver through the same ViewModel boundary. Some recognizers emit the same final
 * twice while a session is stopping; without a gate that starts two model jobs and the
 * companion answers twice. An exact normalized duplicate — or a near-duplicate /
 * stutter fragment of it — in the short transport window is rejected. Repeating a
 * question later remains a legitimate new turn.
 */
class TurnDeduplicator(
    private val duplicateWindowMs: Long = DUPLICATE_WINDOW_MS,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private var lastText = ""
    private var lastAcceptedAt = Long.MIN_VALUE

    /**
     * ASR splits a contraction into two words on one delivery and one on the
     * next ("what's the weather" vs "what is the weather"). Before comparing,
     * expand the common split contractions so the two word-bags line up.
     */
    private fun expandContractions(text: String): String {
        var t = text
        CONTRACTION_EXPANSIONS.forEach { (short, long) ->
            t = t.replace(Regex("""\b$short\b"""), long)
        }
        return t
    }

    @Synchronized
    fun accept(text: String): Boolean {
        // Recognition providers disagree on whether contractions contain an
        // apostrophe ("what's" vs "whats"). They are the same transport turn.
        val normalized = expandContractions(
            WakePhraseMatcher.normalize(text.replace(Regex("['’]"), ""))
        )
        if (normalized.isBlank()) return false
        val at = now()
        val inWindow = lastAcceptedAt != Long.MIN_VALUE &&
            at - lastAcceptedAt in 0..duplicateWindowMs
        if (inWindow) {
            // Exact repeat — the double-delivery path: the same final arrives a
            // couple of seconds later through a second collector.
            val exact = normalized == lastText
            // Near-repeat — the same utterance with ASR re-wording ("what's the
            // weather" then "what is the weather") or a stutter fragment of it.
            // Overlap is on the shorter message so a short fragment of the last
            // turn ("one of the fifteen" after "one of the fifteen I was making")
            // is still caught, while a genuinely different follow-up is not.
            val near = !exact && overlapRatio(normalized, lastText) >= 0.7f
            if (exact || near) return false
        }
        lastText = normalized
        lastAcceptedAt = at
        return true
    }

    /** Fraction of the shorter message's words present in the longer one. */
    private fun overlapRatio(a: String, b: String): Float {
        val wa = a.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wb = b.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (wa.isEmpty() || wb.isEmpty()) return 0f
        val (short, long) = if (wa.size <= wb.size) wa to wb else wb to wa
        if (long.size == 0) return 0f
        return short.count { it in long }.toFloat() / short.size
    }

    companion object {
        // Wide enough to cover the double-delivery path measured on device:
        // the always-on collector commits a recovered partial ~2s after the
        // turn, and the stitched capture delivers the SAME final ~4.4s later.
        // Exact normalized matches only — intentionally repeating a question
        // 8s later still registers as a new turn.
        const val DUPLICATE_WINDOW_MS = 8_000L

        private val CONTRACTION_EXPANSIONS = mapOf(
            "whats" to "what is", "hows" to "how is", "whys" to "why is",
            "whens" to "when is", "wheres" to "where is", "whos" to "who is",
            "thats" to "that is", "dont" to "do not", "cant" to "can not",
            "wont" to "will not", "ive" to "i have", "youre" to "you are",
            "im" to "i am", "id" to "i would", "ill" to "i will",
        )
    }
}
