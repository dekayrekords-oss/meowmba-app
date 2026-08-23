package com.example.meowmba.core.work

/**
 * "Ask Alexa to …" / "tell ChatGPT …" — hand the request to another assistant.
 *
 * Meow cannot sit inside those apps. It opens them (or their web UI) with the
 * user's words. If the app is missing, the web page still carries the query.
 */
data class AssistantRelay(
    val target: Target,
    val query: String,
) {
    enum class Target(
        val spoken: String,
        val packageName: String,
        val aliases: Set<String>,
    ) {
        ALEXA("Alexa", "com.amazon.dee.app", setOf("alexa", "echo", "amazon alexa")),
        GOOGLE("Google", "com.google.android.googlequicksearchbox", setOf("google", "google assistant", "assistant")),
        GEMINI("Gemini", "com.google.android.apps.bard", setOf("gemini", "bard")),
        CHATGPT("ChatGPT", "com.openai.chatgpt", setOf("chatgpt", "chat gpt", "gpt", "openai")),
        CLAUDE("Claude", "com.anthropic.claude", setOf("claude", "anthropic")),
        ;

        fun webUrl(query: String): String {
            val q = java.net.URLEncoder.encode(query, "UTF-8")
            return when (this) {
                ALEXA -> "https://alexa.amazon.com"
                GOOGLE -> "https://www.google.com/search?q=$q"
                GEMINI -> "https://gemini.google.com/app?q=$q"
                CHATGPT -> "https://chatgpt.com/?q=$q"
                CLAUDE -> "https://claude.ai/new?q=$q"
            }
        }
    }

    companion object {
        fun parse(text: String): AssistantRelay? {
            val t = text.trim().lowercase().replace(Regex("\\s+"), " ")
            if (t.isEmpty()) return null
            val m = Regex(
                "^(?:hey meow |ok meow |please )?" +
                    "(?:ask|tell|relay to|send to|pass to|have) " +
                    "(.+?) " +
                    "(?:to |that |if )?" +
                    "(.+)$",
            ).find(t) ?: return null
            val who = m.groupValues[1].trim()
            val query = m.groupValues[2].trim()
            if (query.length < 2) return null
            if (who in setOf("me", "meow", "memba")) return null
            val target = Target.entries.firstOrNull { cand ->
                cand.aliases.any { alias -> who == alias || who.endsWith(" $alias") }
            } ?: return null
            return AssistantRelay(target, query)
        }
    }
}
