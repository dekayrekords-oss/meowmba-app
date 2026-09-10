package com.example.meowmba.core.agent.tools

import android.graphics.*
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * `media.generate_image` — routes to real AI image generation (on-device
 * MediaPipe diffusion, or a user-configured remote server) when a backend
 * is available, and reports a failure when neither backend works.
 * Placeholder drawings are not passed off as generated images. The companion uses it when
 * the user asks to create an image or visualize an idea.
 *
 * SAFE risk, no confirmation — writes to the workspace.
 */
class MediaGenerateTool(
    private val workspace: Workspace,
    /** Supplies the on-device generator. Null when unavailable. */
    private val localGenerator: (() -> com.example.meowmba.core.media.ImageGenerator?)? = null,
    /** Supplies the remote generator. Null unless the user configured a server URL. */
    private val remoteGenerator: (() -> com.example.meowmba.core.media.ImageGenerator?)? = null,
    /** True when Settings has "prefer server" selected. Defaults to local-first. */
    private val preferServer: () -> Boolean = { false },
    /** Captures provider choice/credentials once, so changing settings mid-request
     * cannot send a formerly local prompt to a newly selected cloud provider. */
    private val generatorPlan: (() -> List<() -> com.example.meowmba.core.media.ImageGenerator?>)? = null,
) : Tool {
    override val definition = ToolDefinition(
        id = "media.generate_image",
        displayName = "Generate image",
        description = "Create a PNG image from a text description. Tries the " +
            "configured image backend (on-device or a configured server) and " +
            "reports a setup or generation failure if neither works. " +
            "The result is saved as a .png file in the workspace.",
        inputSchema = mapOf(
            "prompt" to "string",
            "style" to "string"
        ),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        // Community workers may queue for minutes. Horde has its own 9-minute
        // ceiling and cancels the remote ticket; leave time for cleanup here.
        timeoutMs = 600_000
    )

    /**
     * Tries backends in preference order, falling through to the next one on
     * unavailability or failure. Returns null only when every configured
     * backend failed, so the caller reports that no image was created.
     *
     * A backend exception permits trying the next configured backend.
     * If all attempts fail, no substitute artwork is presented as success.
     */
    private suspend fun tryGenerators(fullPrompt: String): Result<Bitmap> {
        var safeError: Throwable? = null
        val order = generatorPlan?.invoke() ?: if (preferServer()) {
            listOf(remoteGenerator, localGenerator)
        } else {
            listOf(localGenerator, remoteGenerator)
        }
        for (supplier in order) {
            val generator = supplier?.invoke() ?: continue
            if (!generator.isAvailable()) continue
            val result = try { generator.generate(fullPrompt) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { null }
            result?.getOrNull()?.let { return Result.success(it) }
            val error = result?.exceptionOrNull()
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (error is com.example.meowmba.core.media.ImageServiceException) safeError = error
            if (result == null) {
                Log.w(TAG, "image backend threw instead of returning a Result")
            } else {
                Log.w(TAG, "image backend failed (${error?.javaClass?.simpleName})")
            }
        }
        return Result.failure(safeError ?: IllegalStateException("Image generation is unavailable or failed. Configure a working image provider or install a supported on-device model in Settings → Images."))
    }

    private companion object {
        const val TAG = "MediaGenerateTool"
    }

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val prompt = args["prompt"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'prompt'")
        val style = args["style"]?.takeIf { it.isNotBlank() }?.lowercase() ?: "auto"
        val fullPrompt = if (style != "auto") "$prompt, $style style" else prompt

        val result = tryGenerators(fullPrompt)
        val bitmap = result.getOrNull()
        if (bitmap != null) {
            val name = fileNameFor(prompt, prefix = "image")
            return@withContext try {
                workspace.newAsset("photos", name).let { file ->
                    FileOutputStream(file).use { out ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encoding failed" }
                    }
                    ToolResult.Success(
                        "Image saved as \"${file.relativeTo(workspace.root).invariantSeparatorsPath}\" in the workspace: $prompt" +
                            "\nMEDIA_FILE:${file.absolutePath}"
                    )
                } ?: ToolResult.Failure("Could not write image — path is outside the workspace.")
            } catch (e: Exception) {
                ToolResult.Failure("Failed to save the generated image: ${e.message}")
            } finally {
                bitmap.recycle()
            }
        }

        ToolResult.Failure(
            "${result.exceptionOrNull()?.message} No image was created."
        )
    }

    fun pickBackground(style: String): Int = when {
        style.contains("dark") || style.contains("night") -> Color.rgb(13, 17, 23)
        style.contains("light") || style.contains("day") -> Color.rgb(240, 240, 245)
        style.contains("ocean") || style.contains("sea") || style.contains("water") -> Color.rgb(10, 30, 50)
        style.contains("forest") || style.contains("nature") || style.contains("green") -> Color.rgb(20, 40, 20)
        style.contains("sunset") || style.contains("warm") -> Color.rgb(40, 20, 10)
        style.contains("neon") || style.contains("cyber") -> Color.rgb(10, 10, 30)
        else -> Color.rgb(22, 27, 34)
    }

    fun pickAccent(style: String): Int = when {
        style.contains("ocean") || style.contains("sea") || style.contains("water") -> Color.rgb(88, 166, 255)
        style.contains("forest") || style.contains("nature") || style.contains("green") -> Color.rgb(63, 185, 80)
        style.contains("sunset") || style.contains("warm") -> Color.rgb(255, 160, 60)
        style.contains("neon") || style.contains("cyber") -> Color.rgb(188, 140, 255)
        style.contains("red") || style.contains("love") || style.contains("heart") -> Color.rgb(248, 81, 73)
        style.contains("dark") || style.contains("night") -> Color.rgb(88, 166, 255)
        style.contains("light") || style.contains("day") -> Color.rgb(30, 100, 200)
        else -> Color.rgb(88, 166, 255)
    }

    private fun wordWrap(text: String, maxChars: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isNotEmpty() && current.length + word.length + 1 > maxChars) {
                lines.add(current.toString())
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(" ")
            current.append(word)
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.ifEmpty { listOf(text) }
    }

    fun setAlpha(color: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    /**
     * Filename from the prompt. The prefix records which renderer produced it,
     * so a workspace full of "canvas_*" vs "image_*" tells you at a glance
     * whether the diffusion model was actually running.
     */
    private fun fileNameFor(prompt: String, prefix: String): String {
        val safe = prompt.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").take(40).trim()
            .replace(Regex("\\s+"), "_")
        return if (safe.isNotBlank()) "${prefix}_$safe.png" else "${prefix}_${System.currentTimeMillis()}.png"
    }
}

/**
 * `media.generate_video` — saves MP4 output from a configured video backend.
 * A missing or failed backend is a failure, never a still-frame substitute.
 */
class MediaGenerateVideoTool(
    private val workspace: Workspace,
    /** Supplies the remote generator. Null unless the user configured a server URL. */
    private val remoteGenerator: (() -> com.example.meowmba.core.media.VideoGenerator?)? = null,
    /** Forwarded to the remote generator's poll loop so a multi-minute wait isn't silent. */
    private val onProgress: (String) -> Unit = {}
) : Tool {
    override val definition = ToolDefinition(
        id = "media.generate_video",
        displayName = "Generate video",
        description = "Create a video from a text description. Tries the configured " +
            "video server if one is set, and reports a failure if no playable video " +
            "is returned. Makes a FILE in the workspace — never for making the " +
            "cat avatar itself move or perform; that is avatar.animate.",
        inputSchema = mapOf("prompt" to "string", "style" to "string", "frames" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        // Includes the provider's bounded polling and transfer budget.
        timeoutMs = 1_080_000
    )

    private companion object {
        const val TAG = "MediaGenerateVideoTool"
    }

    /** Cancellation is a stop, never a failed attempt that can save a late artifact. */
    private suspend fun tryRemote(prompt: String): ByteArray? = try {
        val generator = remoteGenerator?.invoke()
        if (generator == null || !generator.isAvailable()) null else {
            val result = generator.generate(prompt, onProgress)
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            currentCoroutineContext().ensureActive()
            result.getOrNull()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "video backend failed (${e.javaClass.simpleName})")
        null
    }

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val prompt = args["prompt"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'prompt'")
        val remoteBytes = tryRemote(prompt)
        currentCoroutineContext().ensureActive()
        if (remoteBytes != null) {
            if (remoteBytes.size < 12 ||
                remoteBytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) != "ftyp") {
                return@withContext ToolResult.Failure("Video server returned an invalid MP4. No video was saved.")
            }
            val safeName = prompt.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").take(40).trim()
                .replace(Regex("\\s+"), "_")
            val fileName = if (safeName.isNotBlank()) "video_${safeName}.mp4" else "video_${System.currentTimeMillis()}.mp4"
            var created: File? = null
            return@withContext try {
                currentCoroutineContext().ensureActive()
                val file = workspace.newAsset("videos", fileName)
                created = file
                FileOutputStream(file).use { out ->
                    var offset = 0
                    while (offset < remoteBytes.size) {
                        currentCoroutineContext().ensureActive()
                        val count = minOf(64 * 1024, remoteBytes.size - offset)
                        out.write(remoteBytes, offset, count)
                        offset += count
                    }
                }
                currentCoroutineContext().ensureActive()
                ToolResult.Success(
                    "Video saved as \"${file.relativeTo(workspace.root).invariantSeparatorsPath}\" in the workspace: $prompt\nMEDIA_FILE:${file.absolutePath}"
                )
            } catch (e: CancellationException) {
                created?.delete()
                throw e
            } catch (e: Exception) {
                created?.delete()
                ToolResult.Failure("Failed to save the generated video: ${e.javaClass.simpleName}")
            }
        }

        ToolResult.Failure(
            "Video generation is unavailable or failed. Configure and test a video server " +
                "in Settings. No playable video was created."
        )
    }


}

/** Creates a WAV only when a configured music provider returns valid audio.
 * A missing/failed provider must not substitute procedural tones for a song. */
class MediaGenerateAudioTool(
    private val workspace: Workspace,
    private val remoteGenerator: (() -> com.example.meowmba.core.media.MusicGenerator?)? = null,
) : Tool {
    override val definition = ToolDefinition(
        id = "media.generate_audio",
        displayName = "Generate audio",
        description = "Create a WAV audio file using the configured music generation server. " +
            "Reports a failure if no provider or valid WAV is available; never substitutes test tones. " +
            "This creates new audio, not an edit, mix or master of an existing song.",
        inputSchema = mapOf("prompt" to "string", "duration_sec" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 200_000,
    )

    private suspend fun tryRemote(prompt: String, durationSec: Int): ByteArray? = try {
        val generator = remoteGenerator?.invoke()
        if (generator == null || !generator.isAvailable()) null else {
            val result = generator.generate(prompt, durationSec)
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            currentCoroutineContext().ensureActive()
            result.getOrNull()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("MediaGenerateAudioTool", "music backend failed (${e.javaClass.simpleName})")
        null
    }

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val prompt = args["prompt"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'prompt'")
        val durationSec = args["duration_sec"]?.toIntOrNull()?.coerceIn(1, 30) ?: 5
        val bytes = tryRemote(prompt, durationSec)
        currentCoroutineContext().ensureActive()
        if (bytes == null) return@withContext ToolResult.Failure(
            "Audio generation is unavailable or failed. Configure and test a music server in Settings. No audio was created.")
        if (!com.example.meowmba.core.media.WavValidation.hasAudio(bytes)) {
            return@withContext ToolResult.Failure("Music server returned an invalid or empty WAV. No audio was saved.")
        }
        val safeName = prompt.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").take(40).trim()
            .replace(Regex("\\s+"), "_")
        val fileName = if (safeName.isNotBlank()) "audio_${safeName}.wav" else "audio_${System.currentTimeMillis()}.wav"
        var created: File? = null
        try {
            currentCoroutineContext().ensureActive()
            val file = workspace.newAsset("audio", fileName)
            created = file
            FileOutputStream(file).use { out ->
                var offset = 0
                while (offset < bytes.size) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(64 * 1024, bytes.size - offset)
                    out.write(bytes, offset, count)
                    offset += count
                }
            }
            currentCoroutineContext().ensureActive()
            ToolResult.Success("Audio saved as \"${file.relativeTo(workspace.root).invariantSeparatorsPath}\" in the workspace: $prompt\nMEDIA_FILE:${file.absolutePath}")
        } catch (e: CancellationException) {
            created?.delete()
            throw e
        } catch (e: Exception) {
            created?.delete()
            ToolResult.Failure("Failed to save the generated audio: ${e.javaClass.simpleName}")
        }
    }
}
