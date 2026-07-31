package com.example.meowmba.core.agent

/**
 * Recognises questions the companion should answer about *itself*.
 *
 * "What model are you using" has a correct answer sitting in app state, and asking a 2B
 * model to introspect gets you a guess at best. Measured: it replied "I'm looking at your
 * device right now to tell you what model is running" and then nothing, forever — the
 * model cannot look, so the promise never resolves.
 *
 * These questions are therefore answered from state, before any model is involved.
 */
object SelfStatusParser {

    enum class Question {
        /** Which model / provider is answering. */
        MODEL,

        /** Where it runs: on the phone, the LAN, or the cloud. */
        WHERE,
    }

    private val MODEL_PHRASES = listOf(
        "what model are you", "which model are you", "what model is this",
        "what model are you using", "which model are you using",
        "what model are you running", "which model are you running",
        "what model is running", "what llm are you", "which llm are you",
        "what ai are you", "what are you built on", "what's your model",
        "whats your model", "what is your model", "what's your brain",
        "whats your brain", "what is your brain", "which model answered",
        "what model answered", "what model just answered",
    )

    private val WHERE_PHRASES = listOf(
        "are you running locally", "are you local or cloud", "are you offline",
        "where are you running", "what are you running on",
        "are you running on my phone", "are you on device", "are you on-device",
        "are you using the cloud", "are you using my server",
    )

    fun parse(raw: String): Question? {
        val text = raw.lowercase()
            .replace(Regex("[^a-z0-9' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isBlank()) return null
        if (MODEL_PHRASES.any { text.contains(it) }) return Question.MODEL
        if (WHERE_PHRASES.any { text.contains(it) }) return Question.WHERE
        return null
    }
}
