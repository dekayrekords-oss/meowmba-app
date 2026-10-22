package com.example.meowmba.core.nav

sealed interface PlaceVoiceIntent {
    object Current : PlaceVoiceIntent
    object Destination : PlaceVoiceIntent
    object AskName : PlaceVoiceIntent
    data class Save(val name: String) : PlaceVoiceIntent
    data class Go(val name: String) : PlaceVoiceIntent
    /** "I'm heading home", "on my way to work": directions to a saved place (or an honest "not saved yet"). */
    data class Heading(val name: String) : PlaceVoiceIntent
    /** "I'm home now", "just got to work": remember this spot under that name the first time. */
    data class Arrived(val name: String) : PlaceVoiceIntent

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
            Regex("^(?:(?:i'm|im|i am|we're|we are)\\s+)?(?:heading|headed|going|driving|walking|leaving for|on my way|on our way)\\s+(?:back\\s+)?(?:to\\s+)?(.+?)(?:\\s+(?:now|right now|soon))?$").matchEntire(t)?.let {
                return placeWord(it.groupValues[1])?.let(::Heading)
            }
            Regex("^(?:(?:i'm|im|i am|we're|we are)\\s+(?:back\\s+)?(?:at\\s+)?|(?:just\\s+)?(?:got|made it|arrived)\\s+(?:back\\s+)?(?:to\\s+|at\\s+)?)(home|work|the office|school|the gym|the store)(?:\\s+(?:now|right now|already))?$").matchEntire(t)?.let {
                return placeWord(it.groupValues[1])?.let(::Arrived)
            }
            return null
        }

        /** Words that are places even before they are saved. Anything else must be a saved place name. */
        val COMMON_PLACES = setOf("home", "work", "school", "gym", "store", "grocery store", "airport", "office", "church",
            "doctor", "hospital", "mall", "park", "beach", "bank", "post office", "pharmacy")

        /** A destination that names a place (home, work, a saved name), not an activity ("going to sleep"). */
        fun placeWord(raw: String): String? {
            val n = cleanName(raw) ?: return null
            if (Regex("^(sleep|bed|eat|lunch|dinner|shower|work on|be|try|do|get|make|call|buy|pick|see|watch|take)\\b").containsMatchIn(n)) return null
            return when (n) { "the office", "office", "my job", "job" -> "work"; "the house", "my house", "my place" -> "home"; else -> n }
                .takeIf { it.split(' ').size <= 4 }
        }

        fun cleanName(raw: String): String? = raw.trim().trim('"','“','”','\'').removePrefix("call it ")
            .removePrefix("name it ").removePrefix("my ").removePrefix("the ").trim().trim('"','“','”','\'').takeIf { it.length in 1..80 }

        fun isNameReply(text: String): Boolean = cleanName(text)!=null &&
            !Regex("^(where|what|how|when|do you|can you|open|stop|cancel|remember|navigate|please)\\b",RegexOption.IGNORE_CASE).containsMatchIn(text.trim())
    }
}
