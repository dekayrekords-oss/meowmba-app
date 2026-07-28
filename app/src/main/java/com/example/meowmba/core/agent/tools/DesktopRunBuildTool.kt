package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.bridge.DesktopBridgeManager

/**
 * `desktop.run_build` — run a NAMED, pre-declared command on the paired
 * desktop (Phase 6d). The daemon maps command names to fixed argv lists in
 * its own config.json — there is no arbitrary command execution, the same
 * way the phone ships no shell executor.
 *
 * DESTRUCTIVE-class with ConfirmationPolicy.ALWAYS: a build mutates the
 * desktop filesystem and can run for minutes, so the orchestrator's
 * approval card gates every run.
 */
class DesktopRunBuildTool(private val bridge: DesktopBridgeManager) : Tool {
    override val definition = ToolDefinition(
        id = "desktop.run_build",
        displayName = "Run build on desktop",
        description = "Run a named, pre-declared build command on the paired desktop " +
            "(fixed argv in the desktop's config.json — never arbitrary commands).",
        inputSchema = mapOf("name" to "string"),
        riskLevel = RiskLevel.DESTRUCTIVE,
        environments = setOf(ToolEnvironment.DESKTOP),
        confirmationPolicy = ConfirmationPolicy.ALWAYS,
        timeoutMs = 900_000
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        if (!bridge.isPaired()) {
            return ToolResult.Failure("No paired desktop — pair one in Settings → Desktop Bridge")
        }
        val name = args["name"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'name' (a command declared on the desktop)")
        return bridge.runBuild(name).fold(
            onSuccess = { r ->
                val tail = r.output.takeLast(600)
                ToolResult.Success(
                    if (r.timedOut) {
                        "'$name' timed out and was killed after ${r.durationSeconds}s.\n$tail"
                    } else {
                        "'$name' finished in ${r.durationSeconds}s (exit ${r.exitCode}).\n$tail"
                    }
                )
            },
            onFailure = { ToolResult.Failure(it.message ?: "desktop build failed") }
        )
    }
}
