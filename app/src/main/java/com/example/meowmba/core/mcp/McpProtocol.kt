package com.example.meowmba.core.mcp

import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal Model Context Protocol (MCP) surface — JSON-RPC 2.0.
 *
 * Why in-process rather than over a socket: the tools being exposed drive a
 * local WebView, which lives and dies with this app process. Putting a network
 * boundary in front of it would buy nothing but a new failure mode (and a
 * listening port). The wire format is still real MCP JSON-RPC 2.0, so the same
 * tool objects can be served over stdio/HTTP later without touching the tools.
 *
 * Pure Kotlin + org.json (on-device AND on the JVM, see the org.json test
 * dependency) so requests can be unit-tested without Android.
 */
object McpProtocol {

    const val JSONRPC = "2.0"

    // JSON-RPC error codes (plus MCP's own where they overlap).
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603

    /** One exposed capability. [inputSchema] is a JSON Schema object. */
    data class McpTool(
        val name: String,
        val description: String,
        val inputSchema: JSONObject,
    )

    /** What a tool returned. [isError] keeps a failed call from looking like text. */
    data class McpResult(
        val text: String,
        val isError: Boolean = false,
    )

    /** Implemented by anything that serves MCP tools. */
    interface Server {
        fun listTools(): List<McpTool>
        suspend fun callTool(name: String, args: JSONObject): McpResult
    }

    /**
     * Dispatch one JSON-RPC request against [server].
     *
     * Never throws — a tool that blows up must come back as a JSON-RPC error
     * response, not kill the caller (the agent treats a thrown exception as a
     * dead tool and stops offering it).
     */
    suspend fun handle(server: Server, rawRequest: String): String = try {
        val req = JSONObject(rawRequest)
        val id = req.opt("id")
        val method = req.optString("method")
        val result: Any? = when (method) {
            "initialize" -> JSONObject()
                .put("protocolVersion", "2024-11-05")
                .put("serverInfo", JSONObject().put("name", serverName(server)).put("version", "1.0"))
                .put("capabilities", JSONObject().put("tools", JSONObject()))
            "tools/list" -> JSONObject().put(
                "tools",
                JSONArray().apply {
                    server.listTools().forEach { tool ->
                        put(
                            JSONObject()
                                .put("name", tool.name)
                                .put("description", tool.description)
                                .put("inputSchema", tool.inputSchema),
                        )
                    }
                },
            )
            "tools/call" -> {
                val params = req.optJSONObject("params") ?: JSONObject()
                val name = params.optString("name")
                val args = params.optJSONObject("arguments") ?: JSONObject()
                val out = server.callTool(name, args)
                JSONObject()
                    .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", out.text)))
                    .put("isError", out.isError)
            }
            "ping" -> JSONObject()
            else -> return error(id, METHOD_NOT_FOUND, "unknown method: $method")
        }
        JSONObject()
            .put("jsonrpc", JSONRPC)
            .put("id", id)
            .put("result", result)
            .toString()
    } catch (e: org.json.JSONException) {
        error(null, PARSE_ERROR, "invalid JSON: ${e.message}")
    } catch (e: Exception) {
        error(null, INTERNAL_ERROR, e.message ?: "internal error")
    }

    private fun serverName(server: Server): String =
        server::class.simpleName ?: "mcp"

    private fun error(id: Any?, code: Int, message: String): String =
        JSONObject()
            .put("jsonrpc", JSONRPC)
            .put("id", id ?: JSONObject.NULL)
            .put(
                "error",
                JSONObject().put("code", code).put("message", message),
            )
            .toString()

    /** Builds the JSON Schema object for a tool that takes named string args. */
    fun schema(vararg required: Pair<String, String>): JSONObject {
        val props = JSONObject()
        required.forEach { (name, desc) ->
            props.put(name, JSONObject().put("type", "string").put("description", desc))
        }
        return JSONObject()
            .put("type", "object")
            .put("properties", props)
            .put("required", JSONArray().apply { required.forEach { put(it.first) } })
    }
}
