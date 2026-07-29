package com.example.meowmba.core.audio

/**
 * Splits a streaming model reply into chunks that are safe to speak immediately.
 *
 * This is what makes the companion feel like a live call rather than a walkie-talkie:
 * the first sentence goes to TTS as soon as the model has finished it, instead of the
 * user sitting in silence until the whole answer is generated.
 *
 * Only COMPLETE sentences are released. Feeding TTS raw token deltas produces
 * stuttering nonsense, because prosody is chosen per utterance.
 */
object SpeechChunker {

    /**
     * Shortest chunk worth speaking on its own. Guards against list markers ("1.") and
     * abbreviations ("e.g.") being mistaken for the end of a sentence.
     */
    const val MIN_CHUNK_CHARS = 12

    /**
     * The next run of complete sentences in [full] starting at [from], or null when the
     * model has not produced a full sentence yet.
     *
     * Returns the RAW substring (caller trims) so its length can be added to [from]
     * without losing track of position in [full].
     */
    fun nextChunk(full: String, from: Int): String? {
        if (from < 0 || from >= full.length) return null
        val rest = full.substring(from)

        // Take the LAST boundary available, so a burst of tokens containing several
        // sentences is spoken as one natural utterance rather than several clipped ones.
        var lastEnd = -1
        for (i in rest.indices) {
            when (rest[i]) {
                // A newline is a break on its own — requiring whitespace AFTER it
                // meant list output ("...three\nCulver's") was never released.
                '\n' -> lastEnd = i
                // Punctuation only ends a sentence when followed by a break, so
                // decimals and "e.g." do not split mid-word.
                '.', '!', '?' ->
                    if (i == rest.lastIndex || rest[i + 1].isWhitespace()) lastEnd = i
                else -> {}
            }
        }
        if (lastEnd < 0) return null

        val chunk = rest.substring(0, lastEnd + 1)
        return if (chunk.trim().length >= MIN_CHUNK_CHARS) chunk else null
    }
}
