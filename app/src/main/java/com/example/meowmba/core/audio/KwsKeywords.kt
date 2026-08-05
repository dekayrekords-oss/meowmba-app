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
    /**
     * Verified exact BPE splits, from the model's own bpe.model via
     * scripts/kws_tokenise.py (whose self-check reproduces the model's shipped
     * keywords.txt 9/9).
     */
    private val EXACT = mapOf(
        // "yomeow" as one word, alongside the spaced variants.
        "yomeow" to "▁ Y OM E OW",
        "yo meow" to "▁ Y O ▁ME OW",
        "hey meow" to "▁HE Y ▁ME OW",
        "ok meow" to "▁O K ▁ME OW",
        "okay meow" to "▁OKAY ▁ME OW",
        "hey kitty" to "▁HE Y ▁K IT TY",
        "yo memba" to "▁ Y O ▁ME M B A",
        "hey memba" to "▁HE Y ▁ME M B A",
        "oi meow" to "▁O I ▁ME OW",
        "aye meow" to "▁A Y E ▁ME OW",
        "yo meowmba" to "▁ Y O ▁ME OW M B A",
        "hey meowmba" to "▁HE Y ▁ME OW M B A",

        // ── Homophones ───────────────────────────────────────────────────────────
        // "MEOW" is rare in GigaSpeech, which this model was trained on, so its token
        // sequence is acoustically weak — measured on device, "hey kitty" fired and
        // "hey meow" did not. The fix is not only a lower threshold: it is to also
        // register spellings the model is MORE likely to emit for the same sound, so a
        // decode that comes out "yo mao" still counts as the wake phrase. The user never
        // sees these; they are alternative spellings of one noise.
        "yomao" to "▁ Y OM A O",
        "yomeo" to "▁ Y OM E O",
        "yo mao" to "▁ Y O ▁MA O",
        "yo meo" to "▁ Y O ▁ME O",
        "yo mow" to "▁ Y O ▁MO W",
        "yo mew" to "▁ Y O ▁ME W",
        "yo miao" to "▁ Y O ▁MI A O",
        "hey mao" to "▁HE Y ▁MA O",
        "hey meo" to "▁HE Y ▁ME O",
        "hey mew" to "▁HE Y ▁ME W",
        "ok mao" to "▁O K ▁MA O",
        "ok mew" to "▁O K ▁ME W",
        "okay mao" to "▁OKAY ▁MA O",

        // The "hey"/"ok" carriers needed more than homophones of MEOW. Measured on
        // device: with the tuning fix in place, every "yo…" spelling fired and not one
        // "hey meow" or "ok meow" did — the ▁HE Y ▁ME OW path is simply weak in this
        // model. These add the run-together spellings (heymeow, okmeow — no word break
        // before ME) and the drawn-out ones, which is how the phrase is actually said.
        // The H in "hey" is the weak link: it is barely voiced in casual speech, so the
        // model regularly decodes the phrase with NO H ("ey meow") or a reduced vowel
        // ("eh meow"). Those decodes used to be discarded, which is why "hey meow"
        // needed several tries while "yo meow" fired first time — every token below is
        // checked against tokens.txt (KwsKeywordsTest), because a sequence the model
        // cannot emit is worse than useless: it never fires and says nothing about why.
        // ── 2026-08-05 wave: user report "only yo meow works" ─────────────────
        // More vowel/h-dropping decodes of the hey carriers, same sound, spellings the
        // model emits instead of "hey meow". Every token verified against tokens.txt.
        "hey miaw" to "▁HE Y ▁MI A W",
        "hey miow" to "▁HE Y ▁MI O W",
        "hey miou" to "▁HE Y ▁MI O U",
        "hey meao" to "▁HE Y ▁ME A O",
        "hey meou" to "▁HE Y ▁ME O U",
        "hey meu" to "▁HE Y ▁ME U",
        "hey mia" to "▁HE Y ▁MI A",
        "hey meuw" to "▁HE Y ▁ME U W",
        "hey maow" to "▁HE Y ▁MA O W",
        "hey maw" to "▁HE Y ▁MA W",
        "he meow" to "▁HE ▁ME OW",
        "ay meow" to "▁A Y ▁ME OW",
        "yo miaw" to "▁ Y O ▁MI A W",
        "yo miow" to "▁ Y O ▁MI O W",
        "yo meao" to "▁ Y O ▁ME A O",
        "ok miaw" to "▁O K ▁MI A W",

        "ey meow" to "▁E Y ▁ME OW",
        "eh meow" to "▁E H ▁ME OW",
        "eymeow" to "▁E Y ME OW",
        "heymeow" to "▁HE Y ME OW",
        "haymeow" to "▁HA Y ME OW",
        "hay meow" to "▁HA Y ▁ME OW",
        "hi meow" to "▁HI ▁ME OW",
        "hey meaow" to "▁HE Y ▁ME A OW",
        "hey meeow" to "▁HE Y ▁ME E OW",
        "hey miao" to "▁HE Y ▁MI A O",
        "hey me ow" to "▁HE Y ▁ME ▁ OW",
        "okmeow" to "▁O K ME OW",
        "okey meow" to "▁O KE Y ▁ME OW",
        "ok me ow" to "▁O K ▁ME ▁ OW",
    )

    /**
     * Per-keyword `:boost #threshold`, the same syntax the model's own keywords.txt uses.
     *
     * This tuning used to be lost. The bundled keywords.txt carried it, but the phrases
     * actually in force come from `createStream(inlineKeywords)`, which replaces that file
     * — and [build] emitted bare token lines, so every phrase silently fell back to the
     * engine-wide 2.0 / 0.20. The tuned 3.0 / 0.10 the file documented was never reaching
     * the spotter.
     *
     * Higher boost and lower threshold both make a phrase easier to trigger. The wake
     * phrases sit well past sherpa's 1.5 / 0.25 defaults because a MISSED wake is the
     * complaint, and because two gates downstream (VAD, then speaker verification) catch
     * what a permissive threshold lets through.
     */
    private const val DEFAULT_TUNING = ":2.5 #0.12"

    private val TUNING = mapOf(
        // The primary phrase and everything that sounds like it — most permissive.
        "yomeow" to ":4.0 #0.06",
        "yo meow" to ":4.0 #0.06",
        "yomao" to ":4.0 #0.06",
        "yomeo" to ":4.0 #0.06",
        "yo mao" to ":4.0 #0.06",
        "yo meo" to ":4.0 #0.06",
        "yo mow" to ":4.0 #0.06",
        "yo mew" to ":4.0 #0.06",
        "yo miao" to ":4.0 #0.06",
        // "hey"/"ok" carriers: same treatment, since both were reported unreliable.
        "hey meow" to ":5.0 #0.04",
        "hey mao" to ":5.0 #0.04",
        "hey meo" to ":5.0 #0.04",
        "hey mew" to ":5.0 #0.04",
        "ok meow" to ":4.0 #0.06",
        "okay meow" to ":4.0 #0.06",
        "ok mao" to ":4.0 #0.06",
        "ok mew" to ":4.0 #0.06",
        "okay mao" to ":4.0 #0.06",
        "ey meow" to ":5.0 #0.04",
        "eh meow" to ":5.0 #0.04",
        "eymeow" to ":5.0 #0.04",
        "heymeow" to ":5.0 #0.04",
        "haymeow" to ":5.0 #0.04",
        "hay meow" to ":5.0 #0.04",
        "hi meow" to ":5.0 #0.04",
        "hey meaow" to ":5.0 #0.04",
        "hey meeow" to ":5.0 #0.04",
        "hey miao" to ":5.0 #0.04",
        "hey me ow" to ":5.0 #0.04",
        "okmeow" to ":4.0 #0.06",
        "okey meow" to ":5.0 #0.04",
        "ok me ow" to ":4.0 #0.06",
        // Longer and more distinctive: they do not need the help, and loosening them
        // would only buy false wakes.
        "hey miaw" to ":5.0 #0.04",
        "hey miow" to ":5.0 #0.04",
        "hey miou" to ":5.0 #0.04",
        "hey meao" to ":5.0 #0.04",
        "hey meou" to ":5.0 #0.04",
        "hey meu" to ":5.0 #0.04",
        "hey mia" to ":5.0 #0.04",
        "hey meuw" to ":5.0 #0.04",
        "hey maow" to ":5.0 #0.04",
        "hey maw" to ":5.0 #0.04",
        "he meow" to ":5.0 #0.04",
        "ay meow" to ":5.0 #0.04",
        "yo miaw" to ":4.0 #0.06",
        "yo miow" to ":4.0 #0.06",
        "yo meao" to ":4.0 #0.06",
        "ok miaw" to ":4.0 #0.06",
        "hey kitty" to ":2.0 #0.15",
        "yo memba" to ":2.5 #0.12",
        "hey memba" to ":2.5 #0.12",
        "yo meowmba" to ":2.5 #0.12",
        "hey meowmba" to ":2.5 #0.12",
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
                val key = phrase.lowercase()
                val tokens = EXACT[key] ?: tokenise(phrase, vocab) ?: return@mapNotNull null
                "$tokens ${TUNING[key] ?: DEFAULT_TUNING}"
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
