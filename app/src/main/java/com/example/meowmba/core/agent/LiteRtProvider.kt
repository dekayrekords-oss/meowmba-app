package com.example.meowmba.core.agent

import android.app.ActivityManager
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

    // The shared engine registry and the single lock that guards it live in the
    // companion object below. A *per-instance* lock would let two provider
    // instances (route rebuilds create a fresh one each time) race on the
    // process-wide map and leak a resident 2.6GB model — so every map access
    // must serialize on one companion-level lock.
    @Volatile private var lastUsedAtMs = 0L

    /**
     * The resident model is the single biggest thing this app owns: the
     * Gemma E2B bundle is 2.6 GB on disk and lands as ~2 GB of resident GPU +
     * native memory, which is enough to put the whole phone into swap (measured:
     * PSS 2.28 GB, kswapd0 pinned, load ~12, 65% janky frames in the launcher).
     * So the engine is held, but only while it is being used — [release] hands
     * that memory back and the next question pays the load again.
     */
    private fun engine(): Engine {
        engines[modelPath]?.let { return it }
        // Single companion lock: re-check after acquiring it (another instance
        // may have built this model while we waited) and build at most once.
        return synchronized(engineLock) {
            engines[modelPath]?.let { return it }
            buildEngine().also { engines[modelPath] = it }
        }
    }

    private fun buildEngine(): Engine {
        // GPU first: on device, Gemma E2B streamed tokens far too slowly on
        // CPU for voice (20s+ before TTS started), while the LiteRT runtime
        // reports a working GPU accelerator. If the GPU delegate fails to
        // initialize, fall back to CPU instead of killing the local route.
        android.util.Log.i("LiteRt", "loading on-device brain: $modelPath")
        return runCatching {
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

    /** True when the weights for this model are resident right now. */
    val isLoaded: Boolean get() = engines.containsKey(modelPath)

    /** Milliseconds since the last generation, or Long.MAX_VALUE if unused. */
    fun idleMs(nowMs: Long = System.currentTimeMillis()): Long =
        if (lastUsedAtMs == 0L) Long.MAX_VALUE else nowMs - lastUsedAtMs

    /**
     * Free the resident engine. Returns true if something was actually
     * released. Safe to call when nothing is loaded.
     */
    fun release(): Boolean {
        // A background worker can still be generating when the activity hides.
        // Closing that shared engine would free native memory under a live call.
        if (!sessionMutex.tryLock()) return false
        return try {
            val e = synchronized(engineLock) { engines.remove(modelPath) } ?: return false
            runCatching { e.close(); true }.getOrDefault(false)
        } finally { sessionMutex.unlock() }
    }

    /**
     * Would warming up be safe right now? Loading a multi-GB model onto a phone
     * that is already short on memory trades a fast first answer for a slow
     * everything, so when the system is tight we stay cold and load on demand.
     */
    fun memoryAllowsWarmUp(minFreeMb: Long = 1_500L): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return true
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return !info.lowMemory && info.availMem >= minFreeMb * 1024L * 1024L
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
        if (!memoryAllowsWarmUp()) {
            android.util.Log.i(
                "LiteRt",
                "skipping warm-up: not enough free memory — the model loads on demand",
            )
            return
        }
        runCatching { engine() }
    }

    override fun generateStreaming(prompt: String): Flow<ModelResponse> = stream(prompt, limitSentences = true)

    private fun stream(prompt: String, limitSentences: Boolean, jsonSchema: String? = null): Flow<ModelResponse> = flow {
        sessionMutex.lock()
        try {
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
                temperature = if (limitSentences) 0.8 else 0.1,
            ),
            automaticToolCalling = false,
            maxOutputToken = if (limitSentences) 512 else 1536,
            enableResponseFormat = jsonSchema != null,
            thinkingConfig = if (limitSentences) null else com.google.ai.edge.litertlm.ThinkingConfig(enableThinking = false),
        )

        val conversation = try {
            lastUsedAtMs = System.currentTimeMillis()
            noteUsed(modelPath, lastUsedAtMs)
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
                engine().createConversation(config)
            }
        } catch (e: Exception) {
            emit(ModelResponse.Error(e.message ?: "LiteRT could not start a conversation"))
            return@flow
        }

        val accumulated = ModelTextAccumulator()
        var fullText = ""
        try {
            conversation.sendMessageAsync(prompt,
                responseFormat = jsonSchema?.let { com.google.ai.edge.litertlm.ResponseFormat.json(it) }
            ).collect { message ->
                val text = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                // LiteRT sends DELTAS, not cumulative transcripts. Replacing
                // fullText with each chunk reduced a JSON answer to its final
                // '}' on the Flip 6 and made every agent decision fail parsing.
                fullText = accumulated.append(text)
                if (text.isNotEmpty()) {
                    emit(ModelResponse.Partial(text))
                }
                // litertlm 0.13.1 has no maxTokens, and uncapped Gemma rambles
                // for half a minute of TTS. Voice answers stay conversational:
                // stop generating after a few complete sentences.
                if (limitSentences && SENTENCES.findAll(fullText).count() >= MAX_REPLY_SENTENCES) {
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
        } finally { sessionMutex.unlock() }
    }

    private class ReplyCapException : RuntimeException()

    /** JSON/tool decisions must not be cut off after three prose sentences.
     * The asynchronous native path also lets job cancellation stop generation. */
    suspend fun generateStructured(prompt: String, jsonSchema: String = "{\"type\":\"object\"}"): ModelResponse {
        var result: ModelResponse = ModelResponse.Error("No model response")
        stream(prompt, limitSentences = false, jsonSchema = jsonSchema).collect { response ->
            if (response is ModelResponse.Complete || response is ModelResponse.Error) result = response
        }
        return result
    }

    private companion object {
        /** Guards all access to [engines] so build/release are atomic across instances. */
        val engineLock = Any()
        val sessionMutex = kotlinx.coroutines.sync.Mutex()

        /** modelPath -> resident engine. Never grows beyond the models on disk. */
        val engines = java.util.concurrent.ConcurrentHashMap<String, Engine>()
        val lastUsedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

        /**
         * Free every resident model. Returns how many were released.
         *
         * Used when the app goes to the background: nothing reads the on-device
         * brain there, and keeping it is what got this app (the HOME app) killed
         * by lmkd.
         */
        fun releaseAll(): Int {
            val paths = synchronized(engineLock) { engines.keys.toList() }
            var n = 0
            paths.forEach { path ->
                val e = synchronized(engineLock) { engines.remove(path) } ?: return@forEach
                runCatching { e.close() }
                n++
            }
            if (n > 0) {
                android.util.Log.i("LiteRt", "released $n resident model(s)")
            }
            return n
        }

        /** Milliseconds since any local model was last asked a question. */
        fun idleMs(nowMs: Long = System.currentTimeMillis()): Long {
            val last = lastUsedAt.values.maxOrNull() ?: return Long.MAX_VALUE
            return nowMs - last
        }

        internal fun noteUsed(modelPath: String, nowMs: Long) {
            lastUsedAt[modelPath] = nowMs
        }

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
        withContext(Dispatchers.Default) {
            sessionMutex.lock()
            try { generateOffMain(prompt) } finally { sessionMutex.unlock() }
        }

    private suspend fun generateOffMain(prompt: String): ModelResponse {
        val config = ConversationConfig(
            systemInstruction = Contents.of(
                "You are a helpful assistant called Meowmba. Be concise."
            ),
        )
        val conversation = try {
            lastUsedAtMs = System.currentTimeMillis()
            noteUsed(modelPath, lastUsedAtMs)
            engine().createConversation(config)
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
        release()
    }
}
