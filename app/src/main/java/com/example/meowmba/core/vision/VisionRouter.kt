package com.example.meowmba.core.vision

import com.example.meowmba.core.audio.WakePhraseMatcher

object VisionRouter {

    data class VisionQuery(
        val focus: String? = null,
        val isReadQuery: Boolean = false,
        val isQrQuery: Boolean = false,
    )

    private val EXACT = setOf(
        "look", "look at this", "look at that", "what do you see",
        "what do you see?", "can you see", "describe what you see",
        "what is this", "what's this", "what is that", "what's that",
        "what am i holding", "what am i looking at", "describe the scene",
        "what's in front of you", "what is in front of you", "read this",
        "take a look", "see this", "check this out",
        // Face-presence checks — answered from the face tracker, not a vision model.
        "can you see me", "do you see me", "are you looking at me",
        "can you see me?", "do you see me?", "are you looking at me?",
        "look at me", "see me",
    )

    /** True when the user is asking whether the companion can see *them*. */
    fun isSeeMeQuery(text: String): Boolean {
        val n = WakePhraseMatcher.normalize(text.trim()).removeSuffix("?")
        return n in setOf(
            "can you see me", "do you see me", "are you looking at me",
            "look at me", "see me", "can you see me now", "do you see me now",
        ) || n.startsWith("can you see me") || n.startsWith("do you see me")
    }

    private val FOCUS_PREFIXES = listOf(
        "look at the ", "look at my ", "look at ",
        "can you see the ", "read the "
    )

    fun parse(text: String): VisionQuery? {
        val raw = text.trim()
        if (raw.isEmpty()) return null
        val normalized = WakePhraseMatcher.normalize(raw)
        val codeMention = Regex("\\b(qr(?: code)?|bar ?code)\\b").containsMatchIn(normalized)
        val scanRequest = Regex("\\b(scan|read|capture|open|use|look|point|get)\\b").containsMatchIn(normalized)
        val informational = Regex("^(what is|what's|explain|how (does|do)|why)\\b").containsMatchIn(normalized)
        if (codeMention && scanRequest && !informational) {
            return VisionQuery(isQrQuery = true, isReadQuery = true)
        }
        if (normalized in EXACT.map { it.removeSuffix("?") }) {
            val isRead = normalized == "read this"
            return VisionQuery(isReadQuery = isRead)
        }
        for (prefix in FOCUS_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                val focus = raw.substring(prefix.length).trim().removeSuffix("?")
                if (focus.isNotEmpty()) return VisionQuery(
                    focus = focus,
                    isReadQuery = prefix.startsWith("read")
                )
            }
        }
        return null
    }

    fun promptFor(query: VisionQuery): String {
        if (query.isQrQuery) return "A QR scan is being performed separately; do not invent or infer a code."
        val base = if (query.isReadQuery) {
            "Read any visible text carefully and return it verbatim."
        } else {
            "Describe what you see in one or two plain sentences."
        }
        return query.focus?.let { "$base Focus on: $it." } ?: base
    }
}
