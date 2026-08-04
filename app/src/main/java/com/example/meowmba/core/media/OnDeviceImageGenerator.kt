package com.example.meowmba.core.media

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator as MediaPipeImageGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device text-to-image, using MediaPipe's Image Generator
 * (Stable Diffusion 1.5 architecture). Nothing leaves the phone.
 *
 * This exists because `MediaGenerateTool` could only draw the prompt as text
 * on a canvas — honest, but not what anyone means by "generate an image".
 *
 * Two things to know about the underlying task:
 *  - Google lists the MediaPipe Image Generator as no longer actively
 *    maintained. It works and is stable, but do not expect new versions.
 *  - The weights are a *directory* of converted .tflite bins, not a single
 *    file, and they are not bundled — the user supplies the path. See the
 *    README section "On-device image generation" for the conversion steps.
 *
 * Memory: SD 1.5 wants roughly 1.5–2 GB while resident. The generator is
 * therefore created per request and closed immediately after, so the LLM
 * keeps its RAM between images rather than the two fighting over the heap.
 */
class OnDeviceImageGenerator(
    private val context: Context,
    private val modelDirectory: String
) : ImageGenerator {

    /**
     * The task needs a directory of converted bins. Checking for the directory
     * alone is not enough — an empty folder produces a confusing native crash
     * rather than a clean error, so require it to be non-empty.
     */
    override fun isAvailable(): Boolean {
        val dir = File(modelDirectory)
        return dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }

    /**
     * Generate one image. Returns null when the model isn't installed, so the
     * caller can fall back to the canvas renderer instead of failing outright.
     *
     * @param iterations denoising steps. 20 is the usual quality/speed knob;
     *   fewer is faster and blurrier. Expect ~15s on a flagship, well over a
     *   minute on midrange hardware.
     */
    override suspend fun generate(
        prompt: String,
        iterations: Int,
        seed: Int
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(
                IllegalStateException("No image model installed at $modelDirectory")
            )
        }

        var generator: MediaPipeImageGenerator? = null
        return@withContext try {
            val options = MediaPipeImageGenerator.ImageGeneratorOptions.builder()
                .setImageGeneratorModelDirectory(modelDirectory)
                .build()
            generator = MediaPipeImageGenerator.createFromOptions(context, options)

            val result = generator.generate(prompt, iterations, seed)
                ?: return@withContext Result.failure(
                    IllegalStateException("Image generation returned no result")
                )

            // Copy out of the MPImage before the generator is closed underneath it.
            val bitmap = BitmapExtractor.extract(result.generatedImage())
                .copy(Bitmap.Config.ARGB_8888, false)
            Result.success(bitmap)
        } catch (e: Throwable) {
            // Throwable, not Exception: a missing/mismatched native model
            // surfaces as UnsatisfiedLinkError or Error, and that must not
            // take the app down mid-conversation.
            Result.failure(e)
        } finally {
            runCatching { generator?.close() }
        }
    }
}
