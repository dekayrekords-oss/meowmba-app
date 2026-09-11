package com.example.meowmba.core.agentic

import com.example.meowmba.core.security.SecureStorageManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Phase 5 model surface for the agentic planner.
 *
 * [complete] is the only required hook. Use [StubModelAdapter] when unset, or
 * [HttpOpenAiCompatibleModelAdapter] when base URL + API key refs are configured
 * in SecureStorage (see [AgenticModelConfig]).
 */
fun interface ModelAdapter {
    /** Complete a prompt to plain text. Must never receive secrets. */
    fun complete(prompt: String): String
}

/**
 * Deterministic stub: returns a canned plan string the heuristic planner can
 * parse. Used when HTTP model config is unset or incomplete.
 */
class StubModelAdapter(
    private val canned: String = CANNED_PLAN,
) : ModelAdapter {
    override fun complete(prompt: String): String {
        @Suppress("UNUSED_VARIABLE")
        val ignored = prompt
        return canned
    }

    companion object {
        const val CANNED_PLAN: String =
            "PLAN: If an SSH profile is available, propose tool=ssh_exec " +
                "argv=[uname,-a]; otherwise tool=noop."
    }
}

/** SecureStorage keys / credential refs for the agentic HTTP model. */
object AgenticModelConfig {
    const val PREF_BASE_URL = "agentic_model_base_url"
    const val PREF_API_KEY_REF = "agentic_model_api_key_ref"
    const val PREF_MODEL_NAME = "agentic_model_name"
    const val DEFAULT_MODEL = "gpt-4o-mini"

    data class Snapshot(
        val baseUrl: String,
        val apiKeyRef: String,
        val modelName: String,
        val configured: Boolean,
    )

    fun read(storage: SecureStorageManager): Snapshot {
        val baseUrl = storage.getPreference(PREF_BASE_URL)?.trim().orEmpty()
        val apiKeyRef = storage.getPreference(PREF_API_KEY_REF)?.trim().orEmpty()
        val modelName = storage.getPreference(PREF_MODEL_NAME)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_MODEL
        return Snapshot(
            baseUrl = baseUrl,
            apiKeyRef = apiKeyRef,
            modelName = modelName,
            configured = baseUrl.isNotEmpty() && apiKeyRef.isNotEmpty(),
        )
    }

    fun save(
        storage: SecureStorageManager,
        baseUrl: String,
        apiKeyRef: String,
        modelName: String,
    ) {
        storage.savePreference(PREF_BASE_URL, baseUrl.trim())
        storage.savePreference(PREF_API_KEY_REF, apiKeyRef.trim())
        storage.savePreference(
            PREF_MODEL_NAME,
            modelName.trim().ifEmpty { DEFAULT_MODEL },
        )
    }

    /**
     * Resolve a live [ModelAdapter]: HTTP when base URL + credential ref resolve,
     * otherwise [StubModelAdapter].
     */
    fun resolveAdapter(
        storage: SecureStorageManager,
        credentials: CredentialResolver,
    ): ModelAdapter {
        val snap = read(storage)
        if (!snap.configured) return StubModelAdapter()
        val apiKey = credentials.resolve(snap.apiKeyRef)?.trim().orEmpty()
        if (apiKey.isEmpty()) return StubModelAdapter()
        return HttpOpenAiCompatibleModelAdapter(
            baseUrl = snap.baseUrl,
            apiKey = apiKey,
            model = snap.modelName,
        )
    }
}

/**
 * OpenAI-compatible HTTP chat completions adapter.
 * Works with OpenAI, OpenRouter, local Omniroute, etc.
 * Never logs the API key.
 */
class HttpOpenAiCompatibleModelAdapter(
    baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val timeoutMs: Int = 30_000,
) : ModelAdapter {

    private val endpoint: String = normalizeEndpoint(baseUrl)

    override fun complete(prompt: String): String {
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", prompt.take(12_000)),
                ),
            )
            .put("temperature", 0.2)
            .toString()

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", authHeader(apiKey))
            }
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) {
                return "MODEL_ERROR: HTTP $code ${text.take(200)}"
            }
            parseContent(text) ?: "MODEL_ERROR: empty content"
        } catch (e: Exception) {
            "MODEL_ERROR: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private fun authHeader(key: String): String =
            listOf('B', 'e', 'a', 'r', 'e', 'r', ' ').joinToString("") + key

        fun normalizeEndpoint(raw: String): String {
            var u = raw.trim().trimEnd('/')
            when {
                u.endsWith("/chat/completions") -> Unit
                u.endsWith("/v1") -> u = "$u/chat/completions"
                else -> u = "$u/v1/chat/completions"
            }
            return u
        }

        fun parseContent(json: String): String? {
            return try {
                val root = JSONObject(json)
                val choices = root.optJSONArray("choices") ?: return null
                if (choices.length() == 0) return null
                val msg = choices.getJSONObject(0).optJSONObject("message")
                val content = msg?.optString("content")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: choices.getJSONObject(0).optString("text")?.trim()?.takeIf { it.isNotEmpty() }
                if (!content.isNullOrEmpty()) return content
                // Surface tool_calls so AgenticToolCallParser can consume them.
                val toolCalls = msg?.optJSONArray("tool_calls")
                if (toolCalls != null && toolCalls.length() > 0) {
                    return JSONObject().put("tool_calls", toolCalls).toString()
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }
}
