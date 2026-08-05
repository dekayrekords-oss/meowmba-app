package com.example.meowmba.core.agent

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
) : ModelProvider {

    enum class ApiStyle { OLLAMA, OPENAI_COMPATIBLE }

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
        } catch (e: Exception) {
            emit(ModelResponse.Error("$name unreachable: ${e.message ?: "network error"}"))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ModelResponse>.streamOllama(prompt: String) {
        client.preparePost("$baseUrl/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(OllamaRequest(model, prompt, stream = true))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val detail = StreamParsers.extractApiErrorMessage(response.bodyAsText())
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
        client.preparePost("$baseUrl/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            // OpenRouter attribution headers: harmless elsewhere, expected there.
            header("HTTP-Referer", "https://meowmba.local")
            header("X-Title", "MEOWMBA")
            contentType(ContentType.Application.Json)
            setBody(OpenAIRequest(model, listOf(OpenAIMessage("user", prompt)), stream = true))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val detail = StreamParsers.extractApiErrorMessage(response.bodyAsText())
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

    /**
     * Health check used by the Hardware page's "Test connection" button.
     * Returns a human-readable status; never throws.
     */
    suspend fun ping(): String = try {
        when (style) {
            ApiStyle.OLLAMA -> {
                val response = client.get("$baseUrl/api/tags")
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
                    val response = client.get("$baseUrl/v1/models") {
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
}

@Serializable
data class OllamaRequest(val model: String, val prompt: String, val stream: Boolean = true)

@Serializable
data class OpenAIRequest(val model: String, val messages: List<OpenAIMessage>, val stream: Boolean)

@Serializable
data class OpenAIMessage(val role: String, val content: String)
