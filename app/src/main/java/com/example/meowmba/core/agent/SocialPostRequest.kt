package com.example.meowmba.core.agent

/** Explicit social composition requests, never mentions, questions or HTML mockups. */
object SocialPostRequest {
    private val platform = "(?:facebook|fact book|face book|linkedin|linked in|linked it|reddit|instagram|twitter|x)"
    private val prefix = "^(?:please )?(?:(?:can|could|would) you )?(?:please )?"
    private val compose = Regex(prefix + "(?:make|write|draft|create|compose|start) (?:me )?(?:(?:a|the|this|new) ){0,2}(?:($platform) post|post (?:on|in|for|to) (?:my |the )?($platform))\\b", RegexOption.IGNORE_CASE)
    private val open = Regex(prefix + "(?:open|launch) (?:the app )?($platform)(?: app)? (?:and then|and|then|to) (?:start|create|open|write|make|draft|compose|publish) (?:a |the |my |new )?post\\b", RegexOption.IGNORE_CASE)
    private fun target(text: String): String? {
        val request = text.trim().replace(Regex("\\s+"), " ")
            .replace(Regex("^(?:(?:hey|hi|ok|okay) )?(?:meowmba|memba|meow)[, ]+", RegexOption.IGNORE_CASE), "")
        val match = compose.find(request) ?: open.find(request) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.lowercase()
    }
    fun matches(text: String) = target(text) != null
    fun facebook(text: String) = target(text) in setOf("facebook", "fact book", "face book")
}
