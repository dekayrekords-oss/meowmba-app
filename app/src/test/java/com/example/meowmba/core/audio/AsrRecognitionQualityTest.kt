package com.example.meowmba.core.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AsrRecognitionQualityTest {
    @Test fun `ordinary you to and you two phrases are never changed to a media app`() {
        listOf("I want you to become a cat", "Could you two come here?", "I asked you to turn left",
            "You two are welcome", "Can you take me there?").forEach {
            assertEquals(it, it, SpokenText.polish(it))
        }
    }

    @Test fun `Whisper presentation normalization never applies streaming word repairs`() {
        assertEquals("I want you to come here.", SpokenText.normalize("I want you to come here."))
        assertEquals("Ovites up body", SpokenText.normalize("Ovites up body"))
        assertEquals("Play YouTube", SpokenText.normalize("Play YouTube"))
        assertEquals("Set a timer", SpokenText.normalize("SET A TIMER"))
    }

    @Test fun `streaming greeting repair and unambiguous tube spelling still work`() {
        assertEquals("What's up buddy", SpokenText.polish("Ovites up body"))
        assertEquals("Open YouTube", SpokenText.polish("Open you tube"))
    }

    @Test fun `first changing hypothesis stays provisional until words survive the stability interval`() {
        val preview = StreamingTranscriptPreview()
        assertEquals("", preview.update("Turn left", 0))
        assertEquals("", preview.update("Turn left", 200))
        assertEquals("Turn left", preview.update("Turn left", 240))
    }

    @Test fun `stable prefix grows without waiting for the entire sentence to stop changing`() {
        val preview = StreamingTranscriptPreview()
        assertEquals("", preview.update("Turn", 0))
        assertEquals("", preview.update("Turn into", 100))
        assertEquals("Turn", preview.update("Turn into a", 250))
        assertEquals("Turn into", preview.update("Turn into a cat", 350))
        assertEquals("Turn into a", preview.update("Turn into a cat", 500))
        assertEquals("Turn into a cat", preview.update("Turn into a cat", 600))
    }

    @Test fun `revised and shortened hypotheses retract obsolete suffix immediately`() {
        val preview = StreamingTranscriptPreview()
        preview.update("Turn left at home", 0)
        assertEquals("Turn left at home", preview.update("Turn left at home", 250))
        assertEquals("Turn", preview.update("Turn right", 300))
        assertEquals("Turn right", preview.update("Turn right", 550))
        assertEquals("Turn", preview.update("Turn", 600))
    }

    @Test fun `empty and annotation candidates clear old preview and reset its timeline`() {
        val preview = StreamingTranscriptPreview()
        preview.update("Open settings", 0)
        assertEquals("Open settings", preview.update("Open settings", 250))
        assertEquals("", preview.update("", 300))
        assertEquals("", preview.update("Open settings", 400))
        assertEquals("Open settings", preview.update("Open settings", 650))
        assertEquals("", preview.update("[music]", 700))
        assertEquals("", preview.update("Open settings", 800))
    }

    @Test fun `endpoint reset and stale or reversed clock prevent old word stability leaking`() {
        val preview = StreamingTranscriptPreview()
        preview.update("Play jazz", 100)
        assertEquals("Play jazz", preview.update("Play jazz", 400))
        assertEquals("", preview.update("Play jazz", 2500))
        assertEquals("Play jazz", preview.update("Play jazz", 2800))
        assertEquals("", preview.update("Play jazz", 1000))
        preview.reset()
        assertEquals("", preview.update("Play jazz", 1500))
    }

    @Test fun `noise fragments and oversized previews do not become display text`() {
        val preview = StreamingTranscriptPreview()
        preview.update("a b", 0)
        assertEquals("", preview.update("a b", 500))
        assertEquals("", preview.update("x".repeat(2049), 600))
        assertEquals("", preview.update("Open settings", -1))
    }

    @Test fun `local preview retraction clears live words but waits for the real final`() = runTest {
        val engine = FinalEngine()
        var final: String? = null
        var error: Int? = null
        val capture = UtteranceCapture(engine, this, now = { testScheduler.currentTime + 1L })
        capture.start(onFinal = { final = it }, onError = { error = it })
        runCurrent()
        engine.partialResults.value = "Open the files"
        runCurrent()
        assertEquals("Open the files", capture.live.value)
        engine.partialResults.value = ""
        runCurrent()
        assertEquals("", capture.live.value)
        assertTrue(capture.hasHeardSpeech())
        advanceTimeBy(9_000L)
        runCurrent()
        assertNull(final)
        assertNull(error)
        engine.result("What time is it")
        assertEquals("What time is it", final)
        assertNull(error)
    }

    @Test fun `local preview history resets when a new capture starts`() = runTest {
        val engine = FinalEngine()
        var error: Int? = null
        val capture = UtteranceCapture(engine, this, now = { testScheduler.currentTime + 1L })
        capture.start(onFinal = {}, onError = {})
        runCurrent()
        engine.partialResults.value = "Open settings"
        runCurrent()
        capture.cancel()
        engine.partialResults.value = ""
        capture.start(onFinal = {}, onError = { error = it })
        runCurrent()
        assertFalse(capture.hasHeardSpeech())
        advanceTimeBy(8_200L)
        runCurrent()
        assertEquals(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT, error)
    }

    @Test fun `capture ring retains the entire maximum endpoint instead of only the last twenty seconds`() {
        val count = AsrCaptureBounds.SAMPLE_RATE * AsrCaptureBounds.MAX_ENDPOINT_SECONDS
        val samples = ShortArray(count) { ((it / AsrCaptureBounds.SAMPLE_RATE) + 1).toShort() }
        val ring = PcmRingBuffer(AsrCaptureBounds.PCM_CAPACITY)
        ring.append(samples)
        assertArrayEquals(samples, ring.snapshot())
        assertEquals(1.toShort(), ring.snapshot().first())
        assertEquals(30.toShort(), ring.snapshot().last())
        assertTrue(AsrCaptureBounds.PCM_CAPACITY <= AsrCaptureBounds.SAMPLE_RATE * 32)
    }

    @Test fun `numeric diagnostics preserve PCM16 extremes and timebase without text or audio content`() {
        val summary = AsrPcmSummary.from(shortArrayOf(Short.MIN_VALUE, Short.MAX_VALUE, 0, 1, -1), 16_000)
        assertEquals(5, summary.samples)
        assertEquals(32_768, summary.peak)
        assertEquals(2, summary.nearFullScaleSamples)
        assertEquals(1, summary.zeroSamples)
        assertEquals(0L, summary.audioMs)
        assertEquals(1000L, AsrPcmSummary.from(ShortArray(16_000), 16_000).audioMs)
        assertEquals(AsrPcmSummary(0, 0, 0, 0, 0), AsrPcmSummary.from(ShortArray(0), 16_000))
    }

    @Test fun `two queued refinements deliver each final with its own audio before callback`() = runTest {
        val delivered = mutableListOf<Pair<String, List<Short>>>()
        var callbackAudio = ShortArray(0)
        val queue = SpeechFinalizer(this, refine = { pcm ->
            delay(100)
            "Final ${pcm.first()}"
        }, deliver = { text, pcm ->
            // Same ordering as the engine's main-thread delivery boundary.
            callbackAudio = pcm
            delivered += text to callbackAudio.toList()
        })
        val first = shortArrayOf(11, 12, 13)
        val second = shortArrayOf(21, 22)
        assertTrue(queue.offer("First streaming", first))
        runCurrent() // First native pass is in flight when the next endpoint arrives.
        assertTrue(queue.offer("Second streaming", second))
        assertTrue(callbackAudio.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("Final 11" to first.toList(), "Final 21" to second.toList()), delivered)
        queue.close()
    }

    @Test fun `streaming fallback retains pairing when refinement is unavailable`() = runTest {
        val delivered = mutableListOf<Pair<String, Short>>()
        val queue = SpeechFinalizer(this, refine = { null }, deliver = { text, pcm ->
            delivered += text to pcm.first()
        })
        queue.offer("First request", shortArrayOf(7))
        queue.offer("Second request", shortArrayOf(9))
        advanceUntilIdle()
        assertEquals(listOf("First request" to 7.toShort(), "Second request" to 9.toShort()), delivered)
        queue.close()
    }

    @Test fun `closing the listening session prevents both late paired audio and text delivery`() = runTest {
        var deliveries = 0
        var decodes = 0
        val queue = SpeechFinalizer(this, refine = {
            decodes++
            withContext(NonCancellable) { delay(100) }
            "Late result"
        }, deliver = { _, _ -> deliveries++ })
        queue.offer("First request", shortArrayOf(1))
        runCurrent()
        queue.offer("Second request", shortArrayOf(2))
        queue.close()
        advanceUntilIdle()
        assertEquals(1, decodes)
        assertEquals(0, deliveries)
        assertFalse(queue.offer("After stop", shortArrayOf(3)))
    }

    private class FinalEngine : SpeechEngine {
        override val deliversFinalResults = true
        override val isListening = MutableStateFlow(false)
        override val partialResults = MutableStateFlow("")
        private var onResult: (String) -> Unit = {}
        override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            this.onResult = onResult
            isListening.value = true
        }
        override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) { this.onResult = onResult }
        override fun stopListening() { isListening.value = false }
        override fun destroy() = stopListening()
        fun result(text: String) { onResult(text) }
    }
}
