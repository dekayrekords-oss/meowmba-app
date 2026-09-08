package com.example.meowmba.core.agent

import android.util.Log
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * [ModelProvider] for remote streaming APIs.
 *
 * Two wire protocols are supported:
 *  - Ollama (POST /api/generate, newline-delimited JSON)
 *  - OpenAI-compatible (POST /v1/chat/completions, Server-Sent Events) —
 *    also covers llama.cpp, vLLM, and other OpenAI-compatible servers.
 *
 * Rules enforced here (per the project's code quality contract):
 *  - HTTP status is checked before parsing — errors are reported honestly
 *  - A missing API key fails fast instead of sending "Bearer null"
 *  - Generation timeouts are generous, connect timeouts are short
 */
class RemoteModelProvider(
    override val name: String,
    private val baseUrl: String,
    private val apiKey: String?,
    private val model: String,
    private val style: ApiStyle = if (name.contains("ollama", ignoreCase = true)) {
        ApiStyle.OLLAMA
    } else {
        ApiStyle.OPENAI_COMPATIBLE
    }
) : ModelProvider, java.io.Closeable {

    enum class ApiStyle { OLLAMA, OPENAI_COMPATIBLE }

    /**
     * Defense in depth: if a base url is ever stored with a trailing "/v1"
     * (e.g. "https://openrouter.ai/api/v1"), appending "/v1/chat/completions"
     * would hit "/v1/v1/chat/completions" — a guaranteed 404, which is exactly
     * what shipped to the field. Stripping the suffix here makes the provider
     * immune to that. Ollama-style base urls (no /v1) pass through unchanged.
     */
    private val normalizedBase: String = baseUrl.trimEnd('/').removeSuffix("/v1")

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 300_000   // local models can be slow
            socketTimeoutMillis = 300_000
        }
    }

    override fun generateStreaming(prompt: String): Flow<ModelResponse> = flow {
        if (style == ApiStyle.OPENAI_COMPATIBLE && apiKey.isNullOrBlank()) {
            emit(ModelResponse.Error("No API key configured for $name — add it in Settings"))
            return@flow
        }
        try {
            when (style) {
                ApiStyle.OLLAMA -> streamOllama(prompt)
                ApiStyle.OPENAI_COMPATIBLE -> streamOpenAI(prompt)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.w(TAG, "$name unreachable: ${e.message ?: "network error"}")
            emit(ModelResponse.Error("$name unreachable: ${e.message ?: "network error"}"))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ModelResponse>.streamOllama(prompt: String) {
        client.preparePost("$normalizedBase/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(OllamaRequest(model, prompt, stream = true))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val detail = StreamParsers.extractApiErrorMessage(response.bodyAsText())
                Log.w(TAG, "$name request failed: HTTP ${response.status.value}: $detail")
                emit(ModelResponse.Error("$name HTTP ${response.status.value}: $detail"))
                return@execute
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readLine() ?: break
                StreamParsers.parseOllamaLine(line)?.let { emit(it) }
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ModelResponse>.streamOpenAI(prompt: String) {
        client.preparePost("$normalizedBase/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            // OpenRouter attribution headers: harmless elsewhere, expected there.
            header("HTTP-Referer", "https://meowmba.local")
            header("X-Title", "MEOWMBA")
            contentType(ContentType.Application.Json)
            setBody(OpenAIRequest(model, listOf(OpenAIMessage("user", prompt)), stream = true))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val detail = StreamParsers.extractApiErrorMessage(response.bodyAsText())
                Log.w(TAG, "$name request failed: HTTP ${response.status.value} for model $model: $detail")
                emit(ModelResponse.Error("$name HTTP ${response.status.value}: $detail"))
                return@execute
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readLine() ?: break
                StreamParsers.parseOpenAISseLine(line)?.let { emit(it) }
            }
        }
    }

    override suspend fun generate(prompt: String): ModelResponse {
        val full = StringBuilder()
        var result: ModelResponse = ModelResponse.Error("No response")
        generateStreaming(prompt).collect { response ->
            when (response) {
                is ModelResponse.Partial -> full.append(response.text)
                is ModelResponse.Complete -> result = ModelResponse.Complete(full.toString())
                is ModelResponse.Error -> result = response
            }
        }
        return result
    }

    /** Use the model's real tool-calling protocol for controller decisions.
     * One proposed action is returned as JSON; this never executes a tool. */
    suspend fun generateToolDecision(prompt: String, schema: String): ModelResponse {
        if (style != ApiStyle.OPENAI_COMPATIBLE) return generate(prompt)
        if (apiKey.isNullOrBlank()) return ModelResponse.Error("No API key configured for $name — complete Your AI setup")
        return try {
            val response = client.post("$normalizedBase/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header("HTTP-Referer", "https://meowmba.local")
                header("X-Title", "MEOWMBA")
                contentType(ContentType.Application.Json)
                setBody(toolDecisionBody(model, prompt, schema).toString())
                timeout { requestTimeoutMillis = 60_000; socketTimeoutMillis = 60_000 }
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) ModelResponse.Error("$name HTTP ${response.status.value}: ${StreamParsers.extractApiErrorMessage(body)}")
            else parseToolDecision(body)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ModelResponse.Error("$name planning failed: ${e.message ?: "network error"}") }
    }

    override fun close() { client.close() }

    /**
     * Health check used by the Hardware page's "Test connection" button.
     * Returns a human-readable status; never throws.
     */
    suspend fun ping(): String = try {
        when (style) {
            ApiStyle.OLLAMA -> {
                val response = client.get("$normalizedBase/api/tags")
                if (response.status.isSuccess()) {
                    val count = StreamParsers.countOllamaModels(response.bodyAsText())
                    "Ollama reachable · $count model(s) installed"
                } else {
                    "Ollama HTTP ${response.status.value}"
                }
            }
            ApiStyle.OPENAI_COMPATIBLE -> {
                if (apiKey.isNullOrBlank()) {
                    "No API key configured — add it in Settings"
                } else {
                    val response = client.get("$normalizedBase/v1/models") {
                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                    }
                    if (response.status.isSuccess()) {
                        val count = StreamParsers.countOpenAIModels(response.bodyAsText())
                        "Connected · $count model(s) visible"
                    } else {
                        "HTTP ${response.status.value} — check API key"
                    }
                }
            }
        }
    } catch (e: Exception) {
        "Unreachable: ${e.message ?: "network error"}"
    }
    companion object {
        const val TAG = "RemoteModel"
        internal fun toolDecisionBody(model: String, prompt: String, schema: String): JsonObject = buildJsonObject {
            put("model", model); put("stream", false); put("max_tokens", 900)
            putJsonArray("messages") { add(buildJsonObject { put("role", "user"); put("content", prompt) }) }
            putJsonArray("tools") { add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") { put("name", "next_phone_action"); put("description", "Propose one next action; execution is handled by the phone after validation."); put("parameters", Json.parseToJsonElement(schema)) }
            }) }
            putJsonObject("tool_choice") { put("type", "function"); putJsonObject("function") { put("name", "next_phone_action") } }
            put("parallel_tool_calls", false)
        }
        internal fun parseToolDecision(body: String): ModelResponse = runCatching {
            val message = Json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject
            val calls = message["tool_calls"]?.jsonArray ?: error("Model did not return a tool action; choose a tool-capable model")
            check(calls.size == 1) { "Model returned multiple actions; nothing executed" }
            val function = calls.single().jsonObject["function"]!!.jsonObject
            check(function["name"]?.jsonPrimitive?.content == "next_phone_action") { "Unexpected tool name; nothing executed" }
            val args = function["arguments"]!!.jsonPrimitive.content
            check(args.length <= 16_000) { "Action was too large" }
            check(PhoneAgentLoop.parseAction(args) != null) { "Invalid phone action" }
            ModelResponse.Complete(args)
        }.getOrElse { ModelResponse.Error(it.message ?: "Invalid planning reply") }
    }
}

@Serializable
data class OllamaRequest(val model: String, val prompt: String, val stream: Boolean = true)

@Serializable
data class OpenAIRequest(val model: String, val messages: List<OpenAIMessage>, val stream: Boolean)

@Serializable
data class OpenAIMessage(val role: String, val content: String)
