package com.example.meowmba.core.audio

/**
 * Builds the tokenised keyword list the sherpa-onnx spotter needs.
 *
 * The model does not take plain text. Keywords are BPE token sequences in its own
 * 500-entry vocabulary, `▁` marking a word start — exactly the format of the
 * `keywords.txt` that ships with the model:
 *
 *     HELLO WORLD  ->  ▁HE LL O ▁WORLD
 *     HEY SIRI     ->  ▁HE Y ▁S I RI
 *
 * The built-in phrases below were produced with the model's own `bpe.model` via
 * sentencepiece and verified to reproduce those shipped lines byte for byte
 * (`scripts/kws_tokenise.py` regenerates them).
 *
 * A user-chosen companion name cannot be pre-tokenised, so [tokenise] falls back to
 * greedy longest-match over the vocabulary. That is a close approximation of BPE rather
 * than a guarantee of the identical split, so a custom name may detect slightly less
 * reliably than a built-in one; run the script and add it here if it matters.
 */
object KwsKeywords {

    /** Verified exact BPE splits for the phrases the app ships with. */
    private val EXACT = mapOf(
        "hey meow" to "▁HE Y ▁ME OW",
        "hey memba" to "▁HE Y ▁ME M B A",
        "hey meowmba" to "▁HE Y ▁ME OW M B A",
    )

    /**
     * One tokenised line per phrase, ready for `KeywordSpotter.createStream`.
     *
     * @param vocab tokens from the model's tokens.txt, used for the greedy fallback.
     */
    fun build(phrases: List<String>, vocab: Set<String>): String =
        phrases
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { phrase ->
                EXACT[phrase.lowercase()] ?: tokenise(phrase, vocab)
            }
            .distinct()
            .joinToString("\n")

    /**
     * Greedy longest-match tokenisation of [phrase] against [vocab].
     *
     * Returns null when any part of the phrase cannot be covered — better to drop a
     * keyword than to feed the spotter a sequence containing tokens the model can never
     * emit, which would simply never fire.
     */
    fun tokenise(phrase: String, vocab: Set<String>): String? {
        if (vocab.isEmpty()) return null
        val words = phrase.uppercase()
            .replace(Regex("[^A-Z0-9' ]"), " ")
            .split(' ')
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        val out = mutableListOf<String>()
        for (word in words) {
            var i = 0
            var first = true
            while (i < word.length) {
                // Word-initial pieces carry the ▁ marker; later pieces do not.
                val prefix = if (first) "▁" else ""
                var matched: String? = null
                for (end in word.length downTo i + 1) {
                    val candidate = prefix + word.substring(i, end)
                    if (candidate in vocab) {
                        matched = candidate
                        i = end
                        break
                    }
                }
                if (matched == null) return null   // uncoverable
                out += matched
                first = false
            }
        }
        return out.joinToString(" ")
    }

    /** Parses a sherpa tokens.txt ("<token> <id>" per line) into a vocabulary set. */
    fun parseVocab(lines: Sequence<String>): Set<String> =
        lines.mapNotNull { line ->
            val cut = line.trimEnd().lastIndexOf(' ')
            if (cut <= 0) null else line.substring(0, cut)
        }.toSet()
}
