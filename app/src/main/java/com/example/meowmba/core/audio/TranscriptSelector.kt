package com.example.meowmba.core.audio

/**
 * Selects the most complete recognizer hypothesis without inventing words.
 *
 * Some Android recognizers emit an excellent partial and then a shorter final result
 * after endpointing. The old implementation blindly took final hypothesis zero, losing
 * the end of real utterances. We prefer the recognizer's highest-confidence final, but
 * retain a substantially longer partial when it contains that final almost verbatim.
 */
object TranscriptSelector {
    fun select(
        finals: List<String>,
        confidences: FloatArray? = null,
        lastPartial: String = "",
    ): String {
        val nonBlank = finals.mapIndexedNotNull { index, text ->
            text.trim().takeIf { it.isNotEmpty() }?.let { index to it }
        }
        if (nonBlank.isEmpty()) return lastPartial.trim()

        val bestFinal = nonBlank.maxByOrNull { (index, _) ->
            confidences?.getOrNull(index)?.takeIf { it >= 0f } ?: if (index == 0) 0f else -1f
        }?.second ?: nonBlank.first().second

        val partial = lastPartial.trim()
        val finalTokens = tokens(bestFinal)
        val partialTokens = tokens(partial)
        if (partialTokens.size >= finalTokens.size + 2 && finalTokens.isNotEmpty()) {
            val covered = finalTokens.count { it in partialTokens }.toFloat() / finalTokens.size
            if (covered >= 0.85f) return partial
        }
        return bestFinal
    }

    private fun tokens(text: String): List<String> = WakePhraseMatcher.normalize(text)
        .split(' ')
        .filter { it.isNotBlank() }
}
