package com.example.meowmba.core.vision

import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DeviceVisionProvider : VisionProvider {
    override val name = "On-device vision"

    override suspend fun describe(jpegBytes: ByteArray, prompt: String): Result<String> {
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: return Result.failure(Exception("Could not decode camera frame"))
        val image = InputImage.fromBitmap(bitmap, 0)

        return withContext(Dispatchers.IO) {
            try {
                val labels = runCatching {
                    val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                    try {
                        Tasks.await(labeler.process(image), 30, TimeUnit.SECONDS)
                    } finally {
                        labeler.close()
                    }
                }.getOrDefault(emptyList())

                val textResult = runCatching {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        Tasks.await(recognizer.process(image), 30, TimeUnit.SECONDS)
                    } finally {
                        recognizer.close()
                    }
                }.getOrNull()

                val labelWords = labels
                    .sortedByDescending { it.confidence }
                    .take(5)
                    .joinToString { it.text.lowercase() }

                val extractedText = textResult?.text?.trim()

                val sb = StringBuilder()
                if (labelWords.isNotEmpty()) {
                    sb.append("I see $labelWords.")
                }
                if (!extractedText.isNullOrBlank()) {
                    if (sb.isNotEmpty()) sb.append(" ")
                    val clean = extractedText.replace('\n', ' ').replace(Regex("\\s+"), " ")
                    sb.append("The text reads: \"$clean\".")
                }

                val description = sb.toString()
                if (description.isBlank()) {
                    Result.failure(Exception("Couldn't make out anything in this image"))
                } else {
                    Result.success(description)
                }
            } catch (e: Exception) {
                Result.failure(Exception("on-device vision failed: ${e.message}"))
            }
        }
    }
}
