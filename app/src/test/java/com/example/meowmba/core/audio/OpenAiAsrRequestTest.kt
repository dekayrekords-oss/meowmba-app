package com.example.meowmba.core.audio

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

/** Checks the real HTTP adapter without credentials, microphone or paid requests. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33])
class OpenAiAsrRequestTest {
    private class Capture : SpeechEngine, SpeechAudioCapture {
        override val continuousCapture = true
        override val isListening = MutableStateFlow(false)
        override val partialResults = MutableStateFlow("")
        var result: ((String) -> Unit)? = null
        override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            result = onResult; isListening.value = true
        }
        override fun stopListening() { result = null; isListening.value = false }
        override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) { result = onResult }
        override fun destroy() = stopListening()
        override fun snapshotCapturedAudio() = CapturedSpeechAudio(shortArrayOf(100, -100, 20), 16_000)
    }

    @Test fun sendsOriginalWavToTranscriptionEndpointAndReturnsFinalWords() = runBlocking {
        val local = Capture()
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("https://api.openai.com/v1/audio/transcriptions", request.url.toString())
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("Bearer test-only-placeholder", request.headers[HttpHeaders.Authorization])
            val body = request.body.toByteArray().toString(Charsets.ISO_8859_1)
            assertTrue(body.contains("name=\"model\""))
            assertTrue(body.contains("\r\n\r\ngpt-transcribe\r\n"))
            assertTrue(body.contains("filename=\"utterance.wav\""))
            assertTrue(body.contains("audio/wav"))
            assertTrue(body.contains(WavEncoder.encode(shortArrayOf(100, -100, 20), 16_000).toString(Charsets.ISO_8859_1)))
            assertFalse(body.contains("Wrong draft"))
            respond("""{"text":"No no, 42.","languages":["en"]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val engine = OpenAiAsrEngine(local, this, { "test-only-placeholder" }, client = client)
        try {
            val final = CompletableDeferred<String>()
            engine.startListening({ final.complete(it) }, { final.completeExceptionally(IllegalStateException("Error $it")) })
            local.result?.invoke("Wrong draft")
            assertEquals("No no, 42.", withTimeout(5000) { final.await() })
            assertEquals(1, requests)
        } finally { engine.stopListening(); client.close() }
    }

    @Test fun absentKeyNeverSendsAudioAndReportsLocalFallback() = runBlocking {
        val local = Capture()
        val issues = mutableListOf<String?>()
        val client = HttpClient(MockEngine { error("No network request without an OpenAI key") })
        val engine = OpenAiAsrEngine(local, this, { null }, { issues += it }, client)
        try {
            assertFalse(engine.isAvailable)
            val final = CompletableDeferred<String>()
            engine.startListening({ final.complete(it) }, {})
            local.result?.invoke("Open my project")
            assertEquals("Open my project", withTimeout(5000) { final.await() })
            assertTrue(issues.last()!!.contains("local transcript"))
        } finally { engine.stopListening(); client.close() }
    }
}
