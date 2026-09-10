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
    },
    clientOverride: HttpClient? = null,
) : ModelProvider, java.io.Closeable {

    enum class ApiStyle { OLLAMA, OPENAI_COMPATIBLE, ANTHROPIC }

    /**
     * Defense in depth: if a base url is ever stored with a trailing "/v1"
     * (e.g. "https://openrouter.ai/api/v1"), appending "/v1/chat/completions"
     * would hit "/v1/v1/chat/completions" — a guaranteed 404, which is exactly
     * what shipped to the field. Stripping the suffix here makes the provider
     * immune to that. Ollama-style base urls (no /v1) pass through unchanged.
     */
    private val normalizedBase: String = baseUrl.trimEnd('/').removeSuffix("/v1")

    private val client = clientOverride ?: HttpClient {
        followRedirects = false
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
        if (style != ApiStyle.OLLAMA && apiKey.isNullOrBlank()) {
            emit(ModelResponse.Error("No API key configured for $name — add it in Settings"))
            return@flow
        }
        try {
            when (style) {
                ApiStyle.OLLAMA -> streamOllama(prompt)
                ApiStyle.OPENAI_COMPATIBLE -> streamOpenAI(prompt)
                ApiStyle.ANTHROPIC -> streamAnthropic(prompt)
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

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ModelResponse>.streamAnthropic(prompt: String) {
        client.preparePost("$normalizedBase/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("model", model); put("max_tokens", 4096); put("stream", true)
                putJsonArray("messages") { add(buildJsonObject { put("role", "user"); put("content", prompt) }) }
            }.toString())
        }.execute { response ->
            if (!response.status.isSuccess()) {
                emit(ModelResponse.Error("$name HTTP ${response.status.value}. Check API key, model and account access."))
                return@execute
            }
            val channel = response.bodyAsChannel()
            var hasText = false
            while (!channel.isClosedForRead) {
                val line = channel.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val event = Json.parseToJsonElement(line.removePrefix("data:").trim()).jsonObject
                when (event["type"]?.jsonPrimitive?.content) {
                    "content_block_delta" -> {
                        val delta = event["delta"]?.jsonObject
                        if (delta?.get("type")?.jsonPrimitive?.content == "text_delta") {
                            val text = delta["text"]?.jsonPrimitive?.content.orEmpty()
                            hasText = hasText || text.isNotBlank()
                            if (text.isNotEmpty()) emit(ModelResponse.Partial(text))
                        }
                    }
                    "error" -> { emit(ModelResponse.Error("$name streaming error. Please retry.")); return@execute }
                    "message_delta" -> if (event["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.content == "max_tokens") {
                        emit(ModelResponse.Error("$name response reached its output limit. Ask to continue.")); return@execute
                    }
                    "message_stop" -> {
                        emit(if (hasText) ModelResponse.Complete("") else ModelResponse.Error("$name returned no text."))
                        return@execute
                    }
                }
            }
            emit(ModelResponse.Error("$name connection ended before the reply completed."))
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
    suspend fun generateToolDecision(prompt: String, schema: String,
                                    validate: (String) -> Boolean = { PhoneAgentLoop.parseAction(it) != null }): ModelResponse {
        if (style == ApiStyle.ANTHROPIC) return anthropicDecision(prompt, schema, validate)
        if (style != ApiStyle.OPENAI_COMPATIBLE) return generate(prompt)
        if (apiKey.isNullOrBlank()) return ModelResponse.Error("No API key configured for $name — complete Your AI setup")
        return try {
            var instruction=prompt
            var parsed: ModelResponse = ModelResponse.Error("No controller decision")
            repeat(2) {
                val response = client.post("$normalizedBase/v1/chat/completions") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    header("HTTP-Referer", "https://meowmba.local")
                    header("X-Title", "MEOWMBA")
                    contentType(ContentType.Application.Json)
                    setBody(toolDecisionBody(model, instruction, schema).toString())
                    timeout { requestTimeoutMillis = 60_000; socketTimeoutMillis = 60_000 }
                }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) return ModelResponse.Error("$name HTTP ${response.status.value}: ${StreamParsers.extractApiErrorMessage(body)}")
                parsed = parseToolDecision(body, validate)
                if (parsed is ModelResponse.Complete) return parsed
                // No controller action has run. Repair a malformed proposal
                // once; never replay an executed tool or retry a network error.
                instruction=prompt + "\nYour previous proposal was rejected: " +
                    (parsed as ModelResponse.Error).message.take(200) +
                    ". No action was executed. Return EXACTLY ONE next_phone_action function call matching the supplied schema. Do not batch steps or return prose."
            }
            parsed
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ModelResponse.Error("$name planning failed: ${e.message ?: "network error"}") }
    }

    private suspend fun anthropicDecision(prompt: String, schema: String, validate: (String) -> Boolean): ModelResponse {
        if (apiKey.isNullOrBlank()) return ModelResponse.Error("No API key configured for $name")
        return try {
            val response = client.post("$normalizedBase/v1/messages") {
                header("x-api-key", apiKey); header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                timeout { requestTimeoutMillis = 60_000; socketTimeoutMillis = 60_000 }
                setBody(buildJsonObject {
                    put("model", model); put("max_tokens", 2048)
                    putJsonArray("messages") { add(buildJsonObject {
                        put("role", "user"); put("content", prompt + "\nPropose exactly one next_phone_action tool call. Execution and approval are handled by the phone.")
                    }) }
                    putJsonArray("tools") { add(buildJsonObject {
                        put("name", "next_phone_action"); put("description", "Propose one validated controller action; never execute it.")
                        put("input_schema", Json.parseToJsonElement(schema))
                    }) }
                    putJsonObject("tool_choice") { put("type", "auto"); put("disable_parallel_tool_use", true) }
                }.toString())
            }
            if (!response.status.isSuccess()) ModelResponse.Error("$name HTTP ${response.status.value}. Check key, model and account access.")
            else parseAnthropicDecision(response.bodyAsText(), validate)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ModelResponse.Error("$name controller request failed. No action executed.") }
    }

    override fun close() { client.close() }

    /**
     * Health check used by the LLM page's "Test connection" button.
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
            ApiStyle.OPENAI_COMPATIBLE, ApiStyle.ANTHROPIC -> {
                if (apiKey.isNullOrBlank()) {
                    "No API key configured — add it in Settings"
                } else {
                    val response = client.get("$normalizedBase/v1/models") {
                        if (style == ApiStyle.ANTHROPIC) {
                            header("x-api-key", apiKey); header("anthropic-version", "2023-06-01")
                        } else header(HttpHeaders.Authorization, "Bearer $apiKey")
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
        internal fun parseAnthropicDecision(body: String, validate: (String) -> Boolean): ModelResponse = runCatching {
            val obj = Json.parseToJsonElement(body).jsonObject
            check(obj["stop_reason"]?.jsonPrimitive?.content == "tool_use") { "No completed controller tool call" }
            val calls = obj["content"]!!.jsonArray.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "tool_use" }
            check(calls.size == 1) { "Expected exactly one controller action" }
            val call = calls.single().jsonObject
            check(call["name"]?.jsonPrimitive?.content == "next_phone_action") { "Unexpected controller tool" }
            val input = call["input"] as? JsonObject ?: error("Invalid controller input")
            val args = input.toString()
            check(args.length <= 16_000 && validate(args)) { "Invalid controller action" }
            ModelResponse.Complete(args)
        }.getOrElse { ModelResponse.Error("${it.message}. No action executed.") }

        internal fun toolDecisionBody(model: String, prompt: String, schema: String): JsonObject = buildJsonObject {
            put("model", model); put("stream", false); put("max_tokens", 900)
            putJsonArray("messages") { add(buildJsonObject { put("role", "user"); put("content", prompt) }) }
            putJsonArray("tools") { add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") { put("name", "next_phone_action"); put("description", "Propose one controller action from this schema; execution is handled by the phone after validation."); put("parameters", Json.parseToJsonElement(schema)) }
            }) }
            putJsonObject("tool_choice") { put("type", "function"); putJsonObject("function") { put("name", "next_phone_action") } }
            put("parallel_tool_calls", false)
        }
        internal fun parseToolDecision(body: String, validate: (String) -> Boolean = { PhoneAgentLoop.parseAction(it) != null }): ModelResponse = runCatching {
            val message = Json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject
            val rawCalls=message["tool_calls"]
            check(rawCalls==null || rawCalls==JsonNull || rawCalls is JsonArray) { "Invalid tool-call envelope" }
            val calls = rawCalls as? JsonArray
            if (calls.isNullOrEmpty()) {
                // Some compatible providers ignore forced tool choice and
                // return the requested JSON in content. Accept one exact,
                // validated proposal; never infer an action from prose.
                val content=message["content"]?.jsonPrimitive?.content ?: error("Model did not return an action")
                check(content.length<=16_000) { "Action was too large" }
                val clean=content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val proposal=Json.parseToJsonElement(clean) as? JsonObject ?: error("Expected one controller action object")
                val args=proposal.toString()
                check(validate(args)) { "Invalid controller action" }
                return@runCatching ModelResponse.Complete(args)
            }
            check(calls.size == 1) { "Model returned multiple actions; nothing executed" }
            val function = calls.single().jsonObject["function"]!!.jsonObject
            check(function["name"]?.jsonPrimitive?.content == "next_phone_action") { "Unexpected tool name; nothing executed" }
            val args = function["arguments"]!!.jsonPrimitive.content
            check(args.length <= 16_000) { "Action was too large" }
            check(validate(args)) { "Invalid controller action" }
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
