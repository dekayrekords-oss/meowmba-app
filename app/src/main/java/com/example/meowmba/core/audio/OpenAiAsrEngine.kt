package com.example.meowmba.core.audio

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Selected explicitly in Voice & Conversation. Uses the user's encrypted
 * OpenAI key; no app-wide key, transcript rewrite rules or hidden cloud switch. */
class OpenAiAsrEngine(
    local: SpeechEngine,
    scope: CoroutineScope,
    private val apiKey: () -> String?,
    onIssue: (String?) -> Unit = {},
    private val client: HttpClient = defaultClient,
    localFallback: (suspend (ShortArray) -> String?)? = null,
) : RefiningSpeechEngine(local, scope, onIssue, localFallback = localFallback) {
    override val isAvailable get() = local.isAvailable && !apiKey().isNullOrBlank()

    override suspend fun transcribe(pcm: ShortArray): String? = withContext(Dispatchers.IO) {
        val key = apiKey()?.takeIf { it.isNotBlank() } ?: return@withContext null
        if (pcm.isEmpty() || pcm.size > AsrCaptureBounds.PCM_CAPACITY) return@withContext null
        val response = client.post("https://api.openai.com/v1/audio/transcriptions") {
            header(HttpHeaders.Authorization, "Bearer $key")
            setBody(MultiPartFormDataContent(formData {
                append("model", MODEL)
                append("file", WavEncoder.encode(pcm, 16_000), Headers.build {
                    append(HttpHeaders.ContentType, "audio/wav")
                    append(HttpHeaders.ContentDisposition, "filename=\"utterance.wav\"")
                })
            }))
        }
        if (!response.status.isSuccess()) {
            android.util.Log.w("OpenAiAsr", "cloud ASR HTTP ${response.status.value}; model=$MODEL")
            throw CloudTranscriptionFailure("OpenAI", response.status.value)
        }
        parseTranscript(response.bodyAsText()).also {
            android.util.Log.i("OpenAiAsr", "cloud transcription completed model=$MODEL usable=${it != null} characters=${it?.length ?: 0}")
        }
    }

    companion object {
        const val MODEL = "gpt-transcribe"
        private val defaultClient by lazy {
            speechHttpClient(5_000L, 15_000L, followRedirects = false)
        }
        internal fun parseTranscript(body: String): String? = runCatching {
            Json.parseToJsonElement(body).jsonObject["text"]?.jsonPrimitive?.contentOrNull
                ?.let(SpokenText::normalize)
        }.getOrNull()
    }
}
