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
import kotlinx.coroutines.Dispatchers
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
        // GPU first: on device, Gemma E2B streamed tokens far too slowly on
        // CPU for voice (20s+ before TTS started), while the LiteRT runtime
        // reports a working GPU accelerator. If the GPU delegate fails to
        // initialize, fall back to CPU instead of killing the local route.
        runCatching {
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    cacheDir = context.cacheDir.path,
                )
            ).also { it.initialize() }
        }.getOrElse {
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.path,
                )
            ).also { it.initialize() }
        }
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
    /** Pre-load the native engine so the first real question doesn't pay the
     *  multi-second model load mid-conversation (measured on device: an 11.8s
     *  dead gap between hearing the user and starting to think). */
    fun warmUp() {
        runCatching { engine }
    }

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
            // Engine init + createConversation are multi-second native calls
            // (measured on device: 11.8s cold), and under a racing background
            // warmUp() the `by lazy` gate can hold the main thread for 6s+
            // ("Long monitor contention ... SynchronizedLazyImpl") and freeze the
            // whole app. Creating the conversation OFF the collecting dispatcher
            // keeps that block off the UI thread. This block never calls emit, so
            // hopping dispatchers here does not violate the Flow emission-context
            // invariant (which the app's MeowFU guard surfaces and would otherwise
            // abort the whole answer).
            withContext(Dispatchers.Default) {
                engine.createConversation(config)
            }
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
                // litertlm 0.13.1 has no maxTokens, and uncapped Gemma rambles
                // for half a minute of TTS. Voice answers stay conversational:
                // stop generating after a few complete sentences.
                if (SENTENCES.findAll(fullText).count() >= MAX_REPLY_SENTENCES) {
                    throw ReplyCapException()
                }
            }
            emit(ModelResponse.Complete(fullText))
        } catch (_: ReplyCapException) {
            emit(ModelResponse.Complete(trimToSentenceBoundary(fullText)))
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

    private class ReplyCapException : RuntimeException()

    private companion object {
        /** Voice replies stay conversational; uncapped Gemma rambles. */
        val MAX_REPLY_SENTENCES = 3
        val SENTENCES = Regex("""(?<=[.!?])\s+""")
    }

    /** Cut a capped reply at the last complete sentence, dropping the tail. */
    private fun trimToSentenceBoundary(text: String): String {
        val parts = SENTENCES.split(text.trim()).filter { it.isNotBlank() }
        return parts.take(MAX_REPLY_SENTENCES).joinToString(" ").ifBlank { text }
    }

    override suspend fun generate(prompt: String): ModelResponse =
        // Suspends the whole (value-returning, non-flow) call off the main thread.
        // Engine init can block for seconds (see generateStreaming); unlike a flow,
        // a suspend value call has no emission-context invariant to violate.
        withContext(Dispatchers.Default) { generateOffMain(prompt) }

    private suspend fun generateOffMain(prompt: String): ModelResponse {
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
