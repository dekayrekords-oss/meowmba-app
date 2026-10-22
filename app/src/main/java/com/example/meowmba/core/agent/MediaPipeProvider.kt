package com.example.meowmba.core.agent

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor

/** MediaPipe 0.10.35 with an explicit cancellable session for each response. */
class MediaPipeProvider(
    private val context: Context,
    private val modelPath: String,
    private val maxTokens: Int = 512,
) : ModelProvider {
    override val name: String = "Gemma (Local)"
    private val modelKey = "mediapipe:$maxTokens:${File(modelPath).absolutePath}"
    val isLoaded: Boolean get() = LocalInferenceRuntime.models.isLoaded(modelKey)
    fun idleMs(nowMs: Long = System.currentTimeMillis()): Long = LocalInferenceRuntime.models.idleMs(modelKey, nowMs)

    private fun createEngine(): LlmInference {
        require(File(modelPath).extension.lowercase() in setOf("task", "bin")) {
            "${File(modelPath).name} is not a chat model for MediaPipe. Select a compatible model in Your AI setup."
        }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath).setMaxTokens(maxTokens).build()
        return LlmInference.createFromOptions(context, options)
    }

    /** A released provider can safely reload; it never retains a closed lazy engine. */
    suspend fun release(): Boolean = withContext(LocalInferenceRuntime.dispatcher) {
        LocalInferenceRuntime.models.releaseWhenIdle(modelKey)
    }

    override fun generateStreaming(prompt: String): Flow<ModelResponse> = flow {
        if (!File(modelPath).exists()) {
            emit(ModelResponse.Error("Model file not found at $modelPath"))
            return@flow
        }
        try {
            LocalInferenceRuntime.models.withModel(modelKey, ::createEngine) { engine ->
                NativeTextGeneration.stream {
                    val session = LlmInferenceSession.createFromOptions(engine,
                        LlmInferenceSession.LlmInferenceSessionOptions.builder().build())
                    object : NativeTextSession {
                        override fun start(callback: NativeTextCallback) {
                            session.addQueryChunk(ChatTemplate.wrap(modelPath, prompt))
                            val future = session.generateResponseAsync { partial, done ->
                                // Future completion precedes the last progress callback.
                                // That authoritative full result includes the final delta.
                                if (!done) callback.partial(partial)
                            }
                            future.addListener({
                                try { callback.complete(future.get()) }
                                catch (error: Exception) { callback.error(error.cause ?: error) }
                            }, Executor { it.run() })
                        }
                        override fun cancel() = session.cancelGenerateResponseAsync()
                        override fun close() = session.close()
                    }
                }.collect { emit(it) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { emit(ModelResponse.Error(error.message ?: "MediaPipe inference failed")) }
    }.flowOn(LocalInferenceRuntime.dispatcher)

    /** The value-returning path uses the same native cancellation as streaming. */
    override suspend fun generate(prompt: String): ModelResponse {
        var result: ModelResponse = ModelResponse.Error("No model response")
        generateStreaming(prompt).collect {
            if (it is ModelResponse.Complete || it is ModelResponse.Error) result = it
        }
        return result
    }

    /** Explicit warm-up shares the generation lease; no eager route-build work. */
    suspend fun warmUp(): Result<Unit> = withContext(LocalInferenceRuntime.dispatcher) {
        try {
            LocalInferenceRuntime.models.withModel(modelKey, ::createEngine) { }
            Result.success(Unit)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }

    fun isModelAvailable(): Boolean = File(modelPath).exists()
}
