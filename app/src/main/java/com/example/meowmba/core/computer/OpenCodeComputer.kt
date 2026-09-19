package com.example.meowmba.core.computer

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Computer agent transport. Only loopback; no redirects, shell endpoints or blanket approvals. */
class OpenCodeComputer(private val password: String, private val address: String = OpenCodeLocalConnection.ADDRESS) {
    init { OpenCodeLocalConnection.requireLoopback(address) }
    companion object {
        const val SESSION_KEY = "opencode_computer_session"
        fun id(value: String): String {
            require(value.matches(Regex("[A-Za-z0-9_-]{1,160}"))) { "Invalid OpenCode identifier" }
            return value
        }
        fun permissionBody(allow: Boolean) = buildJsonObject { put("reply", if (allow) "once" else "reject") }
        fun artifactPath(value: String): String {
            require(value.isNotBlank() && value.length <= 500 && !value.startsWith('/') && '\\' !in value &&
                value.split('/').none { it == ".." || it == "." || it.isEmpty() } && value.none { it.isISOControl() }) { "Choose a relative file path inside this workspace." }
            return value
        }
        fun sessionBody(title: String) = buildJsonObject {
            put("title", title.take(100))
            putJsonArray("permission") { add(buildJsonObject {
                put("permission", "*"); put("pattern", "*"); put("action", "ask")
            }) }
        }
    }

    suspend fun create(title: String): String {
        OpenCodeLocalConnection.verify(password, address)
        // Require the approval-list endpoint before creating anything. Old servers
        // must be upgraded, never silently run without a permission UI.
        call("GET", "/permission").jsonArray
        val session = call("POST", "/session", sessionBody(title)).jsonObject
        val sessionId = id(session["id"]!!.jsonPrimitive.content)
        val rules = session["permission"] as? JsonArray
        check(rules?.lastOrNull()?.jsonObject?.let {
            it["permission"]?.jsonPrimitive?.content == "*" &&
                it["pattern"]?.jsonPrimitive?.content == "*" &&
                it["action"]?.jsonPrimitive?.content == "ask"
        } == true) { "OpenCode did not confirm permission gating. No task was sent. Update OpenCode." }
        return sessionId
    }

    suspend fun send(session: String, prompt: String) {
        require(prompt.isNotBlank() && prompt.length <= 16_000) { "Enter a task under 16,000 characters." }
        val rules = call("GET", "/session/${id(session)}").jsonObject["permission"] as? JsonArray
        check(rules?.lastOrNull()?.jsonObject?.let {
            it["permission"]?.jsonPrimitive?.content == "*" && it["pattern"]?.jsonPrimitive?.content == "*" && it["action"]?.jsonPrimitive?.content == "ask"
        } == true) { "Session permissions changed. Create a new task before continuing." }
        call("POST", "/session/${id(session)}/prompt_async", buildJsonObject {
            put("agent", "build")
            put("system", "You are Computer, the user's Meow workspace agent. Explain your plan, use the workspace for assets, and report actual file paths and failures. Never claim a task is done without results. Request permissions; do not bypass them.")
            putJsonArray("parts") { add(buildJsonObject { put("type", "text"); put("text", prompt) }) }
        })
    }

    suspend fun snapshot(session: String): Snapshot {
        val key = id(session)
        val permissions = call("GET", "/permission").jsonArray.map { it.jsonObject }
            .filter { it["sessionID"]?.jsonPrimitive?.content == key }
        val messages = call("GET", "/session/$key/message?limit=40").jsonArray
        val states = call("GET", "/session/status").jsonObject
        val todos = call("GET", "/session/$key/todo").jsonArray
        val detail = call("GET", "/session/$key").jsonObject
        val questions = call("GET", "/question").jsonArray.map { it.jsonObject }
            .filter { it["sessionID"]?.jsonPrimitive?.content == key }
        val active = states[key]?.jsonObject?.get("type")?.jsonPrimitive?.content ?: "idle"
        return Snapshot(active, messages, permissions, todos, detail["directory"]?.jsonPrimitive?.content.orEmpty(), questions)
    }

    suspend fun approve(session: String, request: String, allow: Boolean) {
        // Re-read immediately to reject stale or cross-session approval cards.
        val pending = call("GET", "/permission").jsonArray.any {
            it.jsonObject["id"]?.jsonPrimitive?.content == request &&
                it.jsonObject["sessionID"]?.jsonPrimitive?.content == session
        }
        check(pending) { "This approval is no longer pending. Refresh the task." }
        call("POST", "/permission/${id(request)}/reply", permissionBody(allow))
    }

    suspend fun stop(session: String) { call("POST", "/session/${id(session)}/abort", buildJsonObject {}) }

    suspend fun artifact(session: String, path: String): ByteArray {
        val relative = artifactPath(path)
        val detail = call("GET", "/session/${id(session)}").jsonObject
        val directory = detail["directory"]!!.jsonPrimitive.content
        fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
        val file = call("GET", "/file/content?path=${encode(relative)}&directory=${encode(directory)}").jsonObject
        val content = file["content"]!!.jsonPrimitive.content
        val data = if (file["encoding"]?.jsonPrimitive?.content == "base64") Base64.getDecoder().decode(content)
            else {
                check(file["type"]?.jsonPrimitive?.content == "text") { "OpenCode cannot export this binary file." }
                content.toByteArray(Charsets.UTF_8)
            }
        check(data.isNotEmpty()) { "The result file is empty." }
        return data
    }

    suspend fun answer(session: String, request: String, answers: List<String>) {
        val question = call("GET", "/question").jsonArray.map { it.jsonObject }.firstOrNull {
            it["id"]?.jsonPrimitive?.content == request && it["sessionID"]?.jsonPrimitive?.content == session
        }
        check(question != null && question["questions"]!!.jsonArray.size == answers.size && answers.all { it.isNotBlank() }) { "Question changed. Refresh and answer every question." }
        call("POST", "/question/${id(request)}/reply", buildJsonObject {
            putJsonArray("answers") { answers.forEach { value -> add(buildJsonArray { add(value.take(4_000)) }) } }
        })
    }

    data class Snapshot(val status: String, val messages: JsonArray, val permissions: List<JsonObject>, val todos: JsonArray, val directory: String, val questions: List<JsonObject> = emptyList()) {
        fun resultPaths(): List<String> = messages.flatMap { message ->
            (message.jsonObject["parts"] as? JsonArray).orEmpty().mapNotNull { part ->
                val p = part.jsonObject
                val state = p["state"] as? JsonObject
                if (p["type"]?.jsonPrimitive?.content != "tool" || p["tool"]?.jsonPrimitive?.content !in setOf("write", "edit") ||
                    state?.get("status")?.jsonPrimitive?.content != "completed") return@mapNotNull null
                val raw = (state["input"] as? JsonObject)?.get("filePath")?.jsonPrimitive?.content ?: return@mapNotNull null
                val relative = raw.removePrefix(directory.trimEnd('/') + "/")
                runCatching { artifactPath(relative) }.getOrNull()
            }
        }.distinct().take(30)

        fun transcript(): String = messages.joinToString("\n\n") { message ->
            val obj = message.jsonObject
            val info = obj["info"]!!.jsonObject
            val text = (obj["parts"] as? JsonArray).orEmpty().joinToString("\n") { part ->
                val p = part.jsonObject
                when (p["type"]?.jsonPrimitive?.content) {
                    "text" -> p["text"]?.jsonPrimitive?.content.orEmpty()
                    "tool" -> "Tool: ${p["tool"]?.jsonPrimitive?.content} · ${p["state"]?.jsonObject?.get("status")?.jsonPrimitive?.content}"
                    else -> ""
                }
            }
            val error = info["error"]?.takeUnless { it is JsonNull }?.toString()?.take(600)
            "${info["role"]?.jsonPrimitive?.content}: $text${error?.let { "\nError: $it" }.orEmpty()}"
        }.takeLast(24_000)
    }

    private suspend fun call(method: String, path: String, body: JsonObject? = null): JsonElement = withContext(Dispatchers.IO) {
        require(password.isNotBlank()) { "Finish Setup computer first." }
        val connection = URL(address + path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000; connection.readTimeout = 15_000
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString("opencode:$password".toByteArray(Charsets.UTF_8)))
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            check(status in 200..299) { if (status == 401) "Computer rejected the saved password. Run Setup computer again." else "OpenCode returned HTTP $status. Task outcome is unconfirmed; refresh before retrying." }
            if (status == 204) return@withContext JsonNull
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = input.read(chunk); if (n < 0) break
                    check(out.size() + n <= 2_000_000) { "Computer response exceeded the safe display limit." }
                    out.write(chunk, 0, n)
                }
                out.toByteArray()
            }
            if (bytes.isEmpty()) JsonNull else Json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
