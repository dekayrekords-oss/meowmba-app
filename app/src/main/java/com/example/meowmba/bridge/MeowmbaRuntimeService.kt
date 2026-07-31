package com.example.meowmba.bridge

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.example.meowmba.core.agent.MediaPipeProvider
import com.example.meowmba.core.agent.ModelProvider
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.RemoteModelProvider
import com.example.meowmba.core.database.ImportanceLevel
import com.example.meowmba.core.database.MeowmbaDatabase
import com.example.meowmba.core.database.MemoryType
import com.example.meowmba.core.memory.MemoryRepository
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.vision.DeviceVisionProvider
import com.example.meowmba.core.vision.OllamaVisionProvider
import com.example.meowmba.core.vision.OpenAIVisionProvider
import com.example.meowmba.core.vision.VisionProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Shared runtime bridge for MetaMemory (and lab siblings).
 *
 * Exposes Meowmba's vision + LLM + memory store over Messenger IPC so glasses
 * capture can reuse the same model configuration the companion already has.
 *
 * Allowed callers: MetaMemory, Meow Inventory, and self.
 */
class MeowmbaRuntimeService : Service() {

    companion object {
        private const val TAG = "MeowmbaBridge"
        const val ACTION = "com.example.meowmba.action.RUNTIME_BRIDGE"

        const val MSG_PING = 1
        const val MSG_DESCRIBE = 2
        const val MSG_GENERATE = 3
        const val MSG_PUSH_MEMORY = 4
        const val MSG_PULL_MEMORIES = 5
        const val MSG_REPLY = 100

        const val KEY_OK = "ok"
        const val KEY_ERROR = "error"
        const val KEY_TEXT = "text"
        const val KEY_JPEG = "jpeg"
        const val KEY_PROMPT = "prompt"
        const val KEY_CONTENT = "content"
        const val KEY_TAGS = "tags"
        const val KEY_SOURCE = "source"
        const val KEY_MEMORIES_JSON = "memories_json"
        const val KEY_SINCE = "since"

        private val ALLOWED = setOf(
            "com.example.meowmba",
            "com.meow.metamemory",
            "com.meow.inventory",
        )
    }

    private lateinit var secure: SecureStorageManager
    private lateinit var memories: MemoryRepository
    private val messenger = Messenger(IncomingHandler())

    override fun onCreate() {
        super.onCreate()
        secure = SecureStorageManager(this)
        val db = MeowmbaDatabase.getDatabase(this)
        memories = MemoryRepository(db.messageDao(), db.memoryDao())
        Log.i(TAG, "Runtime bridge ready")
    }

    override fun onBind(intent: Intent?): IBinder? {
        val pkg = packageManager.getPackagesForUid(BinderCallingUid.get())?.firstOrNull()
            ?: return null
        if (pkg !in ALLOWED) {
            Log.w(TAG, "Reject bind from $pkg")
            return null
        }
        return messenger.binder
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val replyTo = msg.replyTo ?: return
            // Offload heavy work
            Thread {
                val out = Bundle()
                try {
                    when (msg.what) {
                        MSG_PING -> out.putBoolean(KEY_OK, true).also {
                            out.putString(KEY_TEXT, "meowmba-runtime")
                        }
                        MSG_DESCRIBE -> handleDescribe(msg.data, out)
                        MSG_GENERATE -> handleGenerate(msg.data, out)
                        MSG_PUSH_MEMORY -> handlePush(msg.data, out)
                        MSG_PULL_MEMORIES -> handlePull(msg.data, out)
                        else -> {
                            out.putBoolean(KEY_OK, false)
                            out.putString(KEY_ERROR, "unknown msg ${msg.what}")
                        }
                    }
                } catch (e: Exception) {
                    out.putBoolean(KEY_OK, false)
                    out.putString(KEY_ERROR, e.localizedMessage ?: e.javaClass.simpleName)
                    Log.e(TAG, "handler error", e)
                }
                val reply = Message.obtain(null, MSG_REPLY).also { it.data = out }
                runCatching { replyTo.send(reply) }
            }.start()
        }
    }

    private fun handleDescribe(data: Bundle, out: Bundle) {
        val jpeg = data.getByteArray(KEY_JPEG)
        val prompt = data.getString(KEY_PROMPT)
            ?: "Describe this first-person photo briefly."
        if (jpeg == null || jpeg.isEmpty()) {
            out.putBoolean(KEY_OK, false)
            out.putString(KEY_ERROR, "empty jpeg")
            return
        }
        val vision = visionProvider()
        val result = runBlocking { vision.describe(jpeg, prompt) }
        result.fold(
            onSuccess = { text ->
                out.putBoolean(KEY_OK, true)
                out.putString(KEY_TEXT, text)
                Log.i(TAG, "describe via ${vision.name}: ${text.take(80)}")
            },
            onFailure = { e ->
                out.putBoolean(KEY_OK, false)
                out.putString(KEY_ERROR, e.message)
            },
        )
    }

    private fun handleGenerate(data: Bundle, out: Bundle) {
        val prompt = data.getString(KEY_PROMPT).orEmpty()
        if (prompt.isBlank()) {
            out.putBoolean(KEY_OK, false)
            out.putString(KEY_ERROR, "empty prompt")
            return
        }
        val model = textProvider()
        if (model == null) {
            out.putBoolean(KEY_OK, false)
            out.putString(KEY_ERROR, "no Meowmba model configured")
            return
        }
        val resp = runBlocking { model.generate(prompt) }
        when (resp) {
            is ModelResponse.Complete -> {
                out.putBoolean(KEY_OK, true)
                out.putString(KEY_TEXT, resp.text)
            }
            is ModelResponse.Partial -> {
                out.putBoolean(KEY_OK, true)
                out.putString(KEY_TEXT, resp.text)
            }
            is ModelResponse.Error -> {
                out.putBoolean(KEY_OK, false)
                out.putString(KEY_ERROR, resp.message)
            }
        }
    }

    private fun handlePush(data: Bundle, out: Bundle) {
        val content = data.getString(KEY_CONTENT).orEmpty()
        if (content.isBlank()) {
            out.putBoolean(KEY_OK, false)
            out.putString(KEY_ERROR, "empty content")
            return
        }
        val tags = data.getString(KEY_TAGS)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: listOf("meta_glasses")
        val source = data.getString(KEY_SOURCE) ?: "meta_memory"
        runBlocking {
            memories.addMemory(
                content = content,
                type = MemoryType.EPISODIC,
                importance = ImportanceLevel.MEDIUM,
                tags = tags + source,
            )
        }
        out.putBoolean(KEY_OK, true)
        Log.i(TAG, "memory pushed from $source (${content.take(60)})")
    }

    private fun handlePull(data: Bundle, out: Bundle) {
        val since = data.getLong(KEY_SINCE, 0L)
        val list = runBlocking { memories.getRecentMemories(200) }
            .filter { it.timestamp >= since }
        val arr = JSONArray()
        for (m in list) {
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("content", m.content)
                    .put("timestamp", m.timestamp)
                    .put("type", m.type.name)
                    .put("tags", JSONArray(m.tags)),
            )
        }
        out.putBoolean(KEY_OK, true)
        out.putString(KEY_MEMORIES_JSON, arr.toString())
    }

    private fun visionProvider(): VisionProvider {
        val ollama = secure.getEndpoint("ollama")
        val visionModel = secure.getModelName("ollama_vision") ?: "llava"
        if (!ollama.isNullOrBlank()) {
            return OllamaVisionProvider(ollama.trimEnd('/'), visionModel)
        }
        val openAiKey = secure.getApiKey("openai") ?: secure.getApiKey("xai")
        val openAiBase = secure.getEndpoint("openai")
            ?: secure.getEndpoint("xai")
            ?: "https://api.openai.com"
        if (!openAiKey.isNullOrBlank()) {
            val model = secure.getModelName("openai_vision")
                ?: if (openAiBase.contains("x.ai")) "grok-4.5" else "gpt-4o-mini"
            return OpenAIVisionProvider(openAiBase.trimEnd('/'), openAiKey, model)
        }
        return DeviceVisionProvider()
    }

    private fun textProvider(): ModelProvider? {
        val ollama = secure.getEndpoint("ollama")
        val ollamaModel = secure.getModelName("ollama") ?: "llama3.2"
        if (!ollama.isNullOrBlank()) {
            return RemoteModelProvider(
                name = "Ollama",
                baseUrl = ollama.trimEnd('/'),
                apiKey = null,
                model = ollamaModel,
                style = RemoteModelProvider.ApiStyle.OLLAMA,
            )
        }
        val modelsDir = File(filesDir, "models")
        val task = modelsDir.listFiles()?.firstOrNull {
            it.isFile && (it.name.endsWith(".task") || it.name.endsWith(".bin"))
        }
        if (task != null) {
            return MediaPipeProvider(this, task.absolutePath)
        }
        return null
    }
}

/** Binder.getCallingUid helper without importing Binder in every site. */
private object BinderCallingUid {
    fun get(): Int = android.os.Binder.getCallingUid()
}
