package com.example.meowmba.ui.companion

/**
 * Pure utterance classifiers. Kept off [CompanionViewModel] so routing rules
 * can be read and tested without constructing a 5k-line ViewModel.
 *
 * [CompanionViewModel] companion methods delegate here so existing tests keep
 * compiling as `CompanionViewModel.extractSearchQuery(...)`.
 */
object SpeechIntents {

    /** Verbs that turn "…browser" into a show-in-the-browser request. */
    private val SHOW_BROWSER_VERBS = listOf("show", "open", "display", "put", "bring")

    /** "search for X", "search the web for X", "look up X", "google X", "search X". */
    private val SEARCH_PREFIXES = listOf(
        "search the web for ", "search web for ", "search online for ",
        "search for ", "look up ", "google ", "search "
    )

    /** Local-scope searches that stay with the agent (memory, notes, photos…). */
    private val SEARCH_LOCAL_SCOPES = listOf(
        "my memory", "my memories", "memory for", "memories for",
        "my notes", "my photos", "my pictures", "my files", "my emails", "my calendar"
    )

    private val PHONE_GOAL_VERBS = listOf("play ", "pause ", "skip ", "resume ")
    private val PHONE_GOAL_APPS = listOf(
        "spotify", "youtube", "instagram", "tiktok", "netflix", "hulu",
        "twitter", "snapchat", "whatsapp", "telegram", "discord", "reddit",
        "amazon", "uber", "lyft", "maps", "chrome", "firefox", "gmail",
        "messages", "photos", "camera", "clock", "calculator", "calendar"
    )

    private val QUESTION_STARTERS = listOf(
        "how", "how's", "hows", "howre", "how're",
        "what", "what's", "whats",
        "why", "when", "where", "where's", "wheres",
        "who", "who's", "whos", "which", "is", "are",
        "can", "could", "do", "does", "did", "should", "will", "would"
    )

    /** Check-ins that are questions even after a leftover "hey buddy". */
    private val GREETINGS = listOf(
        "what's up", "whats up", "what up", "sup",
        "how's it going", "hows it going", "how are you", "how're you",
        "how you doing", "how ya doing",
    )

    /** "how do I install X" is a question; "open the browser" is a command. */
    private val ACTION_STARTERS = listOf(
        "open", "send", "show", "play", "set", "turn", "make", "create", "build",
        "add", "put", "launch", "start", "call", "email", "remind", "schedule",
        "write", "draw", "take", "search", "look", "google", "find", "bring", "display",
        "ask", "tell", "relay"
    )

    /** "can/could you open ..." is really a request, not a question. */
    private val POLITE_ACTION = Regex(
        "^(can|could|will|would|please) (you )?(please )?" +
            "(open|send|show|play|set|turn|make|create|build|add|launch|start|call|" +
            "email|remind|schedule|write|draw|take|search|look|google|find|bring|display)\\b"
    )

    /** Creation verbs keep the request with the browser agent instead. */
    private val SHOW_BROWSER_CREATION_VERBS = listOf(
        "make", "build", "create", "generate", "draw", "write", "design", "code"
    )

    /** "in the browser", "on the side browser", "in browser"… */
    private val SHOW_BROWSER_PLACE = Regex("\\b(in|on)( the)? (\\w+ )?browser")

    /** Compiled once: per-utterance Regex() allocs showed up in turn-path profiles. */
    private val ON_IN_APP = Regex("\\b(?:on|in)\\s+(\\w+)\\s*$")
    private val TELL_ME_QUESTION = Regex("^tell me (how|what|why|when|where|who|which|if|about)\\b")

    /** Phrases that politely end conversation mode. */
    val STOP_COMMANDS = setOf(
        "stop", "never mind", "nevermind", "that's all", "thats all",
        "thank you", "thanks", "stop listening", "goodbye", "bye"
    )

    /** Drops a leading address — "hey meow, how do I…" still has to route. */
    fun stripAddress(normalized: String): String {
        var t = normalized.trim()
        while (true) {
            when {
                t.startsWith("hey meow ") -> t = t.removePrefix("hey meow ").trim()
                t.startsWith("hey memba ") -> t = t.removePrefix("hey memba ").trim()
                t.startsWith("hey ") -> t = t.removePrefix("hey ").trim()
                t.startsWith("meow ") -> t = t.removePrefix("meow ").trim()
                t.startsWith("memba ") -> t = t.removePrefix("memba ").trim()
                else -> return t
            }
        }
    }

    /**
     * Explicit web-search phrasing → the query to run in the browser panel.
     * Null when the utterance is not a search request (or searches local data).
     */
    fun extractSearchQuery(normalized: String): String? {
        val t = stripAddress(normalized.trim())
        // Bare verbs with no object are not searches.
        if (t in setOf("search", "search for", "look up", "google")) return null
        if (SEARCH_LOCAL_SCOPES.any { t.contains(it) }) return null
        val rest = SEARCH_PREFIXES.firstOrNull { t.startsWith(it) }
            ?.let { t.removePrefix(it).trim() }
            ?: return null
        return rest.takeIf { it.isNotBlank() }
    }

    /**
     * Detects utterances that need the multi-step phone-control loop rather
     * than single-shot execution or general conversation. Catches patterns
     * the on-device LLM router frequently misses.
     */
    fun isMultiStepPhoneGoal(text: String): Boolean {
        val t = text.lowercase().trim()
        // "play X" / "play X on Y" — but not bare "play" or "play a game with me"
        if (PHONE_GOAL_VERBS.any { t.startsWith(it) }) {
            val rest = PHONE_GOAL_VERBS.first { t.startsWith(it) }.let { t.removePrefix(it) }
            // Must have an object (not just the verb)
            if (rest.isNotBlank() && rest.split(' ').size <= 8) return true
        }
        // "X on/in [app]" — e.g. "search sublime on spotify", "find john on instagram"
        val onInMatch = ON_IN_APP.find(t)
        if (onInMatch != null) {
            val app = onInMatch.groupValues[1].lowercase()
            if (PHONE_GOAL_APPS.any { app.contains(it) }) return true
        }
        return false
    }

    /**
     * Informational questions the brain should answer directly. Commands,
     * requests, and searches keep their existing routing.
     */
    /**
     * Live speed from GPS. Must not go to the chat brain — it will invent
     * "I can't access GPS" instead of reading the phone.
     */
    fun isSpeedQuestion(normalized: String): Boolean {
        val t = stripAddress(normalized.trim())
        if (t.isEmpty()) return false
        return t.contains("how fast") ||
            t.contains("what speed") ||
            t.contains("what is my speed") ||
            t.contains("what's my speed") ||
            t.contains("whats my speed") ||
            (t.contains("how fast") && t.contains("going")) ||
            t.contains("am i speeding")
    }

    fun looksLikeDirectQuestion(normalized: String): Boolean {
        val t = stripAddress(normalized.trim())
        if (t.isEmpty()) return false
        if (POLITE_ACTION.containsMatchIn(t)) return false
        // Device specialists: camera / speed. The chat brain has no sensors.
        if (isSpeedQuestion(t)) return false
        if (com.example.meowmba.core.vision.VisionRouter.parse(t) != null) return false
        // Weather is a device lookup, not a chat guess. It is question-shaped,
        // so without this it hit the direct-question lane and the chat brain —
        // which has no live data — said it "cannot tell the weather" (measured
        // on device 2026-08-23). Falling through lands it in the main turn
        // pipeline, where AgentRouter.isWeatherQuestion answers from
        // WeatherRepository.
        if (com.example.meowmba.core.agent.runtime.AgentRouter.isWeatherQuestion(t)) return false
        val first = t.substringBefore(' ')
        val startsLikeQuestion = first in QUESTION_STARTERS
        val casualCheckIn = GREETINGS.any { t == it || t.endsWith(" $it") || t.contains(" $it") }
        val hasHowTo = t.contains("how to ")
        // "tell me how/what/why…" asks for information, not an action.
        val tellMeQuestion = TELL_ME_QUESTION.containsMatchIn(t)
        val actionRequest = ACTION_STARTERS.any { t.startsWith("$it ") }
        // "tell me how…" / trailing "?" are questions even though "tell" is
        // also an action verb.
        return (startsLikeQuestion || casualCheckIn || hasHowTo || tellMeQuestion || t.endsWith("?")) &&
            (!actionRequest || tellMeQuestion || t.endsWith("?"))
    }

    /** "show it in the browser", "open that in the side browser", etc. */
    fun looksLikeShowInBrowser(normalized: String): Boolean {
        val t = stripAddress(normalized)
        return SHOW_BROWSER_VERBS.any { t.contains(it) } &&
            SHOW_BROWSER_CREATION_VERBS.none { t.contains(it) } &&
            SHOW_BROWSER_PLACE.containsMatchIn(t)
    }
}
