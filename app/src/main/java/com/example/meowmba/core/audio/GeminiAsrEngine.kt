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

/**
 * Cloud speech-to-text: local [SherpaAsrEngine] captures and endpoints, Gemini
 * transcribes the finished utterance.
 *
 * ## Why a decorator and not its own recognizer
 *
 * Gemini is request/response — it needs a COMPLETE utterance, so something has
 * to decide when the user stopped talking. The obvious candidate, Silero VAD,
 * is disabled in this build: sherpa-onnx v1.13.4's `Vad.acceptWaveform`
 * SIGSEGVs on this device (see [WakeWordEngineFactory.VAD_GATE_ENABLED]).
 * Sherpa's *recognizer* endpointing is a different native path and is known
 * good, and it is already holding the one AudioRecord this app is allowed to
 * contend for. So this engine borrows both: sherpa's endpoint fires, sherpa
 * hands over the PCM it just consumed via [SherpaAsrEngine.lastHeardPcm], and
 * that audio goes to Gemini.
 *
 * The user gets sherpa's partials instantly (so the UI never looks deaf) and
 * Gemini's far better text as the final result.
 *
 * ## Failure behaviour
 *
 * Never silently degrades. Any failure — no key, offline, HTTP error, empty
 * completion — delivers the LOCAL transcript instead and logs why. Losing a
 * turn entirely would be worse than answering a slightly misheard one, and
 * per house rule the fallback is visible in logcat rather than pretended away.
 */
class GeminiAsrEngine(
    private val local: SherpaAsrEngine,
    private val scope: CoroutineScope,
    /** OpenRouter key, read fresh per request so Settings edits take effect. */
    private val apiKey: () -> String?,
    /** Model id; defaults to [AsrBackend.DEFAULT_GEMINI_MODEL]. */
    private val model: () -> String = { AsrBackend.DEFAULT_GEMINI_MODEL },
    private val client: HttpClient = defaultClient,
) : SpeechEngine {

    override val isListening: StateFlow<Boolean> get() = local.isListening
    override val partialResults: StateFlow<String> get() = local.partialResults

    /** Cloud transcription is pointless without the local capture path. */
    override val isAvailable: Boolean
        get() = local.isAvailable && !apiKey().isNullOrBlank()

    private var onResult: ((String) -> Unit)? = null
    private var onError: ((Int) -> Unit)? = null

    override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        this.onResult = onResult
        this.onError = onError
        local.startListening(
            onResult = { localText -> finalizeWithGemini(localText) },
            onError = { code -> this.onError?.invoke(code) },
        )
    }

    override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        this.onResult = onResult
        this.onError = onError
        // The inner engine keeps OUR wrappers; only the outward-facing
        // callbacks change. Re-registering the wrappers here would be
        // harmless but pointless — they close over `this`, not the captured
        // callbacks.
        local.swapCallbacks(
            onResult = { localText -> finalizeWithGemini(localText) },
            onError = { code -> this.onError?.invoke(code) },
        )
    }

    override fun setFocusedListening(on: Boolean) = local.setFocusedListening(on)

    override fun stopListening() {
        onResult = null
        onError = null
        local.stopListening()
    }

    override fun destroy() {
        stopListening()
        local.destroy()
    }

    /**
     * Grab the audio for the utterance sherpa just finished and re-transcribe
     * it in the cloud.
     *
     * [SherpaAsrEngine.lastHeardPcm] is read SYNCHRONOUSLY here: the capture
     * loop overwrites it on the next endpoint, so deferring the read into the
     * coroutine would transcribe the wrong utterance when someone talks again
     * while a request is in flight.
     */
    private fun finalizeWithGemini(localText: String) {
        val pcm = local.lastHeardPcm
        val deliver = onResult
        if (deliver == null) return
        if (pcm.isEmpty()) {
            deliver(localText)
            return
        }
        scope.launch {
            val cloud = runCatching { transcribe(pcm) }.getOrElse { e ->
                Log.w(TAG, "cloud transcription threw; using local text", e)
                null
            }
            val finalText = cloud?.takeIf { it.isNotBlank() } ?: localText
            if (cloud.isNullOrBlank()) {
                Log.i(TAG, "fell back to local transcript: \"$localText\"")
            } else if (!cloud.equals(localText, ignoreCase = true)) {
                Log.i(TAG, "gemini corrected \"$localText\" -> \"$finalText\"")
            }
            withContext(Dispatchers.Main.immediate) {
                runCatching { deliver(finalText) }
            }
        }
    }

    /** Returns Gemini's transcript, or null when unusable for any reason. */
    private suspend fun transcribe(pcm: ShortArray): String? {
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
                // Body carries OpenRouter's actual reason (bad key, model
                // lacks audio input, out of credit). Log it verbatim — a bare
                // status code sent us hunting the wrong thing before.
                Log.w(TAG, "cloud ASR ${response.status}: ${response.bodyAsText().take(400)}")
                return@withContext null
            }
            parseTranscript(response.bodyAsText())
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
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    requestTimeoutMillis = 45_000
                    socketTimeoutMillis = 45_000
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
            SpokenText.polish(content.trim().trim('"'))
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
