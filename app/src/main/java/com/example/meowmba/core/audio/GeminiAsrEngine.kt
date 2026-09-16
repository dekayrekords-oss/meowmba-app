package com.example.meowmba.core.audio

import android.util.Base64
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

/** Opt-in OpenRouter transcription over the shared local capture path.
 * Completed audio is ordered and cancelled by RefiningSpeechEngine. Network
 * failure reports local fallback; a successful empty final stays empty.
 * Recognition accuracy and speech-in-noise behavior require device validation.
 */
class GeminiAsrEngine(
    local: SherpaAsrEngine,
    scope: CoroutineScope,
    /** OpenRouter key, read fresh per request so Settings edits take effect. */
    private val apiKey: () -> String?,
    /** Model id; defaults to [AsrBackend.DEFAULT_GEMINI_MODEL]. */
    private val model: () -> String = { AsrBackend.DEFAULT_GEMINI_MODEL },
    private val client: HttpClient = defaultClient,
    onIssue: (String?) -> Unit = {},
    localFallback: (suspend (ShortArray) -> String?)? = null,
) : RefiningSpeechEngine(local, scope, onIssue, localFallback = localFallback) {
    override val isAvailable: Boolean
        get() = local.isAvailable && !apiKey().isNullOrBlank()

    /** Returns Gemini's transcript, or null when unusable for any reason. */
    override suspend fun transcribe(pcm: ShortArray): String? {
        val key = apiKey()?.takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "no OpenRouter key configured")
            return null
        }
        val seconds = WavEncoder.durationSeconds(pcm, SAMPLE_RATE)
        if (seconds < MIN_SECONDS) {
            Log.i(TAG, "utterance too short for cloud (${seconds}s)")
            return null
        }
        if (seconds > MAX_SECONDS) {
            Log.w(TAG, "utterance too long for cloud (${seconds}s) — using local")
            return null
        }

        val wav = WavEncoder.encode(pcm, SAMPLE_RATE)
        val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)

        return withContext(Dispatchers.IO) {
            val response = client.post(ENDPOINT) {
                header(HttpHeaders.Authorization, "Bearer $key")
                header("HTTP-Referer", "https://meowmba.local")
                header("X-Title", "MEOWMBA")
                contentType(ContentType.Application.Json)
                setBody(requestBody(model(), b64).toString())
            }
            if (!response.status.isSuccess()) {
                // Log status only; response bodies can include private request data.
                Log.w(TAG, "cloud ASR HTTP ${response.status.value}")
                throw CloudTranscriptionFailure("OpenRouter", response.status.value)
            }
            parseTranscript(response.bodyAsText()).also {
                Log.i(TAG, "cloud transcription completed usable=${it != null} characters=${it?.length ?: 0}")
            }
        }
    }

    companion object {
        private const val TAG = "GeminiAsr"
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        private const val SAMPLE_RATE = 16_000

        /** Below this it is a cough or a clipped word — not worth a round trip. */
        private const val MIN_SECONDS = 0.35f

        /** Guard against sending a runaway buffer (and a surprise bill). */
        private const val MAX_SECONDS = 60f

        private val json = Json { ignoreUnknownKeys = true }

        private val defaultClient by lazy {
            HttpClient {
                followRedirects = false
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    requestTimeoutMillis = 15_000
                    socketTimeoutMillis = 15_000
                }
            }
        }

        /**
         * OpenAI-compatible `input_audio` content part, which OpenRouter
         * accepts for every Gemini model whose catalog entry lists `audio`
         * among its input modalities.
         *
         * The prompt is deliberately strict: without "return only the words"
         * the model answers the question it heard instead of transcribing it,
         * which turns "what time is it" into a clock reading rather than a
         * transcript.
         */
        private const val TRANSCRIBE_PROMPT =
            "Transcribe this audio verbatim. Return ONLY the spoken words as plain " +
                "text — no commentary, no speaker labels, no explanation. If there is " +
                "no intelligible speech, return an empty string."

        internal fun requestBody(model: String, wavBase64: String): JsonObject = buildJsonObject {
            put("model", model)
            // Transcription is a copy task; sampling only invents words.
            put("temperature", 0.0)
            // Bound the reservation: provider defaults can reserve an entire
            // context window and reject an otherwise affordable short transcript.
            put("max_tokens", 1024)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "text")
                                            put("text", TRANSCRIBE_PROMPT)
                                        },
                                    )
                                    add(
                                        buildJsonObject {
                                            put("type", "input_audio")
                                            putJsonObject("input_audio") {
                                                put("data", wavBase64)
                                                put("format", "wav")
                                            }
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }

        /**
         * Pulls `choices[0].message.content` out of an OpenRouter completion.
         *
         * Internal + pure so the parse is a JVM unit test: a schema drift here
         * looks exactly like "the cloud model heard nothing", which is the
         * most expensive kind of bug to misdiagnose.
         */
        internal fun parseTranscript(body: String): String? = runCatching {
            val content = json.parseToJsonElement(body)
                .jsonObject["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.contentOrNull
                ?: return@runCatching null
            SpokenText.normalize(content.trim().trim('"'))
        }.getOrNull()
    }
}
