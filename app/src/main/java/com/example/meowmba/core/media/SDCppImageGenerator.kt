package com.example.meowmba.core.media

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device text-to-image via stable-diffusion.cpp, replacing the
 * deprecated MediaPipe/SD1.5 path. See
 * docs/superpowers/specs/2026-08-03-native-image-music-video-design.md.
 */
class SDCppImageGenerator internal constructor(
    private val modelPath: String,
    private val native: SDCppNative
) : ImageGenerator {

    constructor(modelPath: String) : this(modelPath, SDCppJniNative)

    override fun isAvailable(): Boolean = File(modelPath).isFile

    override suspend fun generate(
        prompt: String,
        iterations: Int,
        seed: Int
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(
                IllegalStateException("No image model installed at $modelPath")
            )
        }
        try {
            val ctxPtr = native.createContext(modelPath, Runtime.getRuntime().availableProcessors())
            if (ctxPtr == 0L) {
                return@withContext Result.failure(
                    IllegalStateException("stable-diffusion.cpp failed to load the model")
                )
            }
            try {
                val dims = IntArray(3)
                val bytes = native.generate(
                    ctxPtr, prompt, width = 512, height = 512,
                    steps = iterations, seed = seed.toLong(), outDims = dims
                ) ?: return@withContext Result.failure(
                    IllegalStateException("stable-diffusion.cpp generation returned no image")
                )
                val (width, height, channel) = Triple(dims[0], dims[1], dims[2])
                val bitmap = bytesToBitmap(bytes, width, height, channel)
                Result.success(bitmap)
            } finally {
                native.freeContext(ctxPtr)
            }
        } catch (e: Throwable) {
            // Throwable, not Exception: a native crash (UnsatisfiedLinkError, etc.)
            // must not take the app down mid-conversation.
            Result.failure(e)
        }
    }

    private fun bytesToBitmap(bytes: ByteArray, width: Int, height: Int, channel: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val base = i * channel
            val r = bytes[base].toInt() and 0xFF
            val g = bytes[base + 1].toInt() and 0xFF
            val b = bytes[base + 2].toInt() and 0xFF
            val a = if (channel >= 4) bytes[base + 3].toInt() and 0xFF else 255
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
