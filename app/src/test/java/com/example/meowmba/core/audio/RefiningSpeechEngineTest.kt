package com.example.meowmba.core.audio

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefiningSpeechEngineTest {
    private class Capture : SpeechEngine, SpeechAudioCapture {
        override val continuousCapture = true
        override val isListening = MutableStateFlow(false)
        override val partialResults = MutableStateFlow("")
        var result: ((String) -> Unit)? = null
        var pcm = shortArrayOf(1)
        var finishCalls = 0
        override fun finishInput(): Boolean { finishCalls++; return true }
        override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            result = onResult; isListening.value = true
        }
        override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) { result = onResult }
        override fun stopListening() { result = null; isListening.value = false }
        override fun destroy() = stopListening()
        override fun snapshotCapturedAudio() = CapturedSpeechAudio(pcm.copyOf(), 16_000)
        fun say(text: String, sample: Short) { pcm = shortArrayOf(sample); result?.invoke(text) }
    }

    @Test fun `cloud keeps continuous capture and delivers in order with matching audio`() = runTest {
        val local = Capture()
        val engine = object : RefiningSpeechEngine(local, this) {
            override suspend fun transcribe(pcm: ShortArray): String { delay(if (pcm[0] == 1.toShort()) 1000 else 1); return "Final ${pcm[0]}" }
        }
        val heard = mutableListOf<Pair<String, Short>>()
        engine.startListening({ heard += it to engine.snapshotCapturedAudio()!!.pcm16[0] }, {})
        local.say("first", 1); runCurrent(); local.say("second", 2)
        advanceUntilIdle()
        assertTrue(engine.continuousCapture)
        assertEquals(listOf("Final 1" to 1.toShort(), "Final 2" to 2.toShort()), heard)
        engine.stopListening()
    }

    @Test fun `cancelled cloud response cannot answer a later mic session`() = runTest {
        val local = Capture()
        val engine = object : RefiningSpeechEngine(local, this) {
            override suspend fun transcribe(pcm: ShortArray): String {
                withContext(NonCancellable) { delay(1000) }; return "Final ${pcm[0]}"
            }
        }
        val heard = mutableListOf<String>()
        engine.startListening({ heard += it }, {})
        local.say("first", 1); runCurrent()
        engine.stopListening()
        engine.startListening({ heard += it }, {})
        local.say("second", 2)
        advanceUntilIdle()
        assertEquals(listOf("Final 2"), heard)
        engine.stopListening()
    }

    @Test fun `network stall falls back once and reports it without losing the turn`() = runTest {
        val local = Capture()
        val issues = mutableListOf<String?>()
        val engine = object : RefiningSpeechEngine(local, this, { issues += it }, timeoutMs = 1000) {
            override suspend fun transcribe(pcm: ShortArray): String { delay(10000); return "late" }
        }
        val heard = mutableListOf<String>()
        engine.startListening({ heard += it }, {})
        local.say("Open my project", 1)
        advanceUntilIdle()
        assertEquals(listOf("Open my project"), heard)
        assertTrue(issues.last()!!.contains("local transcript"))
        engine.stopListening()
    }

    @Test fun `payment failure decodes original audio locally and keeps actionable status`() = runTest {
        val local = Capture()
        val issues = mutableListOf<String?>()
        var fallbackCalls = 0
        val engine = object : RefiningSpeechEngine(local, this, { issues += it }, localFallback = { pcm ->
            assertArrayEquals(shortArrayOf(42), pcm); fallbackCalls++; "Report a bug."
        }) {
            override suspend fun transcribe(pcm: ShortArray): String? = throw CloudTranscriptionFailure("OpenRouter", 402)
        }
        val heard = mutableListOf<String>()
        engine.startListening({ heard += it }, {})
        local.say("The report of i", 42)
        advanceUntilIdle()
        assertEquals(listOf("Report a bug."), heard)
        assertEquals(1, fallbackCalls)
        assertTrue(issues.last()!!.contains("402"))
        assertTrue(issues.last()!!.contains("local Whisper"))
        engine.stopListening(); engine.startListening({}, {})
        assertTrue(issues.last()!!.contains("402")) // rearming cannot erase the failure
        engine.stopListening()
    }

    @Test fun `successful empty final clears listening without resurrecting a guess`() = runTest {
        val local = Capture()
        val engine = object : RefiningSpeechEngine(local, this) {
            override suspend fun transcribe(pcm: ShortArray) = ""
        }
        val heard = mutableListOf<String>()
        engine.startListening({ heard += it }, {})
        local.say("Hallucinated request", 1)
        advanceUntilIdle()
        assertEquals(listOf(""), heard)
        engine.stopListening()
    }

    @Test fun `finals preserve numeric replies repetitions and non Latin speech`() {
        for (text in listOf("42", "No no", "我", "sí", "I I need help")) {
            assertEquals(text, SpokenText.refinedTranscript("bad draft", text))
        }
        assertNull(SpokenText.refinedTranscript("bad draft", "[noise]"))
        assertNull(SpokenText.refinedTranscript("bad draft", ""))
    }

    @Test fun `OpenAI response parser preserves empty finals and detects malformed responses`() {
        assertEquals("42", OpenAiAsrEngine.parseTranscript("""{"text":"42"}"""))
        assertEquals("", OpenAiAsrEngine.parseTranscript("""{"text":""}"""))
        assertNull(OpenAiAsrEngine.parseTranscript("""{"error":{"message":"bad key"}}"""))
        assertNull(OpenAiAsrEngine.parseTranscript("bad json"))
    }

    @Test fun `OpenAI cannot be enabled by an OpenRouter key`() {
        assertFalse(AsrBackendStatus.evaluate(AsrBackend.OPENAI_CLOUD, false, true, true).available)
        assertTrue(AsrBackendStatus.evaluate(AsrBackend.OPENAI_CLOUD, false, true, false, openAiKeyConfigured = true).available)
    }

    @Test fun `quiet endpoint cannot cancel cloud speech waiting for a final`() = runTest {
        val local = Capture()
        val engine = object : RefiningSpeechEngine(local, this) {
            override suspend fun transcribe(pcm: ShortArray): String { delay(5000); return "Actual words" }
        }
        val capture = UtteranceCapture(engine, this, now = { testScheduler.currentTime + 1 })
        var heard: String? = null
        capture.start({ heard = it }, { fail("Unexpected error $it") })
        local.say("Bad draft", 1); runCurrent()
        advanceTimeBy(3500)
        local.pcm = ShortArray(0); local.result?.invoke(""); runCurrent()
        assertNull(heard)
        capture.finishNow() // Submit while decoding must not force another empty recording.
        assertEquals(0, local.finishCalls)
        advanceUntilIdle()
        assertEquals("Actual words", heard)
    }

    @Test fun `playback to followup handoff retains an in flight cloud transcript`() = runTest {
        val local = Capture()
        val engine = object : RefiningSpeechEngine(local, this) {
            override suspend fun transcribe(pcm: ShortArray): String { delay(5000); return "My follow up" }
        }
        engine.startListening({ fail("Old playback callback must be replaced") }, {})
        local.say("Draft follow up", 1); runCurrent()
        val capture = UtteranceCapture(engine, this, reuseSession = true, now = { testScheduler.currentTime + 1 })
        var heard: String? = null
        capture.start({ heard = it }, { fail("Unexpected error $it") })
        advanceUntilIdle()
        assertEquals("My follow up", heard)
    }

    @Test fun `finalization budget follows endpoint and preserves a long spoken turn`() = runTest {
        val local = Capture()
        val engine = object : RefiningSpeechEngine(local, this) {
            override suspend fun transcribe(pcm: ShortArray): String { delay(14000); return "Complete long turn" }
        }
        val capture = UtteranceCapture(engine, this, now = { testScheduler.currentTime + 1 })
        var heard: String? = null
        capture.start({ heard = it }, { fail("Unexpected timeout $it") })
        local.partialResults.value = "Long speech in progress"; runCurrent()
        advanceTimeBy(36000)
        local.say("Long speech", 1); runCurrent()
        advanceUntilIdle()
        assertEquals("Complete long turn", heard)
    }

    @Test fun `manual submit has a bounded deadline even when recorder never calls back`() = runTest {
        val local = Capture()
        val capture = UtteranceCapture(local, this, now = { testScheduler.currentTime + 1 })
        var error: Int? = null
        capture.start({ fail("No transcript exists") }, { error = it })
        local.partialResults.value = "Draft"; runCurrent()
        capture.finishNow()
        advanceTimeBy(20200); runCurrent()
        assertNotNull(error)
        assertFalse(capture.isCapturing)
    }
}
