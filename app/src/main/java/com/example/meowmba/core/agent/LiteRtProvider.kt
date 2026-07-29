package com.example.meowmba.core.agent

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class LiteRtProvider(
    private val context: Context,
    private val modelPath: String
) : ModelProvider {

    override val name: String = "LiteRT (On-device)"

    private val engine: Engine by lazy {
        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.path,
        )
        Engine(config).also { it.initialize() }
    }

    /**
     * Streams a response, and — critically — never destroys the native conversation
     * while generation is still running.
     *
     * The previous version wrapped the collect in `Conversation.use { }`. When the
     * consumer cancelled mid-answer (barge-in, leaving the screen, scope teardown),
     * `use` closed the native object while the LiteRT callback thread was still
     * delivering tokens into it. That is a use-after-free, and it crashed the whole
     * process:
     *
     *   Fatal signal 11 (SIGSEGV), fault addr 0x0 in tid N (callback_thread)
     *     #00 liblitertlm_jni.so
     *
     * `cancelProcess()` is the supported way to stop a run: it ends generation but
     * leaves the object valid, so closing afterwards is safe. Teardown runs
     * NonCancellable so that ordering holds even when the caller is already being
     * cancelled — otherwise the cleanup itself gets cancelled halfway and we are back
     * to closing under a live callback.
     *
     * Cancellation is still honoured: the flow stops emitting and rethrows, so
     * barge-in continues to interrupt the model.
     */
    override fun generateStreaming(prompt: String): Flow<ModelResponse> = flow {
        if (!File(modelPath).exists()) {
            emit(ModelResponse.Error("Model file not found at $modelPath"))
            return@flow
        }

        val config = ConversationConfig(
            systemInstruction = Contents.of(
                "You are a helpful assistant called Meowmba. Be concise."
            ),
            samplerConfig = SamplerConfig(
                topK = 40,
                topP = 0.95,
                temperature = 0.8,
            ),
        )

        val conversation = try {
            engine.createConversation(config)
        } catch (e: Exception) {
            emit(ModelResponse.Error(e.message ?: "LiteRT could not start a conversation"))
            return@flow
        }

        var fullText = ""
        try {
            conversation.sendMessageAsync(prompt).collect { message ->
                val text = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                val delta = if (text.startsWith(fullText)) {
                    text.removePrefix(fullText)
                } else {
                    text
                }
                fullText = text
                if (delta.isNotEmpty()) {
                    emit(ModelResponse.Partial(delta))
                }
            }
            emit(ModelResponse.Complete(fullText))
        } catch (e: CancellationException) {
            throw e                     // cleaned up in finally; keep cancellation semantics
        } catch (e: Exception) {
            emit(ModelResponse.Error(e.message ?: "LiteRT inference error"))
        } finally {
            withContext(NonCancellable) {
                // Order matters: stop the run first, THEN release the object.
                runCatching { conversation.cancelProcess() }
                runCatching { conversation.close() }
            }
        }
    }

    override suspend fun generate(prompt: String): ModelResponse {
        val config = ConversationConfig(
            systemInstruction = Contents.of(
                "You are a helpful assistant called Meowmba. Be concise."
            ),
        )
        val conversation = try {
            engine.createConversation(config)
        } catch (e: Exception) {
            return ModelResponse.Error(e.message ?: "LiteRT could not start a conversation")
        }
        return try {
            val message = conversation.sendMessage(prompt)
            val text = message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
            ModelResponse.Complete(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ModelResponse.Error(e.message ?: "LiteRT inference error")
        } finally {
            // Same teardown discipline as the streaming path: sendMessage is blocking,
            // but cancelling the calling coroutine can still land here with native work
            // in flight.
            withContext(NonCancellable) {
                runCatching { conversation.cancelProcess() }
                runCatching { conversation.close() }
            }
        }
    }

    fun isModelAvailable(): Boolean = File(modelPath).exists()

    fun close() {
        runCatching { engine.close() }
    }
}
