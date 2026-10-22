package com.example.meowmba.core.setup

import com.example.meowmba.core.agent.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

/** Synthetic probes only: no user screen, messages, inventory or tool execution.
 * Passing is a readiness check, not a guarantee of success in arbitrary apps. */
object PlannerReadiness {
    fun cloudStamp(model: String?, key: String?): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest("${model.orEmpty()}\u0000${key.orEmpty()}".toByteArray()).joinToString("") { "%02x".format(it) }
    suspend fun verify(provider: ModelProvider) = withTimeout(120_000) {
        val navigation = PhoneAgentLoop.generateAction(provider, """
            Return ONE JSON object only. User: Open Facebook and start a post, but do not publish it.
            Facebook is already open. Actual visible controls: 1. What's on your mind? 2. Notifications.
            Choose the next action from tap_index(index), done(summary), blocked(summary).
            Opening the app alone does not finish the request. Use {"action":"tap_index","index":number} to open the composer.
        """.trimIndent())
        check(navigationPasses(navigation)) { "Model could not plan the next screen action. Choose another model and retry." }
        val context = provider.generate("""
            Return ONE JSON object only. Conversation reference: User: I want to read agenda.md, not budget.md.
            Meow: Shall I read agenda.md? Current user: Yes, read that file.
            Available tool: files.read with string argument path. No tools have run yet.
            Resolve 'that file' using the conversation. Format: {"action":"tool","tool":"files.read","args":{"path":"exact filename"}}
        """.trimIndent())
        check(context is ModelResponse.Complete && contextPasses(context.text)) { "Model did not retain the file reference in a follow-up. Choose another model and retry." }
    }
    fun navigationPasses(text: String): Boolean = PhoneAgentLoop.parseAction(text)?.let { it.type == "tap_index" && it.index == 1 } == true
    fun contextPasses(text: String): Boolean = runCatching {
        val obj = Json.parseToJsonElement(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()).jsonObject
        obj["action"]?.jsonPrimitive?.content == "tool" && obj["tool"]?.jsonPrimitive?.content == "files.read" &&
            obj["args"]?.jsonObject?.get("path")?.jsonPrimitive?.content == "agenda.md"
    }.getOrDefault(false)
}
