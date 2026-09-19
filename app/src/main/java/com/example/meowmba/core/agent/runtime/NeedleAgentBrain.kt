package com.example.meowmba.core.agent.runtime

import com.example.meowmba.core.agent.tools.ToolDefinition
import com.example.meowmba.core.agent.tools.ToolRegistry
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal interface NeedleNative {
    fun load(bytes: ByteArray): Int
    fun initialize(systemPrompt: String, toolsJson: String): Int
    fun complete(input: String, maxTokens: Int): String?
    fun reset()
}

internal object NeedleJniNative : NeedleNative {
    init { System.loadLibrary("needle_jni_bridge") }
    external override fun load(bytes: ByteArray): Int
    external override fun initialize(systemPrompt: String, toolsJson: String): Int
    external override fun complete(input: String, maxTokens: Int): String?
    external override fun reset()
}

/** Tiny on-device default that proposes tool calls; policy remains in the orchestrator. */
class NeedleAgentBrain internal constructor(
    private val modelFile: File,
    private val native: NeedleNative = NeedleJniNative,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AgentBrainRouter {
    private val ownerToken = Any()
    private var schemaFingerprint: Int? = null

    override suspend fun route(text: String, registry: ToolRegistry): AgentRouter.AgentIntent? =
        withContext(dispatcher) {
            if (!CommandHeuristics.mightBeCommand(text)) return@withContext null
            lock.withLock {
                val definitions = registry.definitions()
                val schema = toolsJson(definitions)
                if (activeOwner !== ownerToken || schemaFingerprint != schema.hashCode()) {
                    activeOwner = null
                    if (!modelFile.isFile || native.load(modelFile.readBytes()) != 0) return@withLock null
                    // Needle returns a non-negative cached-prefix token count
                    // on success; positive is not an error.
                    if (native.initialize(SYSTEM_PROMPT, schema) < 0) return@withLock null
                    schemaFingerprint = schema.hashCode()
                    activeOwner = ownerToken
                }
                parse(native.complete(text, 256), registry)
            }
        }

    companion object {
        // The vendor API owns one process-global, non-thread-safe model. A
        // ViewModel recreation or model update must not race another instance.
        private val lock = Mutex()
        private var activeOwner: Any? = null
        private const val SYSTEM_PROMPT =
            "Choose a tool only when the user clearly requests an action. Refuse ordinary conversation."

        internal fun toolsJson(definitions: List<ToolDefinition>): String = buildJsonArray {
            definitions.forEach { tool ->
                add(buildJsonObject {
                    put("name", tool.id)
                    put("description", tool.description)
                    put("parameters", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            tool.inputSchema.forEach { (name, type) ->
                                put(name, buildJsonObject { put("type", if (type == "int") "integer" else "string") })
                            }
                        })
                    })
                })
            }
        }.toString()

        internal fun parse(raw: String?, registry: ToolRegistry): AgentRouter.AgentIntent? {
            val root = runCatching { Json.parseToJsonElement(raw ?: return null).jsonObject }.getOrNull()
                ?: return null
            if (runCatching { root["type"]?.jsonPrimitive?.contentOrNull }.getOrNull() != "call") return null
            val call = runCatching { root["function_calls"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull()
                ?: return null
            val id = runCatching { call["name"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: return null
            if (registry.byId(id) == null) return null
            val args = runCatching { call["arguments"]?.jsonObject }.getOrNull()
                ?.mapValues { (_, value) ->
                    (value as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: value.toString()
                } ?: emptyMap()
            val owner = AgentRegistry.ownerOf(id) ?: return null
            return AgentRouter.AgentIntent(owner.id, id, args, registry.byId(id)!!.definition.displayName)
        }
    }
}
