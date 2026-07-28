package com.example.meowmba.core.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure parsers for streaming model APIs. No network, no Android — every line
 * format is unit-testable on the JVM.
 *
 * Ollama streams newline-delimited JSON: {"response":"…","done":false}
 * OpenAI-compatible APIs stream Server-Sent Events: data: {"choices":[…]} …
 */
object StreamParsers {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parse one NDJSON line from Ollama's /api/generate stream.
     * Returns null for blank or malformed lines (keepalive noise).
     */
    fun parseOllamaLine(line: String): ModelResponse? {
        if (line.isBlank()) return null
        return try {
            val obj = json.parseToJsonElement(line).jsonObject
            val partial = obj["response"]?.jsonPrimitive?.content.orEmpty()
            val done = obj["done"]?.jsonPrimitive?.booleanOrNull ?: false
            when {
                done -> ModelResponse.Complete(partial)
                partial.isNotEmpty() -> ModelResponse.Partial(partial)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse one SSE line from an OpenAI-compatible /v1/chat/completions stream.
     * Returns null for non-data lines (comments, blanks, malformed chunks).
     */
    fun parseOpenAISseLine(line: String): ModelResponse? {
        if (!line.startsWith("data: ")) return null
        val data = line.removePrefix("data: ").trim()
        if (data == "[DONE]") return ModelResponse.Complete("")
        return try {
            val obj = json.parseToJsonElement(data).jsonObject
            val content = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("delta")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content.orEmpty()
            if (content.isNotEmpty()) ModelResponse.Partial(content) else null
        } catch (e: Exception) {
            null
        }
    }

    /** Count models in an Ollama /api/tags response: {"models":[…]}. */
    fun countOllamaModels(body: String): Int = countArrayEntries(body, "models")

    /** Count models in an OpenAI /v1/models response: {"data":[…]}. */
    fun countOpenAIModels(body: String): Int = countArrayEntries(body, "data")

    private fun countArrayEntries(body: String, key: String): Int = try {
        json.parseToJsonElement(body).jsonObject[key]?.jsonArray?.size ?: 0
    } catch (e: Exception) {
        0
    }
}
