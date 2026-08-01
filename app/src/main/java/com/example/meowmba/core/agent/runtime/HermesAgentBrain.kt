package com.example.meowmba.core.agent.runtime

import com.example.meowmba.core.agent.tools.ToolDefinition
import com.example.meowmba.core.agent.tools.ToolRegistry
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Pure protocol helpers for the Hermes Agent integration. Hermes Agent
 * (v0.19.x in the Memba stack) speaks OpenAI-compatible
 * /v1/chat/completions with native function-calling `tools` — these helpers
 * convert our tool definitions and parse its responses without any network.
 */
object HermesProtocol {

    private val json = Json { ignoreUnknownKeys = true }

    /** Convert registry definitions to OpenAI function-calling format. */
    fun toOpenAiTools(definitions: List<ToolDefinition>): JsonArray = buildJsonArray {
        definitions.forEach { tool ->
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", tool.id)
                    put("description", tool.description)
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            tool.inputSchema.forEach { (argName, argType) ->
                                putJsonObject(argName) {
                                    put("type", if (argType == "int") "integer" else "string")
                                }
                            }
                        }
                        putJsonArray("required") {
                            tool.inputSchema.keys.forEach { add(JsonPrimitive(it)) }
                        }
                    }
                }
            })
        }
    }

    /** Request body for one routing decision. */
    fun buildRequestBody(model: String, userText: String, tools: JsonArray): JsonObject =
        buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", "You are the Meowmba agent router. Decide which single " +
                        "tool serves the user's request and call it. If no tool fits, " +
                        "answer normally without calling a tool.")
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userText)
                })
            }
            put("tools", tools)
            put("tool_choice", "auto")
            put("stream", false)
        }

    /**
     * Parse a Hermes/OpenAI chat-completions response body.
     * Structured `tool_calls` win; otherwise fall back to defensive text
     * parsing (models occasionally speak raw JSON as content — the exact
     * failure mode the Memba knowledge base warns about).
     * Unknown tools and garbage yield null — never a guess.
     */
    fun parseResponse(body: String, registry: ToolRegistry): ToolCallParser.ParsedToolCall? {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return null
        }
        val message = root["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject ?: return null

        // 1. Structured tool_calls (preferred)
        val toolCalls = message["tool_calls"]?.jsonArray
        val firstCall = toolCalls?.firstOrNull()?.jsonObject
        if (firstCall != null) {
            val function = firstCall["function"]?.jsonObject ?: return null
            val name = function["name"]?.jsonPrimitive?.content ?: return null
            if (registry.byId(name) == null) return null
            val args = function["arguments"]?.jsonPrimitive?.content.orEmpty()
            val parsedArgs = try {
                json.parseToJsonElement(args).jsonObject.entries
                    .associate { (k, v) ->
                        k to when (v) {
                            // Structured values (arrays/objects) ride as JSON text —
                            // avatar.animate's "frames" arrives exactly this way.
                            is JsonPrimitive -> v.content
                            else -> v.toString()
                        }
                    }
            } catch (e: Exception) {
                emptyMap()
            }
            return ToolCallParser.ParsedToolCall(name, parsedArgs)
        }

        // 2. Content fallback: same defensive path as the on-device parser
        val content = message["content"]?.jsonPrimitive?.content.orEmpty()
        if (content.isBlank()) return null
        return ToolCallParser.parse(content, registry)
    }
}

/** Connection details for a Hermes-compatible agent endpoint. */
data class HermesConfig(
    val baseUrl: String,
    val model: String = "hermes-agent",
    val apiKey: String? = null
)

/**
 * Agent brain backed by a Hermes Agent endpoint (OpenAI-compatible
 * function-calling). The endpoint only PROPOSES tool calls — the
 * orchestrator's permission checks and the user's approval card still
 * decide what actually executes.
 *
 * Honest failures: unreachable, non-200, malformed, or no-tool responses all
 * return null so the caller falls back (on-device brain, then conversation).
 */
class HermesAgentBrain(
    private val config: HermesConfig,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AgentBrainRouter {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    /**
     * Candidate endpoint paths. The OpenAI SDK (used by the Memba server
     * stack) posts to `base_url + /chat/completions`, while most proxies
     * serve `/v1/chat/completions` — try the configured shape first, then
     * the alternate, so either deployment works without configuration.
     */
    internal fun endpointCandidates(): List<String> {
        val base = config.baseUrl.trimEnd('/')
        return if (base.endsWith("/v1")) {
            listOf("$base/chat/completions")
        } else {
            listOf("$base/v1/chat/completions", "$base/chat/completions")
        }
    }

    override suspend fun route(text: String, registry: ToolRegistry): AgentRouter.AgentIntent? {
        val body = try {
            withContext(dispatcher) {
                var responseBody: String? = null
                for (endpoint in endpointCandidates()) {
                    val response = client.post(endpoint) {
                        config.apiKey?.takeIf { it.isNotBlank() }?.let {
                            header(HttpHeaders.Authorization, "Bearer $it")
                        }
                        contentType(ContentType.Application.Json)
                        setBody(
                            HermesProtocol.buildRequestBody(
                                config.model, text, HermesProtocol.toOpenAiTools(registry.definitions())
                            ).toString()
                        )
                    }
                    if (response.status.isSuccess()) {
                        responseBody = response.bodyAsText()
                        break
                    }
                    // 404 = wrong path shape for this deployment: try the next one
                    if (response.status.value != 404) break
                }
                responseBody
            }
        } catch (e: Exception) {
            null
        } ?: return null

        val call = HermesProtocol.parseResponse(body, registry) ?: return null
        val owner = AgentRegistry.ownerOf(call.toolId) ?: return null

        return AgentRouter.AgentIntent(
            agentId = owner.id,
            toolId = call.toolId,
            args = call.args,
            title = buildTitle(call, registry)
        )
    }

    private fun buildTitle(call: ToolCallParser.ParsedToolCall, registry: ToolRegistry): String {
        val tool = registry.byId(call.toolId)
        val keyArg = call.args.values.firstOrNull()?.take(40)
        return listOfNotNull(tool?.definition?.displayName, keyArg).joinToString(": ")
            .ifBlank { call.toolId }
    }
}
