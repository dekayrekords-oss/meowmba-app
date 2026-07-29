package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.bridge.DesktopBridgeManager

/**
 * `desktop.run_script` — run a script file (e.g. .py, .sh) on the paired
 * desktop. The daemon executes it with /usr/bin/env and streams output.
 *
 * DESTRUCTIVE-class with ConfirmationPolicy.ALWAYS: arbitrary script
 * execution mutates the desktop and can consume resources.
 */
class DesktopRunScriptTool(private val bridge: DesktopBridgeManager) : Tool {
    override val definition = ToolDefinition(
        id = "desktop.run_script",
        displayName = "Run script on desktop",
        description = "Run a script file on the paired desktop (Python, shell, etc.). " +
            "Provide the path to the script and optional arguments.",
        inputSchema = mapOf(
            "path" to "string",
            "args" to "string",
            "timeout_sec" to "string"
        ),
        riskLevel = RiskLevel.DESTRUCTIVE,
        environments = setOf(ToolEnvironment.DESKTOP),
        confirmationPolicy = ConfirmationPolicy.ALWAYS,
        timeoutMs = 900_000
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        if (!bridge.isPaired()) {
            return ToolResult.Failure("No paired desktop — pair one in Settings → Desktop Bridge")
        }
        val path = args["path"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'path' — path to the script on the desktop")
        val scriptArgs = args["args"] ?: ""
        val timeoutSec = args["timeout_sec"]?.toIntOrNull()?.coerceIn(5, 600) ?: 60
        return bridge.runScript(path, scriptArgs, timeoutSec).fold(
            onSuccess = { r ->
                val tail = r.output.takeLast(600)
                ToolResult.Success(
                    if (r.timedOut) {
                        "'$path' timed out after ${r.durationSeconds}s.\n$tail"
                    } else {
                        "'$path' finished in ${r.durationSeconds}s (exit ${r.exitCode}).\n$tail"
                    }
                )
            },
            onFailure = { ToolResult.Failure(it.message ?: "desktop script failed") }
        )
    }
}
