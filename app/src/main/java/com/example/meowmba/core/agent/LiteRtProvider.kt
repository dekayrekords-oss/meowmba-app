package com.example.meowmba.core.agent

import android.app.ActivityManager
import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class LiteRtProvider(
    private val context: Context,
    private val modelPath: String,
) : ModelProvider {
    override val name: String = "LiteRT (On-device)"
    private val modelKey = "litert:${File(modelPath).absolutePath}"

    private fun buildEngine(): Engine {
        // Keep the existing GPU-first, CPU-fallback policy and all native thread
        // settings. The shared lease prevents a warm-up racing a generation.
        android.util.Log.i("LiteRt", "loading on-device brain: $modelPath")
        fun initialized(backend: Backend): Engine {
            val candidate = Engine(EngineConfig(modelPath = modelPath, backend = backend, cacheDir = context.cacheDir.path))
            try { candidate.initialize(); return candidate }
            catch (error: Exception) {
                if (candidate.isInitialized()) runCatching { candidate.close() }
                throw error
            }
        }
        return try { initialized(Backend.GPU()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { initialized(Backend.CPU()) }
    }

    val isLoaded: Boolean get() = LocalInferenceRuntime.models.isLoaded(modelKey)

    /** Uses shared completion time, so rebuilding a wrapper cannot fake ten minutes of idleness. */
    fun idleMs(nowMs: Long = System.currentTimeMillis()): Long = LocalInferenceRuntime.models.idleMs(modelKey, nowMs)

    /** Existing synchronous API for worker callers; UI uses LocalInferenceRuntime.releaseIdle. */
    fun release(): Boolean = runCatching { LocalInferenceRuntime.models.releaseIfIdle(modelKey) }.getOrDefault(false)

    fun memoryAllowsWarmUp(minFreeMb: Long = 1_500L): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return true
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return !info.lowMemory && info.availMem >= minFreeMb * 1024L * 1024L
    }

    /** Explicit preloading only. Runs off Main under the same lease as real inference. */
    fun warmUp() {
        if (!memoryAllowsWarmUp()) return
        LocalInferenceRuntime.warmUp(modelKey, ::buildEngine)
    }

    override fun generateStreaming(prompt: String): Flow<ModelResponse> = stream(prompt, limitSentences = true)

    private fun stream(
        prompt: String,
        limitSentences: Boolean,
        jsonSchema: String? = null,
        useDefaultConfig: Boolean = false,
    ): Flow<ModelResponse> = flow {
        if (!File(modelPath).exists()) {
            emit(ModelResponse.Error("Model file not found at $modelPath"))
            return@flow
        }
        // Preserve prior defaults for value-returning generate(), and the existing
        // chat/structured settings exactly. Cancellation is not a token/quality cap.
        val config = if (useDefaultConfig) ConversationConfig(
            systemInstruction = Contents.of("You are a helpful assistant called Meowmba. Be concise."),
        ) else ConversationConfig(
            systemInstruction = Contents.of("You are a helpful assistant called Meowmba. Be concise."),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = if (limitSentences) 0.8 else 0.1),
            automaticToolCalling = false,
            maxOutputToken = if (limitSentences) 512 else 1536,
            enableResponseFormat = jsonSchema != null,
            thinkingConfig = if (limitSentences) null else com.google.ai.edge.litertlm.ThinkingConfig(enableThinking = false),
        )
        try {
            LocalInferenceRuntime.models.withModel(modelKey, ::buildEngine) { engine ->
                val accumulated = ModelTextAccumulator()
                var fullText = ""
                try {
                    NativeTextGeneration.stream {
                        // No dispatcher return boundary between creating this native
                        // handle and assigning ownership to its finally-protected session.
                        val conversation = engine.createConversation(config)
                        object : NativeTextSession {
                            override fun start(callback: NativeTextCallback) {
                                conversation.sendMessageAsync(prompt, object : MessageCallback {
                                    override fun onMessage(message: Message) {
                                        callback.partial(message.contents.contents.filterIsInstance<Content.Text>()
                                            .joinToString("") { it.text })
                                    }
                                    override fun onDone() = callback.complete()
                                    override fun onError(throwable: Throwable) = callback.error(throwable)
                                }, responseFormat = jsonSchema?.let { com.google.ai.edge.litertlm.ResponseFormat.json(it) })
                            }
                            override fun cancel() = conversation.cancelProcess()
                            override fun close() = conversation.close()
                        }
                    }.collect { response ->
                        if (response is ModelResponse.Partial) {
                            fullText = accumulated.append(response.text)
                            emit(response)
                            // This is the existing conversational sentence cap.
                            if (limitSentences && SENTENCES.findAll(fullText).count() >= MAX_REPLY_SENTENCES) {
                                throw ReplyCapException()
                            }
                        } else emit(response)
                    }
                } catch (_: ReplyCapException) {
                    // Cancelling the nested stream has already stopped native work,
                    // waited for acknowledgement and closed its conversation.
                    emit(ModelResponse.Complete(trimToSentenceBoundary(fullText)))
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { emit(ModelResponse.Error(error.message ?: "LiteRT inference error")) }
    }.flowOn(LocalInferenceRuntime.dispatcher)

    private class ReplyCapException : RuntimeException()

    /** JSON/tool decisions retain their separate output budget and constrained response format. */
    suspend fun generateStructured(prompt: String, jsonSchema: String = "{\"type\":\"object\"}"): ModelResponse {
        var result: ModelResponse = ModelResponse.Error("No model response")
        stream(prompt, limitSentences = false, jsonSchema = jsonSchema).collect {
            if (it is ModelResponse.Complete || it is ModelResponse.Error) result = it
        }
        return result
    }

    /** Blocking sendMessage used to continue native generation after coroutine cancellation. */
    override suspend fun generate(prompt: String): ModelResponse {
        var result: ModelResponse = ModelResponse.Error("No model response")
        stream(prompt, limitSentences = false, useDefaultConfig = true).collect {
            if (it is ModelResponse.Complete || it is ModelResponse.Error) result = it
        }
        return result
    }

    private fun trimToSentenceBoundary(text: String): String =
        SENTENCES.split(text.trim()).filter { it.isNotBlank() }.take(MAX_REPLY_SENTENCES).joinToString(" ").ifBlank { text }

    fun isModelAvailable(): Boolean = File(modelPath).exists()
    fun close() { release() }

    private companion object {
        const val MAX_REPLY_SENTENCES = 3
        val SENTENCES = Regex("""(?<=[.!?])\s+""")
    }
}
