package com.example.meowmba.core.workflows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/** Streamable-HTTP MCP with authenticated, session-aware requests. No automatic
 * replay of tools/call, redirects, stdio processes, or server-requested sampling.
 * Authentication is supplied by the user, never by model-generated arguments. */
class WorkflowMcpClient(private val endpoint: String, private val token: String = "") {
    private val mutex = Mutex()
    private var session: String? = null
    private var protocol = "2025-11-25"
    private var ready = false
    private var nextId = 1
    init { validateEndpoint(endpoint, allowLocalHttp = true); require(!token.contains('\r') && !token.contains('\n')) }

    suspend fun listTools(): List<BusinessMcpTool> = mutex.withLock {
        initialize()
        val tools = mutableListOf<BusinessMcpTool>()
        val seen = mutableSetOf<String>()
        var cursor = ""
        do {
            val result = request("tools/list", JSONObject().apply { if (cursor.isNotBlank()) put("cursor", cursor) })
            val items = result.getJSONArray("tools")
            repeat(items.length()) { index ->
                val item = items.getJSONObject(index)
                val name = item.getString("name")
                require(name.matches(Regex("[a-zA-Z0-9_.-]{1,128}"))) { "Unsupported MCP tool name" }
                val schema = item.getJSONObject("inputSchema").toString()
                require(schema.length <= 12_000) { "MCP tool schema too large" }
                require(tools.none { it.name == name }) { "Duplicate MCP tool name" }
                tools += BusinessMcpTool(name, item.optString("description").take(2000), schema,
                    item.optJSONObject("annotations")?.optBoolean("readOnlyHint", false) == true)
            }
            require(tools.size <= 200) { "This endpoint exposes more than 200 tools; use a narrower server" }
            cursor = result.optString("nextCursor")
            require(cursor.isBlank() || seen.add(cursor)) { "MCP pagination repeated a cursor" }
        } while (cursor.isNotBlank())
        tools
    }

    suspend fun call(name: String, arguments: JSONObject): String = mutex.withLock {
        initialize()
        val result = request("tools/call", JSONObject().put("name", name).put("arguments", arguments))
        check(!result.optBoolean("isError")) { "MCP tool reported a failure: ${render(result).take(2000)}" }
        render(result).also { check(it.isNotBlank()) { "MCP returned no result evidence" } }
    }

    private suspend fun initialize() {
        if (ready) return
        val result = request("initialize", JSONObject().put("protocolVersion", protocol)
            .put("capabilities", JSONObject()).put("clientInfo", JSONObject().put("name", "Meowmba Business Agents").put("version", "1.0")))
        val negotiated = result.getString("protocolVersion")
        require(negotiated in setOf("2025-11-25", "2025-06-18", "2025-03-26")) { "Unsupported MCP protocol: $negotiated. Use a Streamable HTTP endpoint with a supported initialize handshake." }
        require(result.optJSONObject("capabilities")?.has("tools") == true) { "This MCP endpoint does not expose tools" }
        protocol = negotiated
        request("notifications/initialized", JSONObject(), notification = true)
        ready = true
    }

    private suspend fun request(method: String, params: JSONObject, notification: Boolean = false): JSONObject = runInterruptible(Dispatchers.IO) {
        val id = nextId++
        val body = JSONObject().put("jsonrpc", "2.0").put("method", method).put("params", params).apply { if (!notification) put("id", id) }
        val conn = URI(endpoint).toURL().openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false // never leak a bearer token across origins
            conn.requestMethod = "POST"; conn.doOutput = true
            conn.connectTimeout = 10_000; conn.readTimeout = 25_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json, text/event-stream")
            if (token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            session?.let { conn.setRequestProperty("Mcp-Session-Id", it) }
            if (method != "initialize") conn.setRequestProperty("MCP-Protocol-Version", protocol)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            if (status == 404 && session != null) { session = null; ready = false }
            check(status in 200..299) { when (status) {
                401, 403 -> "MCP authorization denied. Reconnect this service with a valid token."
                404 -> "MCP endpoint or session unavailable. No action was replayed; reconnect and verify the result."
                in 300..399 -> "MCP redirected the request. Enter the final HTTPS endpoint; credentials were not forwarded."
                else -> "MCP HTTP $status; no successful result was received"
            } }
            if (notification) return@runInterruptible JSONObject()
            if (method == "initialize") conn.getHeaderField("Mcp-Session-Id")?.let {
                require(it.isNotEmpty() && it.all { c -> c.code in 0x21..0x7e }) { "Invalid MCP session ID" }; session = it
            }
            val isSse = conn.contentType.orEmpty().startsWith("text/event-stream")
            val reader = conn.inputStream.bufferedReader(Charsets.UTF_8)
            val payload = StringBuilder()
            val line = StringBuilder()
            var total = 0
            fun response(text: String): JSONObject? {
                val parsed = JSONObject(text)
                if (parsed.opt("id")?.toString() != id.toString()) return null
                check(parsed.optString("jsonrpc") == "2.0") { "Invalid MCP JSON-RPC response" }
                parsed.optJSONObject("error")?.let { error("MCP error ${it.optInt("code")}: ${it.optString("message").take(1000)}") }
                return parsed.getJSONObject("result")
            }
            while (true) {
                val c = reader.read()
                if (c < 0) break
                check(++total <= 500_000) { "MCP response exceeds 500KB" }
                if (!isSse) { payload.append(c.toChar()); continue }
                if (c.toChar() == '\n') {
                    val eventLine = line.toString().removeSuffix("\r"); line.setLength(0)
                    if (eventLine.isEmpty() && payload.isNotEmpty()) {
                        response(payload.toString())?.let { return@runInterruptible it }; payload.setLength(0)
                    } else if (eventLine.startsWith("data:")) {
                        if (payload.isNotEmpty()) payload.append('\n')
                        payload.append(eventLine.removePrefix("data:").removePrefix(" "))
                    }
                } else line.append(c.toChar())
            }
            response(payload.toString()) ?: error("MCP response did not match this request")
        } finally { conn.disconnect() }
    }
    companion object {
        fun validateEndpoint(endpoint: String, allowLocalHttp: Boolean = false) {
            val uri = URI(endpoint)
            require(uri.host != null && uri.userInfo == null && uri.fragment == null && uri.rawQuery == null) { "Use an MCP endpoint without embedded credentials, query tokens, or fragments" }
            val host = uri.host.lowercase()
            val octets = host.split('.').mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 0..255 } }
            val privateV4 = octets.size == 4 && (octets[0] == 10 || octets[0] == 192 && octets[1] == 168 || octets[0] == 172 && octets[1] in 16..31)
            require(uri.scheme == "https" || allowLocalHttp && uri.scheme == "http" && (host == "localhost" || host == "127.0.0.1" || privateV4)) { "Use HTTPS (or explicitly approved local-network HTTP)" }
        }
        private fun render(result: JSONObject): String = buildString {
            result.optJSONObject("structuredContent")?.let { append(it.toString()).append('\n') }
            result.optJSONArray("content")?.let { items -> repeat(items.length()) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                when (item.optString("type")) {
                    "text" -> append(item.optString("text")).append('\n')
                    "resource_link" -> append("Resource: ${item.optString("name")} ${item.optString("uri")}\n")
                    "resource" -> append(item.optJSONObject("resource")?.optString("text").orEmpty()).append('\n')
                    else -> append("[${item.optString("type")} content returned; not rendered in the text workflow]\n")
                }
            } }
        }.let { if (it.length <= 11_000) it else it.take(11_000) + "\n[Result truncated to 11,000 characters; request a narrower result before drawing conclusions.]" }
    }
}
