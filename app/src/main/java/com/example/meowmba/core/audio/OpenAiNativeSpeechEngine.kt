package com.example.meowmba.core.audio

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * One-turn audio-native conversation over OpenAI Chat Completions.
 *
 * The request contains WAV audio as the user message and asks the model for WAV audio
 * back. The transcript bundled with that generated audio is retained for captions,
 * memory, notifications, and echo suppression. The local recognizer's text is never
 * placed in the request, so the model genuinely understands the audio rather than a
 * hidden transcription.
 */
class OpenAiNativeSpeechEngine(
    /** Read fresh for every turn so saving Settings does not require an app restart. */
    private val apiKey: () -> String?,
    private val model: () -> String = { DEFAULT_MODEL },
    private val voice: () -> String = { DEFAULT_VOICE },
    private val client: HttpClient = defaultClient,
) : NativeSpeechEngine {

    override val isAvailable: Boolean get() = !apiKey().isNullOrBlank()

    @Volatile
    private var activeJob: Job? = null

    override suspend fun respond(request: NativeSpeechRequest): NativeSpeechResponse {
        if (!request.audio.isUsable) return NativeSpeechResponse.Failure("No captured microphone audio")
        val key = apiKey()?.trim().takeIf { !it.isNullOrEmpty() }
            ?: return NativeSpeechResponse.Failure("No OpenAI API key configured")
        val durationSeconds = request.audio.pcm16.size.toFloat() / request.audio.sampleRate
        if (durationSeconds < MIN_AUDIO_SECONDS) {
            return NativeSpeechResponse.Failure("Captured speech was too short")
        }
        if (durationSeconds > MAX_AUDIO_SECONDS) {
            return NativeSpeechResponse.Failure("Captured speech exceeded ${MAX_AUDIO_SECONDS.toInt()} seconds")
        }

        val job = currentCoroutineContext()[Job]
        activeJob = job
        return try {
            val wav = WavEncoder.encode(request.audio.pcm16, request.audio.sampleRate)
            val input = Base64.getEncoder().encodeToString(wav)
            val response = withContext(Dispatchers.IO) {
                client.post(ENDPOINT) {
                    header(HttpHeaders.Authorization, "Bearer $key")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody(model(), voice(), request.instructions, input).toString())
                }
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                NativeSpeechResponse.Failure(
                    "OpenAI native speech ${response.status.value}: ${apiError(body) ?: body.take(240)}",
                )
            } else {
                parseResponse(body)?.let { NativeSpeechResponse.Success(it) }
                    ?: NativeSpeechResponse.Failure("OpenAI returned no playable speech audio")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            NativeSpeechResponse.Failure(e.message ?: e.javaClass.simpleName)
        } finally {
            if (activeJob === job) activeJob = null
        }
    }

    override fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }

    override fun close() {
        cancel()
        client.close()
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-audio"
        const val DEFAULT_VOICE = "coral"
        const val MODEL_PROVIDER_KEY = "openai_audio"
        const val VOICE_PREF_KEY = "openai_audio_voice"

        private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val MIN_AUDIO_SECONDS = 0.25f
        private const val MAX_AUDIO_SECONDS = 60f

        private val json = Json { ignoreUnknownKeys = true }

        private val defaultClient by lazy {
            HttpClient {
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    requestTimeoutMillis = 90_000
                    socketTimeoutMillis = 90_000
                }
            }
        }

        internal fun requestBody(
            model: String,
            voice: String,
            instructions: String,
            wavBase64: String,
        ): JsonObject = buildJsonObject {
            put("model", model.ifBlank { DEFAULT_MODEL })
            put("modalities", buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("text"))
                add(kotlinx.serialization.json.JsonPrimitive("audio"))
            })
            putJsonObject("audio") {
                put("voice", voice.ifBlank { DEFAULT_VOICE })
                put("format", "wav")
            }
            put("max_completion_tokens", 700)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "developer")
                    put("content", instructions)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "input_audio")
                            putJsonObject("input_audio") {
                                put("data", wavBase64)
                                put("format", "wav")
                            }
                        })
                    })
                })
            })
        }

        /** Pure parser: malformed or text-only responses fail closed. */
        internal fun parseResponse(body: String): NativeSpeechReply? = runCatching {
            val audio = json.parseToJsonElement(body)
                .jsonObject["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("audio")?.jsonObject
                ?: return@runCatching null
            val data = audio["data"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val transcript = audio["transcript"]?.jsonPrimitive?.contentOrNull
                ?.let(SpokenText::polish)?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            val decoded = Base64.getDecoder().decode(data)
            if (decoded.isEmpty()) return@runCatching null
            NativeSpeechReply(wav = decoded, transcript = transcript)
        }.getOrNull()

        internal fun apiError(body: String): String? = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}

