package com.example.meowmba.core.agent.runtime

object MusicRequest {
    data class Request(val query: String, val app: String?)
    fun parse(raw: String): Request? {
        val text = AgentRouter.stripAddress(raw).trim().trimEnd('.', '!', '?')
            .replace(Regex("^(?:(?:can|could|would|will) you |i want you to )", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^please ", RegexOption.IGNORE_CASE), "")
        val m = Regex("^(?:play|put on) (.+?)(?: (?:on|in|using) (Spotify|YouTube Music|Apple Music|Amazon Music|Pandora|Tidal|Deezer))?$", RegexOption.IGNORE_CASE)
            .matchEntire(text) ?: return null
        val query = m.groupValues[1].trim().removeSuffix(" please")
        if (query.startsWith("with ", ignoreCase = true)) return null
        if (Regex("(?i)\\b(video|movie|game|animation|cozmo|looi|act|dance|trick)\\b").containsMatchIn(query)) return null
        if (query.lowercase() in setOf("with me", "a sound", "that again", "it again", "something", "the piano", "piano", "the guitar", "guitar")) return null
        return Request(if (query.lowercase() in setOf("music", "some music", "the music", "something")) "" else query,
            m.groupValues[2].ifBlank { null })
    }
}
