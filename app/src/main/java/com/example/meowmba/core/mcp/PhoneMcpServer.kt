package com.example.meowmba.core.mcp

import org.json.JSONObject

/**
 * The read-only MCP tool surface the phone hosts for an external client such
 * as Android Studio (which connects over LAN as an MCP host).
 *
 * Everything here is a QUERY. There is deliberately no shell, no file write,
 * no device control and nothing destructive — the endpoint is reachable from
 * any host on the same Wi-Fi, so the blast radius has to be zero. If a future
 * build wants an actionable tool it should be gated, not just added.
 *
 * Pure JVM + suspend lambdas so [callTool] is unit-testable without a device.
 */
class PhoneMcpServer(
    private val appName: String,
    private val appVersion: String,
    private val deviceModel: String,
    private val androidVersion: String,
    private val sdkInt: String,
    private val batteryPercent: () -> Int,
    private val board: String,
    private val language: String,
    private val asrBackend: () -> String,
    private val asrUsable: () -> Boolean,
    private val registeredTools: () -> List<String>,
    private val memoryRecall: suspend () -> List<String>,
    private val auditRecent: suspend () -> List<String>,
) : McpProtocol.Server {

    override fun listTools(): List<McpProtocol.McpTool> = listOf(
        McpProtocol.McpTool(
            name = "device.info",
            description = "Read-only identity and build info about this Android phone",
            inputSchema = McpProtocol.schema(),
        ),
        McpProtocol.McpTool(
            name = "device.battery",
            description = "Current battery percentage (0..100)",
            inputSchema = McpProtocol.schema(),
        ),
        McpProtocol.McpTool(
            name = "asr.status",
            description = "Which speech-to-text backend is selected and whether it is usable now",
            inputSchema = McpProtocol.schema(),
        ),
        McpProtocol.McpTool(
            name = "tools.available",
            description = "Names of the agent tools currently registered on the companion",
            inputSchema = McpProtocol.schema(),
        ),
        McpProtocol.McpTool(
            name = "memory.recall",
            description = "Most recent remembered facts, newest first",
            inputSchema = McpProtocol.schema(),
        ),
        McpProtocol.McpTool(
            name = "audit.recent",
            description = "Most recent companion task-log entries (submit/start/complete/fail)",
            inputSchema = McpProtocol.schema("limit" to "how many entries to return (default 10)"),
        ),
    )

    override suspend fun callTool(name: String, args: JSONObject): McpProtocol.McpResult =
        when (name) {
            "device.info" -> McpProtocol.McpResult(
                "app: $appName $appVersion\n" +
                    "device: $deviceModel (board $board)\n" +
                    "android: $androidVersion (sdk $sdkInt)\n" +
                    "language: $language",
            )
            "device.battery" -> McpProtocol.McpResult("battery ${batteryPercent()}%")
            "asr.status" -> McpProtocol.McpResult(
                "backend: ${asrBackend()}\nusable now: ${if (asrUsable()) "yes" else "no"}",
            )
            "tools.available" -> {
                val tools = registeredTools()
                McpProtocol.McpResult(
                    if (tools.isEmpty()) "no tools registered" else "tools: ${tools.joinToString(", ")}",
                )
            }
            "memory.recall" -> {
                val memories = memoryRecall()
                McpProtocol.McpResult(
                    if (memories.isEmpty()) "no memories" else memories.joinToString("\n"),
                )
            }
            "audit.recent" -> {
                val limit = args.optInt("limit", 10).coerceIn(1, 100)
                val entries = auditRecent().take(limit)
                McpProtocol.McpResult(
                    if (entries.isEmpty()) "no audit entries" else entries.joinToString("\n"),
                )
            }
            else -> McpProtocol.McpResult("no such tool: $name", isError = true)
        }
}
