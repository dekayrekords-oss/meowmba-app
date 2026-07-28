package com.example.meowmba.core.vision

import com.example.meowmba.core.audio.WakePhraseMatcher

/**
 * Keyword fast path for vision: "what do you see", "look at this", …
 * Deterministic, free, and unit-tested — the model is never asked whether
 * something is a look request.
 */
object VisionRouter {

    data class VisionQuery(
        /** Optional focus extracted from the phrase ("look at the screen" → "the screen"). */
        val focus: String? = null
    )

    private val EXACT = setOf(
        "look", "look at this", "look at that", "what do you see",
        "what do you see?", "can you see", "describe what you see",
        "what is this", "what's this", "what is that", "what's that",
        "what am i holding", "what am i looking at", "describe the scene",
        "what's in front of you", "what is in front of you", "read this",
        "take a look", "see this", "check this out"
    )

    private val FOCUS_PREFIXES = listOf(
        "look at the ", "look at my ", "look at ",
        "can you see the ", "read the "
    )

    fun parse(text: String): VisionQuery? {
        val raw = text.trim()
        if (raw.isEmpty()) return null
        val normalized = WakePhraseMatcher.normalize(raw)
        if (normalized in EXACT.map { it.removeSuffix("?") }) return VisionQuery()
        for (prefix in FOCUS_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                val focus = raw.substring(prefix.length).trim().removeSuffix("?")
                if (focus.isNotEmpty()) return VisionQuery(focus)
            }
        }
        return null
    }

    /** The prompt sent to the vision model with the captured frame. */
    fun promptFor(query: VisionQuery): String {
        val base = "Describe what you see in one or two plain sentences."
        return query.focus?.let { "$base Focus on: $it." } ?: base
    }
}
