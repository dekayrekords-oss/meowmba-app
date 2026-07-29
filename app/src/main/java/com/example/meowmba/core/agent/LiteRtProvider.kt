package com.example.meowmba.core.agent

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    override fun generateStreaming(prompt: String): Flow<ModelResponse> = callbackFlow {
        if (!File(modelPath).exists()) {
            trySend(ModelResponse.Error("Model file not found at $modelPath"))
            close()
            return@callbackFlow
        }

        try {
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
            var fullText = ""
            engine.createConversation(config).use { conversation ->
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
                        trySend(ModelResponse.Partial(delta))
                    }
                }
            }
            trySend(ModelResponse.Complete(fullText))
        } catch (e: Exception) {
            trySend(ModelResponse.Error(e.message ?: "LiteRT inference error"))
        }
        close()
        awaitClose { }
    }

    override suspend fun generate(prompt: String): ModelResponse {
        return try {
            val config = ConversationConfig(
                systemInstruction = Contents.of(
                    "You are a helpful assistant called Meowmba. Be concise."
                ),
            )
            engine.createConversation(config).use { conversation ->
                val message = conversation.sendMessage(prompt)
                val text = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                ModelResponse.Complete(text)
            }
        } catch (e: Exception) {
            ModelResponse.Error(e.message ?: "LiteRT inference error")
        }
    }

    fun isModelAvailable(): Boolean = File(modelPath).exists()

    fun close() {
        runCatching { engine.close() }
    }
}
