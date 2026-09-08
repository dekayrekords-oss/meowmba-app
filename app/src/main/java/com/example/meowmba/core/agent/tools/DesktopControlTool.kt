package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.bridge.DesktopBridgeManager

/**
 * `desktop.control` — drive the PAIRED desktop: open apps/URLs/files,
 * read/write the clipboard, control media playback, and screenshot the
 * screen (Phase F1). Screenshots can be described by the vision provider
 * via [describeImage] so the companion can tell the user what it sees.
 *
 * Trust: MODERATE + ConfirmationPolicy.ALWAYS — every action goes through
 * the orchestrator's approval card. Unpaired bridge = honest failure.
 */
class DesktopControlTool(
    private val bridge: DesktopBridgeManager,
    private val screenshotDir: java.io.File? = null,
    /** Optional vision pass: PNG bytes in, one-sentence description out. */
    private val describeImage: (suspend (ByteArray) -> String?)? = null
) : Tool {
    override val definition = ToolDefinition(
        id = "desktop.control",
        displayName = "Control desktop",
        description = "Act on the paired desktop computer: open_app(name), open_url(url), " +
            "open_file(path), clipboard_get, clipboard_set(text), media(play|pause|next|volume), " +
            "screenshot (described aloud), status, read_screen, click(snapshot_id, element_id), type(snapshot_id, element_id, value). UI control requires bridge 0.7 on macOS with explicit OS access. Each action asks approval; snapshots expire and cannot be reused.",
        inputSchema = mapOf(
            "action" to "open_app|open_url|open_file|clipboard_get|clipboard_set|media|screenshot|status|read_screen|click|type",
            "snapshot_id" to "ID returned by read_screen; never invent",
            "element_id" to "exact accessible control ID returned by read_screen",
            "value" to "exact text to type; visible on approval",
            "name" to "app name (open_app)",
            "url" to "url (open_url)",
            "path" to "file path (open_file)",
            "text" to "clipboard content (clipboard_set)",
            "media" to "play|pause|next|volume (media)",
            "volume" to "0-100 (media volume)"
        ),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.DESKTOP),
        confirmationPolicy = ConfirmationPolicy.ALWAYS,
        timeoutMs = 45_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        if (!bridge.isPaired()) {
            return ToolResult.Failure("No paired desktop — pair one in Settings → Desktop Bridge")
        }
        return when (val action = args["action"]?.trim()?.lowercase()) {
            "status" -> bridge.status().fold({ ToolResult.Success("Connected to ${it.name} · ${it.platform} · bridge ${it.version}. UI/Blender still require their desktop capability switches and OS permissions.") }, { ToolResult.Failure(it.message ?: "Desktop unreachable") })
            "read_screen" -> bridge.readUi().fold({ ToolResult.Success(it.toString()) }, { ToolResult.Failure(it.message ?: "Desktop UI unavailable") })
            "click", "type" -> bridge.uiAction(args["snapshot_id"].orEmpty(), args["element_id"].orEmpty(), action, args["value"].orEmpty()).toTool()
            "open_app" -> args["name"]?.takeIf { it.isNotBlank() }?.let { bridge.openApp(it).toTool() }
                ?: ToolResult.Failure("Missing 'name' for open_app")
            "open_url" -> args["url"]?.takeIf { it.isNotBlank() }?.let { bridge.openUrl(it).toTool() }
                ?: ToolResult.Failure("Missing 'url' for open_url")
            "open_file" -> args["path"]?.takeIf { it.isNotBlank() }?.let { bridge.openFile(it).toTool() }
                ?: ToolResult.Failure("Missing 'path' for open_file")
            "clipboard_get" -> bridge.clipboardGet().fold(
                onSuccess = { ToolResult.Success("Clipboard says: ${it.take(800)}") },
                onFailure = { ToolResult.Failure(it.message ?: "clipboard read failed") }
            )
            "clipboard_set" -> args["text"]?.let { bridge.clipboardSet(it).toTool() }
                ?: ToolResult.Failure("Missing 'text' for clipboard_set")
            "media" -> {
                val media = args["media"]?.trim()?.lowercase()
                    ?: return ToolResult.Failure("Missing 'media' (play|pause|next|volume)")
                val volume = if (media == "volume") {
                    args["volume"]?.toIntOrNull()
                        ?: return ToolResult.Failure("Missing 'volume' (0-100)")
                } else null
                bridge.mediaControl(media, volume).toTool()
            }
            "screenshot" -> bridge.screenshot().fold(
                onSuccess = { shot ->
                    val bytes = try {
                        java.util.Base64.getDecoder().decode(shot.pngBase64)
                    } catch (e: Exception) { ByteArray(0) }
                    screenshotDir?.let { dir ->
                        runCatching {
                            dir.mkdirs()
                            java.io.File(dir, "desktop-${System.currentTimeMillis()}.png")
                                .writeBytes(bytes)
                        }
                    }
                    val description = if (bytes.isNotEmpty()) {
                        runCatching { describeImage?.invoke(bytes) }.getOrNull()
                    } else null
                    ToolResult.Success(
                        description?.let { "Desktop screenshot: $it" }
                            ?: "Captured desktop screenshot (${shot.bytes} B) but I can't describe it right now."
                    )
                },
                onFailure = { ToolResult.Failure(it.message ?: "screenshot failed") }
            )
            else -> ToolResult.Failure(
                "Unknown action '${action ?: ""}'. Use: open_app, open_url, open_file, " +
                    "clipboard_get, clipboard_set, media, screenshot."
            )
        }
    }

    private fun Result<String>.toTool(): ToolResult = fold(
        onSuccess = { ToolResult.Success(it) },
        onFailure = { ToolResult.Failure(it.message ?: "desktop action failed") }
    )
}
