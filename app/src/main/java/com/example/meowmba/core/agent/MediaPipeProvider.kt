package com.example.meowmba.core.agent

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

/**
 * Implementation of ModelProvider using MediaPipe LLM Inference (On-device).
 */
class MediaPipeProvider(
    private val context: Context,
    private val modelPath: String
) : ModelProvider {

    override val name: String = "Gemma (Local)"

    private val llmInference: LlmInference by lazy {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(512)
            .build()
        LlmInference.createFromOptions(context, options)
    }

    override fun generateStreaming(prompt: String): Flow<ModelResponse> = callbackFlow {
        if (!File(modelPath).exists()) {
            trySend(ModelResponse.Error("Model file not found at $modelPath"))
            close()
            return@callbackFlow
        }

        // Instruct models leak template tokens / drift on a raw flat prompt —
        // wrap in the model's native chat format (Qwen/Hermes: ChatML, Gemma: turns).
        val templatedPrompt = ChatTemplate.wrap(modelPath, prompt)
        try {
            llmInference.generateResponseAsync(templatedPrompt) { partialResult, done ->
                trySend(ModelResponse.Partial(partialResult))
                if (done) {
                    trySend(ModelResponse.Complete(partialResult))
                    close()
                }
            }
        } catch (e: Exception) {
            trySend(ModelResponse.Error(e.message ?: "Inference error"))
            close()
        }
        awaitClose { /* Cleanup if needed */ }
    }

    override suspend fun generate(prompt: String): ModelResponse {
        return try {
            val response = llmInference.generateResponse(ChatTemplate.wrap(modelPath, prompt))
            ModelResponse.Complete(response)
        } catch (e: Exception) {
            ModelResponse.Error(e.message ?: "Inference error")
        }
    }

    /** Honest availability check for the Hardware page's local-model test. */
    fun isModelAvailable(): Boolean = File(modelPath).exists()
}
