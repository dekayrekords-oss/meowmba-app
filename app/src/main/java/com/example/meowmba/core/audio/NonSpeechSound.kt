package com.example.meowmba.core.audio

/** Decoder stage directions are observations, never user commands or evidence of danger. */
object NonSpeechSound {
    enum class Kind { HUMAN, BACKGROUND }
    data class Observation(val kind: Kind, val label: String)
    private val bracket = Regex("[\\[(*]([^\\[\\]()*]+)[\\])*]")
    private val human = Regex("\\b(?:sigh(?:s|ing)?|cough(?:s|ing)?|laugh(?:s|ing|ter)?|chuckl(?:e|es|ing)|sneez(?:e|es|ing)|sniffl(?:e|es|ing)|gasp(?:s|ing)?|sob(?:s|bing)?|cry(?:ing)?|groan(?:s|ing)?|hiccup(?:s|ing)?|breathing|snoring|muffled speaking)\\b", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Observation? {
        // A transcript cut off mid-tag ends with a dangling "(" or "(click": still only tags.
        val text = raw.trim().trimEnd('.', ',', '!', '?').trim()
            .replace(Regex("\\s*[\\[(][^\\[\\]()]{0,40}$"), "").trim()
            .ifEmpty { raw.trim() }
        val parts = bracket.findAll(text).toList()
        if (parts.isEmpty() || bracket.replace(text, "").isNotBlank()) return null
        val labels = parts.map { it.groupValues[1].trim().lowercase() }
        if (labels.any { it.length > 80 || it.none(Char::isLetter) }) return null
        // Parentheses can also surround actual words. Preserve explicit spoken imperatives.
        if (labels.any { Regex("^(?:open|close|play|pause|stop|turn|read|tell|show|please|can you|could you)\\s+\\S+").containsMatchIn(it) }) return null
        val kind = if (labels.all {
            human.matches(it.removePrefix("human ").removePrefix("person ").removePrefix("soft ").removePrefix("loud "))
        }) Kind.HUMAN else Kind.BACKGROUND
        return Observation(kind, labels.joinToString(", ").take(100))
    }
}
