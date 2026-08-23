package com.example.meowmba.core.agent

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class OpenRouterModel(
    val id: String,
    val name: String,
    val vision: Boolean,
    val free: Boolean,
)

/**
 * Live catalog from OpenRouter (`GET /api/v1/models`). The list is public;
 * a key is sent when we have one so the catalog matches the account.
 */
object OpenRouterModels {

    private val client = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
    }

    suspend fun fetch(apiKey: String? = null): Result<List<OpenRouterModel>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = client.get("https://openrouter.ai/api/v1/models") {
                    parameter("limit", 1000)
                    header("HTTP-Referer", "https://meowmba.local")
                    header("X-Title", "MEOWMBA")
                    if (!apiKey.isNullOrBlank()) {
                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                    }
                }.bodyAsText()
                parse(body)
            }
        }

    fun parse(body: String): List<OpenRouterModel> {
        val data = Json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?: return emptyList()
        return data.mapNotNull { el ->
            val obj = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: id
            val arch = obj["architecture"] as? kotlinx.serialization.json.JsonObject
            val inputs = (arch?.get("input_modalities") as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: emptyList()
            val outputs = (arch?.get("output_modalities") as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: listOf("text")
            if (outputs.isNotEmpty() && "text" !in outputs) return@mapNotNull null
            val pricing = obj["pricing"] as? kotlinx.serialization.json.JsonObject
            val prompt = pricing?.get("prompt")?.jsonPrimitive?.contentOrNull
            OpenRouterModel(
                id = id,
                name = name,
                vision = "image" in inputs,
                free = id.endsWith(":free", ignoreCase = true) || prompt == "0",
            )
        }.sortedWith(
            compareByDescending<OpenRouterModel> { it.free }
                .thenBy { it.name.lowercase() }
        )
    }
}
