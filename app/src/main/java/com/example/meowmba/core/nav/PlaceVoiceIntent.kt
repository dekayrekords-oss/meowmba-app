package com.example.meowmba.core.nav

sealed interface PlaceVoiceIntent {
    object Current : PlaceVoiceIntent
    object Destination : PlaceVoiceIntent
    object AskName : PlaceVoiceIntent
    data class Save(val name: String) : PlaceVoiceIntent
    data class Go(val name: String) : PlaceVoiceIntent

    companion object {
        fun parse(text: String): PlaceVoiceIntent? {
            val t = text.trim().lowercase().replace('’','\'').trimEnd('.', '!', '?')
                .replace(Regex("^(?:(?:ok|okay|please),?\\s+)+"), "")
            if (t in setOf("where are we", "where am i", "where are we right now", "where am i right now", "what is my location", "what's my location", "what is our location", "do you know where we are")) return Current
            if (t in setOf("where are we going", "where am i going", "where are we headed", "do you know where we're going", "do you know where we are going")) return Destination
            if (t in setOf("remember here", "remember this place", "save this place", "save here", "remember this location")) return AskName
            Regex("^(?:remember here as|remember this place as|remember this location as|save this place as|remember this as) (.+)$").matchEntire(t)?.let {
                return cleanName(it.groupValues[1])?.let(::Save)
            }
            Regex("^(?:let's go to|lets go to|go back to|take me back to|navigate to|take me to|go to) (.+)$").matchEntire(t)?.let {
                return cleanName(it.groupValues[1])?.let(::Go)
            }
            return null
        }

        fun cleanName(raw: String): String? = raw.trim().trim('"','“','”','\'').removePrefix("call it ")
            .removePrefix("name it ").removePrefix("my ").removePrefix("the ").trim().trim('"','“','”','\'').takeIf { it.length in 1..80 }

        fun isNameReply(text: String): Boolean = cleanName(text)!=null &&
            !Regex("^(where|what|how|when|do you|can you|open|stop|cancel|remember|navigate|please)\\b",RegexOption.IGNORE_CASE).containsMatchIn(text.trim())
    }
}
