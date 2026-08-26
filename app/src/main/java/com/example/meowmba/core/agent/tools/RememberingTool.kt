package com.example.meowmba.core.agent.tools

import android.content.ComponentName
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `memory.start_remembering` / `memory.stop_remembering` — drive the
 * MetaMemory app's continuous capture from Meowmba.
 *
 * MetaMemory ingests glasses frames (and mic) into its own store AND pushes
 * each event into this Room via the runtime bridge, so "start remembering"
 * makes both apps track the same memories. The service must be exported on
 * the MetaMemory side (com.meow.metamemory/.capture.CaptureService).
 *
 * Honesty: reports when MetaMemory is not installed or the start fails,
 * instead of implying memories are being kept.
 */
class RememberingTool(
    private val context: android.content.Context?,
    private val start: Boolean,
) : Tool {
    override val definition = ToolDefinition(
        id = if (start) "memory.start_remembering" else "memory.stop_remembering",
        displayName = if (start) "Start remembering" else "Stop remembering",
        description = (if (start) "Start" else "Stop") +
            " continuous memory capture via the MetaMemory app (glasses frames + speech). " +
            "Captured memories are shared back into Meowmba's memory automatically.",
        inputSchema = emptyMap(),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE)
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext ToolResult.Failure("No app context available.")
        val intent = Intent().apply {
            component = ComponentName(META_PACKAGE, CAPTURE_SERVICE)
            if (!start) action = ACTION_STOP
        }
        val installed = runCatching {
            ctx.packageManager.getServiceInfo(intent.component!!, 0)
        }.getOrNull() != null
        if (!installed) {
            return@withContext ToolResult.Failure(
                "MetaMemory app is not installed (package $META_PACKAGE not found)."
            )
        }
        try {
            if (start) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
            ToolResult.Success(
                if (start) {
                    "MetaMemory capture started — glasses memories will flow into Meowmba's memory as they land."
                } else {
                    "MetaMemory capture stopped."
                }
            )
        } catch (e: Exception) {
            ToolResult.Failure("Could not ${if (start) "start" else "stop"} MetaMemory capture: ${e.message}")
        }
    }

    companion object {
        private const val META_PACKAGE = "com.meow.metamemory"
        private const val CAPTURE_SERVICE = "com.meow.metamemory.capture.CaptureService"
        private const val ACTION_STOP = "com.meow.metamemory.capture.STOP"
    }
}
