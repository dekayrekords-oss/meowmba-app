package com.example.meowmba.core.agent

import android.content.Context
import android.os.Build
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Wraps Google's on-device Gemini Nano via AICore as a [ModelProvider].
 *
 * Requirements:
 * - Device running Android 15+ with AICore system service
 * - `com.google.android.ai.core` package present
 * - Gemini Nano model downloaded (AICore manages this)
 *
 * Guard with [isAvailable] before creating an instance.
 */
class GeminiNanoProvider private constructor(
    private val model: GenerativeModel
) : ModelProvider, AutoCloseable {

    override val name = "Gemini Nano (AICore)"

    override fun generateStreaming(prompt: String): Flow<ModelResponse> = callbackFlow {
        try {
            model.generateContentStream(prompt).collect { response ->
                response.text?.let { trySend(ModelResponse.Partial(it)) }
            }
            close()
        } catch (e: Exception) {
            trySend(ModelResponse.Error(e.message ?: "Gemini Nano streaming error"))
            close()
        }
    }

    override suspend fun generate(prompt: String): ModelResponse = withContext(Dispatchers.IO) {
        try {
            val response = model.generateContent(prompt)
            ModelResponse.Complete(response.text ?: "")
        } catch (e: Exception) {
            ModelResponse.Error(e.message ?: "Gemini Nano error")
        }
    }

    override fun close() {
        model.close()
    }

    companion object {
        /** True when this device can run Gemini Nano via AICore. */
        fun isAvailable(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 31) return false
            return try {
                context.packageManager.getPackageInfo("com.google.android.ai.core", 0)
                true
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Create a [GeminiNanoProvider] or null when AICore isn't available.
         * Call [isAvailable] first, or rely on this returning null.
         */
        suspend fun create(context: Context): GeminiNanoProvider? {
            if (!isAvailable(context)) return null
            return withContext(Dispatchers.IO) {
                try {
                    val genConfig = generationConfig {
                        this.context = context
                        temperature = 0.7f
                        maxOutputTokens = 512
                    }
                    val model = GenerativeModel(generationConfig = genConfig)
                    model.prepareInferenceEngine()
                    GeminiNanoProvider(model)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
