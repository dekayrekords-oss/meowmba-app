package com.example.meowmba.core.agent

/** Classifier protocols are not conversational answers. Normal refusals are untouched. */
object ChatModelSuitability {
    private val specialist = Regex("(?i)(?:^|[/ _-])(?:content[-_ ]safety|safety[-_ ]guard|nemoguard|llama[-_ ]guard|moderation|embedding|rerank)(?:$|[/ _:-])")
    private val label = Regex("(?i)^(?:user|response) safety:\\s*(?:safe|unsafe)\\s*$")
    fun isChatModel(id: String, name: String = id): Boolean = !specialist.containsMatchIn("$id $name")
    fun isClassifierReply(text: String): Boolean {
        val lines = text.trim().lines().map(String::trim).filter(String::isNotEmpty)
        return lines.any { label.matches(it) } && lines.all { label.matches(it) || it.startsWith("Safety Categories:", true) }
    }
    /** Hold only the tiny classifier-like prefix until it becomes ordinary prose or a final. */
    fun holdPrefix(text: String): Boolean {
        val t = text.trimStart().lowercase()
        return listOf("user safety:", "response safety:").any { it.startsWith(t) || t.startsWith(it) }
    }
    const val ERROR = "The model returned classification labels instead of a conversation. Choose a chat model in LLM settings; no requested action was performed."
}
