package com.example.meowmba.core.audio

/**
 * "I'm Alice" / "enroll Bob" — bind a spoken name to the person talking.
 *
 * Deliberately strict: "I'm fine" and "this is great" are not names.
 */
object EnrollPhraseParser {

    private val PREFIX = Regex(
        "^(?:enroll|remember me as|call me|my name is)\\s+([a-z]{2,16}(?:\\s+[a-z]{2,16})?)\$",
    )
    private val INTRO = Regex("^(?:i(?:'m| am)|this is)\\s+([a-z]{2,16})\$")

    private val NOT_A_NAME = setOf(
        "fine", "good", "okay", "ok", "great", "hungry", "tired", "here",
        "ready", "back", "sorry", "just", "not", "going", "trying", "looking",
        "home", "done", "lost", "confused", "happy", "sad", "mad", "listening",
        "talking", "working", "kidding", "joking", "meow", "memba", "meowmba",
        "well", "hot", "cold", "busy", "late", "early",
        "yes", "yeah", "yep", "yup", "no", "nope", "nah", "hey", "hi", "hello",
        "what", "huh", "thanks", "please", "sure", "maybe", "why", "who",
    )

    private val ASKED = Regex("^(?:it'?s|its)\\s+([a-z]{2,16})$")
    private val BARE = Regex("^([a-z]{2,16})$")

    fun parse(raw: String): String? {
        val t = WakePhraseMatcher.normalize(raw).trim()
        PREFIX.matchEntire(t)?.groupValues?.getOrNull(1)?.let { return clean(it) }
        INTRO.matchEntire(t)?.groupValues?.getOrNull(1)?.let { return clean(it) }
        return null
    }

    /** After Meow asked "what's your name?", a bare "Alice" counts. */
    fun parseAskedReply(raw: String): String? {
        parse(raw)?.let { return it }
        val t = WakePhraseMatcher.normalize(raw).trim()
        ASKED.matchEntire(t)?.groupValues?.getOrNull(1)?.let { return clean(it) }
        BARE.matchEntire(t)?.groupValues?.getOrNull(1)?.let { return clean(it) }
        return null
    }

    fun displayName(raw: String): String =
        raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.titlecase() }
        }

    private fun clean(name: String): String? {
        val n = name.trim().lowercase()
        if (n.isBlank() || n in NOT_A_NAME) return null
        if (n.any { it.isDigit() }) return null
        return n
    }
}
