package com.example.meowmba.core.workflows

import com.example.meowmba.core.agent.tools.*
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

object WorkflowMcpTools {
    fun register(registry: ToolRegistry, connections: List<BusinessMcpConnection>, current: (String) -> BusinessMcpConnection?, token: (String) -> String) {
        connections.filter { it.enabled }.forEach { connection ->
            val client by lazy { WorkflowMcpClient(connection.endpoint, token(connection.id)) }
            connection.tools.forEach { spec -> registry.register(object : Tool, WorkflowArgumentValidator {
                // A server hint alone is NEVER authorization. The user must
                // explicitly grant this specific read tool in connection setup.
                private val automaticRead = spec.readOnlyHint && spec.name in connection.automaticReadTools
                override val definition = ToolDefinition(
                    id = "mcp.${connection.id}.${spec.name}", displayName = "${connection.name}: ${spec.name}",
                    description = "Connected service ${connection.name}. ${spec.description}. Supply named args from this schema; nested values are JSON strings: ${spec.schema}",
                    inputSchema = WorkflowMcpArguments.describe(spec.schema), riskLevel = if (automaticRead) RiskLevel.SAFE else RiskLevel.DESTRUCTIVE,
                    confirmationPolicy = if (automaticRead) ConfirmationPolicy.NONE else ConfirmationPolicy.ALWAYS, environments = setOf(ToolEnvironment.CLOUD), timeoutMs = 60_000,
                )
                override fun validateArguments(args: Map<String, String>) { WorkflowMcpArguments.decode(spec.schema, args) }
                override suspend fun execute(args: Map<String, String>): ToolResult = try {
                    check(current(connection.id) == connection) { "Connection changed or was disabled; reconnect and start a new job" }
                    val supplied = WorkflowMcpArguments.decode(spec.schema, args)
                    ToolResult.Success(client.call(spec.name, supplied))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { ToolResult.Failure(e.message ?: "MCP call failed") }
            }) }
        }
    }
}
