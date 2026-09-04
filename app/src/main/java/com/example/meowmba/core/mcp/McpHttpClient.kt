package com.example.meowmba.core.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP client for EXTERNAL servers over streamable HTTP — e.g. the Android
 * Studio MCP server reachable on the LAN, or a cloud MCP endpoint.
 *
 * Why HTTP and not stdio: the Android Studio MCP host only supports streamable
 * HTTP transport, and a phone cannot run a stdio transport for a remote CLI
 * anyway. We use the "initialize → tools/list → tools/call" handshake on a
 * single POST per request.
 *
 * Pure Kotlin + org.json so the wire handling is unit-testable off-device.
 */
class McpHttpClient(
    private val url: String,
    private val timeoutMs: Long = 20_000,
) {
    private val requestId = java.util.concurrent.atomic.AtomicInteger(1)

    /** True if the endpoint answers `initialize` with an MCP result. */
    suspend fun ping(): Boolean = runCatching {
        val resp = post("initialize", JSONObject()
            .put("protocolVersion", "2024-11-05")
            .put("capabilities", JSONObject())
            .put("clientInfo", JSONObject().put("name", "meowmba").put("version", "1.0")))
        resp.has("result") || resp.has("error")
    }.getOrDefault(false)

    /** Fetch the tools the remote endpoint exposes. */
    suspend fun listTools(): List<McpProtocol.McpTool> {
        val resp = post("tools/list", JSONObject())
        val result = resp.optJSONObject("result") ?: return emptyList()
        val arr = result.optJSONArray("tools") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            McpProtocol.McpTool(
                name = o.optString("name"),
                description = o.optString("description"),
                inputSchema = o.optJSONObject("inputSchema") ?: McpProtocol.schema(),
            )
        }
    }

    suspend fun callTool(name: String, args: JSONObject): McpProtocol.McpResult {
        val resp = post("tools/call", JSONObject()
            .put("name", name)
            .put("arguments", args))
        val err = resp.optJSONObject("error")
        if (err != null) return McpProtocol.McpResult(err.optString("message", "MCP error"), isError = true)
        val result = resp.optJSONObject("result") ?: return McpProtocol.McpResult("empty result", isError = true)
        val text = result.optJSONArray("content")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: ""
        return McpProtocol.McpResult(text, isError = result.optBoolean("isError", false))
    }

    private suspend fun post(method: String, params: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("jsonrpc", McpProtocol.JSONRPC)
            .put("id", requestId.getAndIncrement())
            .put("method", method)
            .put("params", params)
            .toString()
        val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs.toInt()
            conn.readTimeout = timeoutMs.toInt()
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json, text/event-stream")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            // Some servers respond with SSE frames; strip any "data: " prefix.
            val clean = raw.lineSequence()
                .filter { it.isNotEmpty() }
                .joinToString("") { line -> line.removePrefix("data: ") }
            val parsed = runCatching { JSONObject(clean) }
                .getOrElse { JSONObject().put("error", JSONObject().put("code", code).put("message", "HTTP $code")) }
            parsed
        } finally {
            conn.disconnect()
        }
    }
}
