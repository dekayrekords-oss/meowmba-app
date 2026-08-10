package com.example.meowmba.core.agent

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Implementation of ModelProvider using MediaPipe LLM Inference (On-device).
 */
class MediaPipeProvider(
    private val context: Context,
    private val modelPath: String
) : ModelProvider {

    override val name: String = "Gemma (Local)"

    private companion object {
        /**
         * One thread for all on-device inference, for two reasons.
         *
         * It must not be the main thread: creating the engine memory-maps a
         * multi-gigabyte model and `generateResponse` blocks until the whole
         * completion is decoded. Neither had a dispatcher before, so both ran
         * on whatever the caller was on — usually Main — which froze the UI,
         * the avatar, and (because the speech recognizer's callbacks are
         * main-thread) the microphone and wake word for the length of the
         * turn. That is exactly the reported "the model is holding up the next
         * call and the wake word stops working".
         *
         * And it must be exactly one thread: LlmInference holds a single
         * native session, so two concurrent generations corrupt each other.
         * Serializing here means a second request waits instead of racing.
         */
        private val inferenceDispatcher: CoroutineDispatcher =
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "meow-llm").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY - 1
                }
            }.asCoroutineDispatcher()
    }

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
        // Partial tokens are droppable under backpressure; the terminal
        // Complete/Error is not — losing it leaves the turn hanging in
        // THINKING forever, and the mic only re-arms from IDLE. Keep the
        // newest and never block the native callback thread.
        .buffer(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .flowOn(inferenceDispatcher)

    override suspend fun generate(prompt: String): ModelResponse =
        withContext(inferenceDispatcher) {
            try {
                val response = llmInference.generateResponse(ChatTemplate.wrap(modelPath, prompt))
                ModelResponse.Complete(response)
            } catch (e: Exception) {
                ModelResponse.Error(e.message ?: "Inference error")
            }
        }

    /**
     * Load the model now, off the main thread, so the first prompt does not pay
     * for it. Safe to call repeatedly — `lazy` collapses to a no-op.
     */
    suspend fun warmUp(): Result<Unit> = withContext(inferenceDispatcher) {
        runCatching { llmInference; Unit }
    }

    /** Honest availability check for the Hardware page's local-model test. */
    fun isModelAvailable(): Boolean = File(modelPath).exists()
}
