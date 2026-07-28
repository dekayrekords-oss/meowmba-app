package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.bridge.DesktopBridgeManager

/**
 * `desktop.write_file` — write a utf-8 text file on the PAIRED desktop,
 * inside its declared workspace roots (Phase 6c).
 *
 * Trust boundaries (all enforced, none assumed):
 *  - MODERATE risk with ConfirmationPolicy.ALWAYS — the orchestrator's
 *    approval card decides, the tool never executes silently.
 *  - The daemon re-checks confinement, the sensitive-name denylist, and the
 *    write cap on its side; existing files are backed up before overwrite
 *    and writes are atomic + audit-logged on the desktop.
 *  - Unpaired bridge = honest failure, never a fake success.
 */
class DesktopWriteFileTool(private val bridge: DesktopBridgeManager) : Tool {
    override val definition = ToolDefinition(
        id = "desktop.write_file",
        displayName = "Write file on desktop",
        description = "Create or overwrite a utf-8 text file inside the paired desktop's " +
            "workspace roots. Existing files are backed up first; the write is atomic.",
        inputSchema = mapOf("path" to "path", "content" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.DESKTOP),
        confirmationPolicy = ConfirmationPolicy.ALWAYS
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        if (!bridge.isPaired()) {
            return ToolResult.Failure("No paired desktop — pair one in Settings → Desktop Bridge")
        }
        val path = args["path"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'path'")
        val content = args["content"]
            ?: return ToolResult.Failure("Missing 'content'")
        return bridge.writeFile(path, content).fold(
            onSuccess = { r ->
                ToolResult.Success(
                    "Wrote ${r.path} (${r.size} B" +
                        (if (r.created) ", new file)" else ", backup: ${r.backup})")
                )
            },
            onFailure = { ToolResult.Failure(it.message ?: "desktop write failed") }
        )
    }
}
