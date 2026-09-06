package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.glasses.MetaGlassesController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileOutputStream

/**
 * `glasses.take_photo` — capture a still from the connected Ray-Ban glasses
 * and SHOW it (MEDIA_FILE opens the browser overlay, same contract as every
 * other media tool).
 *
 * The MWDAT camera stream delivers JPEG frames onto [MetaGlassesController]'s
 * bus; this tool grabs the freshest one. If nothing is streaming yet it asks
 * the controller to start and waits up to 15s — honest failure when the
 * glasses are off, instead of silently pretending a photo happened.
 */
class GlassesTakePhotoTool(
    private val glasses: MetaGlassesController?,
    private val workspace: Workspace
) : Tool {
    override val definition = ToolDefinition(
        id = "glasses.take_photo",
        displayName = "Take photo with glasses",
        description = "Capture a photo through the connected Ray-Ban Meta glasses and show it " +
            "on screen. Fails honestly when the glasses are not connected — tell the user to " +
            "connect them on the Hardware page instead of faking a photo.",
        inputSchema = emptyMap(),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE)
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        if (glasses == null) {
            return@withContext ToolResult.Failure("No glasses controller in this build.")
        }
        if (!glasses.hasSdk()) {
            return@withContext ToolResult.Failure(
                "Glasses camera support is not included in this installation. Update to a glasses-enabled build; adding an API key in Settings cannot install the SDK."
            )
        }
        var frame = glasses.latestJpeg.value
        if (frame == null) {
            // Nudge the stream, then wait for first light.
            glasses.ensureCaptureStream()
            frame = withTimeoutOrNull(15_000) {
                while (glasses.latestJpeg.value == null) delay(250)
                glasses.latestJpeg.value
            }
        }
        if (!glasses.isRealDevice.value || !glasses.isStreaming.value ||
            System.currentTimeMillis() - MetaGlassesController.lastFrameAtMs.value > 12_000L) {
            return@withContext ToolResult.Failure(
                "No fresh camera frame from real glasses. Reconnect them on the Hardware page and try again."
            )
        }
        if (frame == null) {
            return@withContext ToolResult.Failure(
                "No camera frame from the glasses yet. Connect them on the Hardware page " +
                    "(Setup glasses → Connect) and approve camera permission in Meta AI."
            )
        }
        val file = workspace.resolve("glasses/photo_${System.currentTimeMillis()}.jpg")
            ?: return@withContext ToolResult.Failure("Could not write photo — path is outside the workspace.")
        runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { it.write(frame) }
        }.onFailure {
            return@withContext ToolResult.Failure("Failed to save the photo: ${it.message}")
        }
        ToolResult.Success(
            "Photo captured through the glasses and opening now.\nMEDIA_FILE:${file.absolutePath}"
        )
    }
}
