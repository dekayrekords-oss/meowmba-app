package com.example.meowmba.core.vision

import com.example.meowmba.core.bridge.DesktopBridgeProtocol
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Vision (image-understanding) providers.
 *
 * Honesty rules inherited from the chat path: HTTP status checked before
 * parsing, missing API key fails fast, empty answers are errors, timeouts
 * are short on connect and generous on inference. JPEG bytes in, plain
 * description out — no streaming for v1 (a sentence or two).
 */
interface VisionProvider {
    val name: String
    suspend fun describe(jpegBytes: ByteArray, prompt: String): Result<String>
}

private val visionClient = HttpClient {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 180_000
        socketTimeoutMillis = 180_000
    }
}

// Hand-rolled base64: java.util.Base64 needs API 26, minSdk is 24.
private fun b64(bytes: ByteArray): String = DesktopBridgeProtocol.b64EncodeStd(bytes)

private fun unreachable(name: String, e: Exception) =
    Exception("$name unreachable: ${e.message ?: "network error"}")

/** Ollama /api/generate with an image attachment (Gemma-class multimodal). */
class OllamaVisionProvider(
    private val baseUrl: String,
    private val model: String
) : VisionProvider {
    override val name = "Ollama vision"

    override suspend fun describe(jpegBytes: ByteArray, prompt: String): Result<String> {
        return try {
            val body = buildJsonObject {
                put("model", model)
                put("prompt", prompt)
                put("stream", false)
                put("images", buildJsonArray { add(JsonPrimitive(b64(jpegBytes))) })
            }.toString()
            val response = visionClient.post("$baseUrl/api/generate") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("Ollama HTTP ${response.status.value}: ${response.bodyAsText().take(160)}"))
            }
            val text = Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["response"]?.jsonPrimitive?.content?.trim()
            if (text.isNullOrBlank()) {
                Result.failure(Exception("Ollama returned an empty description"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(unreachable(name, e))
        }
    }
}

/** OpenAI-compatible /v1/chat/completions with a base64 image_url. */
class OpenAIVisionProvider(
    private val baseUrl: String,
    private val apiKey: String?,
    private val model: String
) : VisionProvider {
    override val name = "OpenAI vision"

    override suspend fun describe(jpegBytes: ByteArray, prompt: String): Result<String> {
        if (apiKey.isNullOrBlank()) {
            return Result.failure(Exception("No API key configured for OpenAI — add it in Settings"))
        }
        return try {
            val body = buildJsonObject {
                put("model", model)
                put("max_tokens", 300)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", prompt)
                            })
                            add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject {
                                    put("url", "data:image/jpeg;base64,${b64(jpegBytes)}")
                                })
                            })
                        })
                    })
                })
            }.toString()
            val response = visionClient.post("$baseUrl/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("OpenAI HTTP ${response.status.value}: ${response.bodyAsText().take(160)}"))
            }
            val text = Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content?.trim()
            if (text.isNullOrBlank()) {
                Result.failure(Exception("OpenAI returned an empty description"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(unreachable(name, e))
        }
    }
}
