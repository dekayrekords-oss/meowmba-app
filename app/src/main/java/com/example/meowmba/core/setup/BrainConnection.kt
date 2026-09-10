package com.example.meowmba.core.setup

import com.example.meowmba.core.agent.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

/** Key authentication, chat readiness and advanced planning are different
 * checks. A temporary model outage must not discard a valid key. */
object BrainConnection {
    suspend fun verifyKey(key: String) {
        HttpClient { install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000 } }.use { client ->
            val response = client.get("https://openrouter.ai/api/v1/key") { header(HttpHeaders.Authorization, "Bearer $key") }
            check(response.status.isSuccess()) {
                when (response.status.value) {
                    401, 403 -> "OpenRouter rejected this key. Create a new API key, copy the full secret, then paste it here."
                    429 -> "OpenRouter is rate-limiting verification. Wait a moment and retry; your existing key is unchanged."
                    else -> "OpenRouter key check returned HTTP ${response.status.value}. Retry when the service is available."
                }
            }
            check(Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"] is JsonObject) { "OpenRouter returned an unexpected key response. Please retry." }
        }
    }
    suspend fun verifyChat(provider: ModelProvider) = withTimeout(60_000) {
        val response = provider.generate("Reply with one short sentence confirming you are ready to chat. Do not call tools.")
        check(response is ModelResponse.Complete && response.text.isNotBlank()) {
            if (response is ModelResponse.Error) response.message else "The model returned no answer. Try another model."
        }
    }
    /** Automatic setup tries free tool-capable models only. Paid selection is
     * explicit, even if the user expanded the paid catalog. */
    fun candidates(models: List<OpenRouterModel>, selected: String): List<OpenRouterModel> =
        if (selected.isNotBlank()) models.filter { it.id == selected && it.tools }
        else models.filter { it.free && it.tools }.take(3)
}
