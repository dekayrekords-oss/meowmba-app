package com.example.meowmba.core.audio

/**
 * The 20M zipformer always-on model emits SHOUTING (all caps).
 * Turn that into readable sentence case; leave mixed-case text alone.
 */
object SpokenText {

    /**
     * Drop ASR junk so a stray "e" / "a b" does not become a turn.
     * Always-on AiAi and the 20M zipformer both emit these on silence.
     */
    fun isUsable(raw: String): Boolean {
        val t = normalize(raw).trim()
        if (t.isEmpty()) return false
        val letters = t.filter { it.isLetter() }
        if (letters.length < 2) return false
        val compact = t.lowercase().replace(Regex("[^a-z0-9 ]+"), "").trim()
        if (compact in FILLERS || compact in FRAGMENTS) return false
        if (Regex("^[a-z](?: [a-z])+$").matches(compact)) return false
        val words = compact.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.any { it in FRAGMENTS } && words.size <= 2) return false
        if (words.size == 1) {
            val w = words[0]
            if (w.length <= 2 && w !in SHORT_OK) return false
        }
        return true
    }

    /**
     * True when [raw] looks like a finished phrase, not a cut-off hypothesis.
     * Always-on used to commit "set a" / "what time" mid-sentence — that is
     * most of the "it heard me but got it wrong" reports.
     */
    fun looksFinished(raw: String): Boolean {
        if (!isUsable(raw)) return false
        val words = normalize(raw).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size == 1) {
            val w = words[0].lowercase().trimEnd('.', ',', '?', '!')
            return w in SHORT_OK || w.length >= 4
        }
        val last = words.last().lowercase().trimEnd('.', ',', '?', '!', ';', ':')
        return last !in UNFINISHED_TAILS
    }

    /**
     * Pick the best n-best hypothesis. Prefer the highest confidence among
     * usable strings; if the engine sent no scores, take the longest usable.
     */
    fun bestTranscript(matches: List<String>?, scores: FloatArray? = null): String {
        if (matches.isNullOrEmpty()) return ""
        data class Cand(val text: String, val score: Float, val usable: Boolean)
        val cands = matches.mapIndexed { i, raw ->
            val text = normalize(raw).trim()
            val score = scores?.getOrNull(i) ?: Float.NaN
            Cand(text, score, isUsable(text))
        }
        val usable = cands.filter { it.usable }
        if (usable.isNotEmpty()) {
            val scored = usable.filter { !it.score.isNaN() }
            if (scored.isNotEmpty()) return scored.maxBy { it.score }.text
            return usable.maxBy { it.text.length }.text
        }
        return cands.firstOrNull { it.text.isNotBlank() }?.text ?: ""
    }

    /**
     * Merge a final result with the last partial. Official wins unless it is
     * empty/junk and the partial is a complete usable phrase (AiAi on this
     * Fold often ends with an empty final).
     */
    fun preferFinal(official: String, partial: String): String {
        val o = normalize(official).trim()
        val p = normalize(partial).trim()
        val oOk = isUsable(o)
        val pOk = isUsable(p)
        if (oOk && pOk) {
            return if (p.length >= o.length + 12 && looksFinished(p)) p else o
        }
        if (oOk) return o
        if (pOk) return p
        return o.ifBlank { p }
    }

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val letters = trimmed.filter { it.isLetter() }
        if (letters.isEmpty()) return trimmed
        val upperShare = letters.count { it.isUpperCase() }.toFloat() / letters.length
        if (upperShare < 0.6f) return trimmed
        val lower = trimmed.lowercase()
        return sentenceCase(lower)
    }

    /**
     * Sentence-case plus the 20M zipformer's known greeting mangling.
     * Measured on device: "Yo, what's up buddy" → "Ovites up body" / "Ulat's up buddy".
     */
    fun polish(raw: String): String = repair(normalize(raw))

    internal fun repair(sentence: String): String {
        if (sentence.isBlank()) return sentence
        val compact = sentence.lowercase()
            .replace(Regex("[^a-z0-9'\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        var t = compact
        t = t.replace(
            Regex("""\b(ulat'?s|ulats|oot'?s|oots|ovites|ovite'?s|wot'?s|wats)\s+up\b"""),
            "what's up",
        )
        t = t.replace(Regex("""\bup body\b"""), "up buddy")
        t = t.replace(Regex("""\bbudd\b"""), "buddy")
        if (t == compact) return sentence
        return sentenceCase(t)
    }

    private fun sentenceCase(lower: String): String = buildString(lower.length) {
        var capNext = true
        for (c in lower) {
            if (capNext && c.isLetter()) {
                append(c.titlecaseChar())
                capNext = false
            } else {
                append(c)
                if (c == '.' || c == '!' || c == '?') capNext = true
                if (c.isWhitespace() && lastOrNull() in setOf('.', '!', '?')) capNext = true
            }
        }
    }

    private val FILLERS = setOf(
        "uh", "um", "er", "ah", "eh", "mm", "mmm", "hmm", "hm",
        "huh", "oh", "ooh", "the", "a", "an", "and", "to", "of",
    )

    private val SHORT_OK = setOf(
        "ok", "no", "hi", "yo", "go", "up", "on", "off", "yes",
    )

    /** Last words that mean the decoder cut the user off mid-phrase. */
    private val UNFINISHED_TAILS = setOf(
        "a", "an", "the", "to", "of", "and", "or", "my", "your", "for",
        "with", "in", "on", "at", "i", "i'm", "im", "we", "it's", "its",
        "be", "is", "are", "was", "want", "wanna", "gonna", "gotta",
    )

    /**
     * Word-tail scraps AiAi / zipformer emit on room noise.
     * "ular" is the one that kept firing on this Fold.
     */
    private val FRAGMENTS = setOf(
        "ular", "ulas", "ula", "uler", "ulur",
        "tion", "sion", "ation", "ness", "ment", "ible", "able",
        "ally", "ially", "ious", "uous", "izing", "ising", "ated", "ator",
        "ical", "icle", "ency", "ance", "ence", "ough", "ight", "ound",
        "yeah", "yep", "mmm", "uhhuh", "uhuh", "mmhm",
    )
}
