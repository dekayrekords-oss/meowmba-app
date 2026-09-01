package com.example.meowmba.core.mcp

import com.example.meowmba.core.agent.tools.ConfirmationPolicy
import com.example.meowmba.core.agent.tools.RiskLevel
import com.example.meowmba.core.agent.tools.Tool
import com.example.meowmba.core.agent.tools.ToolDefinition
import com.example.meowmba.core.agent.tools.ToolEnvironment
import com.example.meowmba.core.agent.tools.ToolResult
import org.json.JSONObject

/**
 * Publishes an MCP server's tools into the agent's [ToolRegistry].
 *
 * The model should not care whether a tool is implemented locally or served
 * over MCP, so each MCP tool is wrapped in an ordinary [Tool] and registered
 * like any other. Calls go through [McpProtocol.handle] — real JSON-RPC — so
 * the same server could be moved out of process later without touching the
 * agent or these adapters.
 *
 * Arguments arrive as `Map<String, String>` (how every other tool in the app
 * is called) and are converted to a JSON object for the MCP call.
 */
class McpToolBridge(
    private val server: McpProtocol.Server,
    /** Prefix so two servers cannot collide on a tool id. */
    private val idPrefix: String = "",
    private val riskLevel: RiskLevel = RiskLevel.SAFE,
) {
    /** One MCP tool, exposed as an app [Tool]. */
    private inner class Adapted(private val tool: McpProtocol.McpTool) : Tool {
        override val definition = ToolDefinition(
            id = idPrefix + tool.name,
            displayName = tool.name.substringAfterLast('.'),
            description = tool.description,
            inputSchema = schemaToMap(tool.inputSchema),
            riskLevel = riskLevel,
            environments = setOf(ToolEnvironment.PHONE),
            confirmationPolicy = ConfirmationPolicy.NONE,
            // Loading a page can legitimately take a while.
            timeoutMs = 60_000,
        )

        override suspend fun execute(args: Map<String, String>): ToolResult {
            val params = JSONObject()
                .put("name", tool.name)
                .put("arguments", JSONObject().apply { args.forEach { (k, v) -> put(k, v) } })
            val request = JSONObject()
                .put("jsonrpc", McpProtocol.JSONRPC)
                .put("id", 1)
                .put("method", "tools/call")
                .put("params", params)
                .toString()
            val response = McpProtocol.handle(server, request)
            return try {
                val json = JSONObject(response)
                val error = json.optJSONObject("error")
                if (error != null) {
                    return ToolResult.Failure(error.optString("message", "MCP error"))
                }
                val result = json.optJSONObject("result") ?: return ToolResult.Failure("empty MCP result")
                val text = result.optJSONArray("content")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?: ""
                if (result.optBoolean("isError", false)) ToolResult.Failure(text) else ToolResult.Success(text)
            } catch (e: Exception) {
                ToolResult.Failure("bad MCP response: ${e.message}")
            }
        }
    }

    fun tools(): List<Tool> = server.listTools().map { Adapted(it) }

    private fun schemaToMap(schema: JSONObject): Map<String, String> {
        val props = schema.optJSONObject("properties") ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        props.keys().forEach { key ->
            out[key] = props.optJSONObject(key)?.optString("type", "string") ?: "string"
        }
        return out
    }
}
