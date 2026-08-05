package com.example.meowmba.core.agent.tools

import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * `media.generate_image` — routes to real AI image generation (on-device
 * MediaPipe diffusion, or a user-configured remote server) when a backend
 * is available, and falls back to a simple procedural canvas renderer
 * (text, geometric shapes, color themes drawn from the prompt) when
 * neither backend is available or both fail. The companion uses it when
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
    private val preferServer: () -> Boolean = { false }
) : Tool {
    override val definition = ToolDefinition(
        id = "media.generate_image",
        displayName = "Generate image",
        description = "Create a PNG image from a text description. Tries the " +
            "configured image backend (on-device or a configured server) and " +
            "falls back to a simple drawn canvas if neither is available. " +
            "The result is saved as a .png file in the workspace.",
        inputSchema = mapOf(
            "prompt" to "string",
            "style" to "string"
        ),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        // 15s was inherited from the canvas-only days, when this tool drew a
        // bitmap and returned instantly. On-device diffusion loads ~2 GB of
        // weights and runs 20 denoising steps — the orchestrator was killing
        // it mid-generation every time. The canvas fallback still returns in
        // milliseconds, so a long ceiling costs it nothing.
        timeoutMs = 300_000
    )

    /**
     * Tries backends in preference order, falling through to the next one on
     * unavailability or failure. Returns null only when every configured
     * backend failed, so the caller can fall back to the canvas placeholder.
     *
     * The `runCatching` here is the routing layer enforcing its own "always
     * produce something" guarantee structurally: both current backends
     * already catch Throwable internally and return Result.failure, but a
     * future backend (e.g. a planned JNI-based local backend) isn't
     * guaranteed to be equally careful — a native crash escaping generate()
     * should still fall through to the canvas, not take down the tool call.
     */
    private suspend fun tryGenerators(fullPrompt: String): Bitmap? {
        val order = if (preferServer()) {
            listOf(remoteGenerator, localGenerator)
        } else {
            listOf(localGenerator, remoteGenerator)
        }
        for (supplier in order) {
            val generator = supplier?.invoke() ?: continue
            if (!generator.isAvailable()) continue
            val result = runCatching { generator.generate(fullPrompt) }.getOrNull()
            result?.getOrNull()?.let { return it }
            if (result == null) {
                Log.w(TAG, "image backend threw instead of returning a Result")
            } else {
                Log.w(TAG, "image backend failed: ${result.exceptionOrNull()?.message}")
            }
        }
        return null
    }

    private companion object {
        const val TAG = "MediaGenerateTool"
    }

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val prompt = args["prompt"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'prompt'")
        val style = args["style"]?.takeIf { it.isNotBlank() }?.lowercase() ?: "auto"
        val fullPrompt = if (style != "auto") "$prompt, $style style" else prompt

        val bitmap = tryGenerators(fullPrompt)
        if (bitmap != null) {
            val name = fileNameFor(prompt, prefix = "image")
            return@withContext try {
                workspace.resolve(name)?.let { file ->
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    ToolResult.Success(
                        "Image saved as \"$name\" in the workspace: $prompt" +
                            "\nMEDIA_FILE:${file.absolutePath}"
                    )
                } ?: ToolResult.Failure("Could not write image — path is outside the workspace.")
            } catch (e: Exception) {
                ToolResult.Failure("Failed to save the generated image: ${e.message}")
            } finally {
                bitmap.recycle()
            }
        }

        val width = 512
        val height = 512
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgColor = pickBackground(style)
        canvas.drawColor(bgColor)

        val accentColor = pickAccent(style)
        val titlePaint = Paint().apply {
            color = accentColor
            textSize = 24f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val bodyPaint = Paint().apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 16f
            isAntiAlias = true
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

        val shapePaint = Paint()
        shapePaint.color = setAlpha(accentColor, 60)
        shapePaint.style = Paint.Style.FILL
        shapePaint.isAntiAlias = true

        // Decorative shapes in the background
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        for (i in 0..5) {
            canvas.rotate(30f)
            shapePaint.color = setAlpha(accentColor, (20 + i * 8).coerceAtMost(80))
            canvas.drawCircle(0f, 0f, 80f + i * 30f, shapePaint)
        }
        canvas.restore()

        // Title: the prompt at the top
        val lines = wordWrap(prompt, 30)
        var y = 180f
        for (line in lines) {
            canvas.drawText(line, width / 2f, y, titlePaint)
            y += 32f
        }

        // Subtitle
        if (style != "auto") {
            canvas.drawText("[$style style]", width / 2f, y + 16f, bodyPaint)
        }

        // Footer
        bodyPaint.textSize = 12f
        bodyPaint.color = Color.argb(120, 255, 255, 255)
        canvas.drawText("Generated by Meowmba Canvas", width / 2f, height - 24f, bodyPaint)

        val fileName = fileNameFor(prompt, prefix = "canvas")

        return@withContext try {
            workspace.resolve(fileName)?.let { file ->
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                ToolResult.Success("Image saved as \"$fileName\" in the workspace: $prompt\nMEDIA_FILE:${file.absolutePath}")
            } ?: ToolResult.Failure("Could not write image — path is outside the workspace.")
        } catch (e: Exception) {
            ToolResult.Failure("Failed to generate image: ${e.message}")
        } finally {
            bmp.recycle()
        }
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
 * `media.generate_video` — creates a simple animated GIF from frames drawn
 * from a text description. Saves as .gif in the workspace.
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
            "video server if one is set, and falls back to a simple animated-frames " +
            "manifest otherwise. Makes a FILE in the workspace — never for making the " +
            "cat avatar itself move or perform; that is avatar.animate.",
        inputSchema = mapOf("prompt" to "string", "style" to "string", "frames" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        // 30s was inherited from the placeholder-only days, when this tool drew
        // animated frames instantly. Real video generation via RemoteVideoGenerator
        // has its own poll budget of up to 900s (maxPolls=90 * pollIntervalMs=10_000)
        // — a 30s ceiling here would let the orchestrator's own timeout kill the
        // whole tool call, including the placeholder fallback, before a real
        // generation could ever finish or fail cleanly. Same class of bug the
        // image tool's timeoutMs comment already documents.
        //
        // Final-review finding: setting this to *exactly* 900_000 (the poll
        // budget's own delay floor) left zero margin — RemoteVideoGenerator
        // still needs time for the initial POST /generate, all 90 GET /status
        // round trips (not just the sleeps between them), and the final
        // GET /result fetch, plus this tool still needs to run its own
        // fallback path afterward if the remote call times out. 180s of
        // margin (POST/GET overhead is normally seconds, but this box has
        // shown real multi-minute contention-related slowness — see the SDD
        // progress log) plus room for the fallback itself to actually run.
        timeoutMs = 1_080_000
    )

    private companion object {
        const val TAG = "MediaGenerateVideoTool"
    }

    /**
     * Mirrors `MediaGenerateTool.tryGenerators`: never throws, logs failures,
     * and returns null (rather than propagating) so the caller can fall
     * through to the frame-manifest placeholder.
     */
    private suspend fun tryRemote(prompt: String): ByteArray? {
        val generator = remoteGenerator?.invoke() ?: return null
        if (!generator.isAvailable()) return null
        val result = runCatching { generator.generate(prompt, onProgress) }.getOrNull()
        result?.getOrNull()?.let { return it }
        if (result == null) {
            Log.w(TAG, "video backend threw instead of returning a Result")
        } else {
            Log.w(TAG, "video backend failed: ${result.exceptionOrNull()?.message}")
        }
        return null
    }

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val prompt = args["prompt"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'prompt'")
        val style = args["style"]?.takeIf { it.isNotBlank() }?.lowercase() ?: "auto"
        val frameCount = args["frames"]?.toIntOrNull()?.coerceIn(2, 30) ?: 8

        val remoteBytes = tryRemote(prompt)
        if (remoteBytes != null) {
            val safeName = prompt.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").take(40).trim()
                .replace(Regex("\\s+"), "_")
            val fileName = if (safeName.isNotBlank()) "video_${safeName}.mp4" else "video_${System.currentTimeMillis()}.mp4"
            return@withContext try {
                workspace.resolve(fileName)?.let { file ->
                    FileOutputStream(file).use { out -> out.write(remoteBytes) }
                    ToolResult.Success(
                        "Video saved as \"$fileName\" in the workspace: $prompt\nMEDIA_FILE:${file.absolutePath}"
                    )
                } ?: ToolResult.Failure("Could not write video — path is outside the workspace.")
            } catch (e: Exception) {
                ToolResult.Failure("Failed to save the generated video: ${e.message}")
            }
        }

        val width = 256
        val height = 256
        val bgColor = pickBackground(style)
        val accentColor = pickAccent(style)

        // Generate frames as individual PNGs in a temp dir, then bundle as a note
        val frames = mutableListOf<File>()
        val frameDir = java.io.File.createTempFile("vid_", "").apply { delete(); mkdirs() }
        for (i in 0 until frameCount) {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(bgColor)

            val paint = Paint()
            paint.color = setAlpha(accentColor, (60 + i * 10).coerceAtMost(200))
            paint.isAntiAlias = true
            paint.style = Paint.Style.FILL
            val cx = width / 2f + (i * 15f - (frameCount / 2f) * 15f)
            val cy = height / 2f + kotlin.math.sin(i * 0.5).toFloat() * 30f
            canvas.drawCircle(cx, cy, 40f + i * 5f, paint)

            val textPaint = Paint().apply {
                color = Color.argb(200, 255, 255, 255)
                textSize = 14f
                isAntiAlias = true
                typeface = Typeface.DEFAULT
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(prompt.take(20), width / 2f, height - 20f, textPaint)

            val frameFile = File(frameDir, "frame_${"%02d".format(i)}.png")
            FileOutputStream(frameFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            frames.add(frameFile)
            bmp.recycle()
        }

        val safeName = prompt.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").take(40).trim()
            .replace(Regex("\\s+"), "_")
        val baseName = if (safeName.isNotBlank()) "video_${safeName}" else "video_${System.currentTimeMillis()}"
        val noteFile = workspace.resolve("${baseName}_frames.txt")
            ?: return@withContext ToolResult.Failure("Could not write video — path is outside workspace.")
        // Keep one real frame for display before the temp dir is wiped.
        val displayFrame = frames.firstOrNull()?.let { first ->
            workspace.resolve("${baseName}_frame.png")?.also { kept ->
                runCatching { first.copyTo(kept, overwrite = true) }
            }
        }
        noteFile.writeText(
            "Video: $prompt\nStyle: $style\nFrames: ${frames.size}\n" +
            frames.joinToString("\n") { it.absolutePath }
        )
        frameDir.deleteRecursively()

        // MEDIA_FILE points at the kept frame so the browser panel shows
        // something real; the note file keeps the full frame list.
        ToolResult.Success(
            "Video \"$baseName\" ($frameCount frames) created: $prompt\nMEDIA_FILE:${displayFrame?.absolutePath ?: noteFile.absolutePath}"
        )
    }

    private fun pickBackground(s: String): Int = when {
        s.contains("dark") || s.contains("night") -> Color.rgb(13, 17, 23)
        s.contains("light") || s.contains("day") -> Color.rgb(240, 240, 245)
        s.contains("ocean") || s.contains("sea") || s.contains("water") -> Color.rgb(10, 30, 50)
        s.contains("forest") || s.contains("nature") || s.contains("green") -> Color.rgb(20, 40, 20)
        s.contains("sunset") || s.contains("warm") -> Color.rgb(40, 20, 10)
        s.contains("neon") || s.contains("cyber") -> Color.rgb(10, 10, 30)
        else -> Color.rgb(22, 27, 34)
    }
    private fun pickAccent(s: String): Int = when {
        s.contains("ocean") || s.contains("sea") || s.contains("water") -> Color.rgb(88, 166, 255)
        s.contains("forest") || s.contains("nature") || s.contains("green") -> Color.rgb(63, 185, 80)
        s.contains("sunset") || s.contains("warm") -> Color.rgb(255, 160, 60)
        s.contains("neon") || s.contains("cyber") -> Color.rgb(188, 140, 255)
        s.contains("red") || s.contains("love") || s.contains("heart") -> Color.rgb(248, 81, 73)
        s.contains("dark") || s.contains("night") -> Color.rgb(88, 166, 255)
        s.contains("light") || s.contains("day") -> Color.rgb(30, 100, 200)
        else -> Color.rgb(88, 166, 255)
    }
    private fun setAlpha(color: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)
}

/**
 * `media.generate_audio` — routes to real AI music generation (a
 * user-configured remote server) when available, and falls back to a
 * simple procedural WAV tone sequence (sine waves) when no backend is
 * configured or generation fails. The companion uses it when the user asks
 * to create music or a sound.
 *
 * Note on file naming: the remote backend (meow-music/ACE-Step) returns WAV
 * audio on the currently deployed host (ffmpeg is unavailable there, so it
 * can't transcode to MP3), the same container the tone-generator fallback
 * already writes via `writeWav`. Both paths therefore produce `audio_*.wav`
 * — unlike `media.generate_image`'s `image_*` vs `canvas_*` split, the
 * extension alone can't tell you which backend produced a given file here.
 * They're still distinguishable by content (real generated music vs. a
 * procedural tone sequence) if that ever matters.
 */
class MediaGenerateAudioTool(
    private val workspace: Workspace,
    /** Supplies the remote generator. Null unless the user configured a server URL. */
    private val remoteGenerator: (() -> com.example.meowmba.core.media.MusicGenerator?)? = null
) : Tool {
    override val definition = ToolDefinition(
        id = "media.generate_audio",
        displayName = "Generate audio",
        description = "Create a WAV audio file from a text description. Tries the " +
            "configured music server if one is set, and falls back to a simple " +
            "procedural tone sequence otherwise.",
        inputSchema = mapOf("prompt" to "string", "duration_sec" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        // 30s was inherited from the tone-generator-only days, when this tool
        // returned instantly. RemoteMusicGenerator's own poll budget alone is
        // up to 120s (maxPolls=60 * pollIntervalMs=2000ms), plus its HTTP
        // timeouts on the initial /generate and final /result calls (up to
        // 30s each) — a 30s ceiling here means the orchestrator's own
        // withTimeoutOrNull kills the whole execute() call, including the
        // tone-generator fallback, before the remote path can ever finish or
        // fail cleanly. Same class of bug the image tool hit first (see its
        // timeoutMs comment) — mirrored here with the same fix shape.
        timeoutMs = 200_000
    )

    private companion object {
        const val TAG = "MediaGenerateAudioTool"
    }

    /**
     * Mirrors `MediaGenerateTool.tryGenerators`: never throws, logs failures,
     * and returns null (rather than propagating) so the caller can fall
     * through to the procedural tone generator.
     */
    private suspend fun tryRemote(prompt: String, durationSec: Int): ByteArray? {
        val generator = remoteGenerator?.invoke() ?: return null
        if (!generator.isAvailable()) return null
        val result = runCatching { generator.generate(prompt, durationSec) }.getOrNull()
        result?.getOrNull()?.let { return it }
        if (result == null) {
            Log.w(TAG, "music backend threw instead of returning a Result")
        } else {
            Log.w(TAG, "music backend failed: ${result.exceptionOrNull()?.message}")
        }
        return null
    }

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val prompt = args["prompt"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'prompt'")
        val durationSec = args["duration_sec"]?.toIntOrNull()?.coerceIn(1, 30) ?: 5

        val remoteBytes = tryRemote(prompt, durationSec)
        if (remoteBytes != null) {
            val safeName = prompt.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").take(40).trim()
                .replace(Regex("\\s+"), "_")
            val fileName = if (safeName.isNotBlank()) "audio_${safeName}.wav" else "audio_${System.currentTimeMillis()}.wav"
            return@withContext try {
                workspace.resolve(fileName)?.let { file ->
                    FileOutputStream(file).use { out -> out.write(remoteBytes) }
                    ToolResult.Success(
                        "Audio saved as \"$fileName\" ($durationSec sec) in the workspace: $prompt\nMEDIA_FILE:${file.absolutePath}"
                    )
                } ?: ToolResult.Failure("Could not write audio — path is outside workspace.")
            } catch (e: Exception) {
                ToolResult.Failure("Failed to save the generated audio: ${e.message}")
            }
        }

        val sampleRate = 22050
        val numSamples = sampleRate * durationSec
        val samples = ShortArray(numSamples)

        // Map prompt to a simple tone sequence
        val baseFreq = 220 + (prompt.length % 10) * 40f
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val freq = baseFreq + kotlin.math.sin(t * 2.0) * 50f
            val envelope = kotlin.math.min(1.0, kotlin.math.min(t * 10.0, (durationSec - t) * 10.0))
            val sample = (kotlin.math.sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * envelope * 0.3).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val safeName = prompt.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").take(40).trim()
            .replace(Regex("\\s+"), "_")
        val fileName = if (safeName.isNotBlank()) "audio_${safeName}.wav" else "audio_${System.currentTimeMillis()}.wav"

        return@withContext try {
            workspace.resolve(fileName)?.let { file ->
                writeWav(file, sampleRate, samples)
                ToolResult.Success(
                    "Audio saved as \"$fileName\" ($durationSec sec) in the workspace: $prompt\nMEDIA_FILE:${file.absolutePath}"
                )
            } ?: ToolResult.Failure("Could not write audio — path is outside workspace.")
        } catch (e: Exception) {
            ToolResult.Failure("Failed to generate audio: ${e.message}")
        }
    }

    private fun writeWav(file: File, sampleRate: Int, samples: ShortArray) {
        FileOutputStream(file).use { out ->
            val dataSize = samples.size * 2
            val fileSize = 44 + dataSize
            out.write("RIFF".toByteArray())
            out.write(intToBytes(fileSize - 8))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intToBytes(16))           // chunk size
            out.write(shortToBytes(1))           // PCM
            out.write(shortToBytes(1))           // mono
            out.write(intToBytes(sampleRate))
            out.write(intToBytes(sampleRate * 2))
            out.write(shortToBytes(2))           // block align
            out.write(shortToBytes(16))          // bits per sample
            out.write("data".toByteArray())
            out.write(intToBytes(dataSize))
            val buf = ByteArray(dataSize)
            for (i in samples.indices) {
                val v = samples[i].toInt()
                buf[i * 2] = (v and 0xFF).toByte()
                buf[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }
            out.write(buf)
        }
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortToBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )
}
