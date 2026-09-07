package com.example.meowmba.core.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThermalAudioTest {
    @Test fun `digital silence gate preserves quiet input and endpoint tail`() {
        val gate = DigitalSilenceGate()
        assertFalse(gate.shouldDecode(ShortArray(1600), 1600))
        assertFalse(gate.shouldDecode(shortArrayOf(0, 0, 5), 2))
        assertTrue(gate.shouldDecode(shortArrayOf(0, 1), 2))
        repeat(32) { assertTrue(gate.shouldDecode(ShortArray(1600), 1600)) }
        gate.reset()
        assertFalse(gate.shouldDecode(ShortArray(1600), 1600))
        assertTrue(gate.shouldDecode(shortArrayOf(-1), 1))
    }

    @Test fun `annotation only finals cannot trigger chat or restore streaming hallucination`() {
        for (noise in listOf("(buzzing)", "[Music]", "[BLANK_AUDIO]", "(silence)", "(Breathing).", "[music] (applause)")) {
            assertFalse(noise, SpokenText.isUsable(noise))
            assertEquals("", SpokenText.preferFinal(noise, "Hello how can I help you today"))
            assertNull(SpokenText.refinedTranscript("Hello how can I help you today", noise))
        }
        assertNull(SpokenText.refinedTranscript("Hello", ""))
        assertEquals("Hello", SpokenText.refinedTranscript("Hello", null))
        for (speech in listOf("Why is my phone buzzing?", "Play music", "(Open the files)", "[music] stop playing that")) {
            assertTrue(speech, SpokenText.isUsable(speech))
        }
    }

    @Test fun `ring is bounded chronological and snapshots do not alias`() {
        val ring = PcmRingBuffer(5)
        ring.append(shortArrayOf(1, 2, 3))
        val snapshot = ring.snapshot()
        ring.append(shortArrayOf(4, 5, 6, 7))
        assertArrayEquals(shortArrayOf(3, 4, 5, 6, 7), ring.snapshot())
        assertArrayEquals(shortArrayOf(1, 2, 3), snapshot)
        assertEquals(5, ring.size)
        ring.clear()
        assertArrayEquals(shortArrayOf(), ring.snapshot())
        ring.append(shortArrayOf(9, 10))
        assertArrayEquals(shortArrayOf(9, 10), ring.snapshot())
    }

    @Test fun `8k SCO samples match advertised 16k timebase including partial reads`() {
        val ring = PcmRingBuffer(8)
        ring.append(shortArrayOf(1, 2, 99), count = 2, repeatEach = 2)
        assertArrayEquals(shortArrayOf(1, 1, 2, 2), ring.snapshot())
    }

    @Test fun `refinement serializes endpoints and delivers in order`() = runTest {
        var concurrent = 0
        var peak = 0
        val results = mutableListOf<String>()
        val queue = SpeechFinalizer(this, refine = {
            concurrent++
            peak = maxOf(peak, concurrent)
            delay(100)
            concurrent--
            "Result ${it[0]}"
        }, deliver = { results.add(it) })
        repeat(4) { assertTrue(queue.offer("Streaming", shortArrayOf((it + 1).toShort()))) }
        advanceUntilIdle()
        assertEquals(1, peak)
        assertEquals(listOf("Result 1", "Result 2", "Result 3", "Result 4"), results)
        queue.close()
    }

    @Test fun `stop cancels pending work and never delivers late final`() = runTest {
        var decodes = 0
        val results = mutableListOf<String>()
        val queue = SpeechFinalizer(this, refine = {
            decodes++
            // Native decode cannot stop halfway through a JNI call.
            withContext(NonCancellable) { delay(1000) }
            "Hello"
        }, deliver = { results.add(it) })
        repeat(3) { queue.offer("Streaming", shortArrayOf(1)) }
        runCurrent()
        queue.close()
        advanceUntilIdle()
        assertEquals(1, decodes)
        assertTrue(results.isEmpty())
        assertFalse(queue.offer("Late", shortArrayOf(2)))
    }

    @Test fun `overload is bounded and failed refinement can recover without wedging worker`() = runTest {
        val results = mutableListOf<String>()
        val queue = SpeechFinalizer(this, refine = { throw IllegalStateException("unavailable") },
            deliver = { results.add(it) }, capacity = 2)
        assertTrue(queue.offer("First request", shortArrayOf(1)))
        assertTrue(queue.offer("Second request", shortArrayOf(2)))
        assertFalse(queue.offer("Overflow", shortArrayOf(3)))
        advanceUntilIdle()
        assertEquals(listOf("First request", "Second request"), results)
        queue.close()
    }

    @Test fun `exact hardware mute skips inference but quiet audio still reaches refiner`() = runTest {
        var decodes = 0
        val results = mutableListOf<String>()
        val queue = SpeechFinalizer(this, refine = { decodes++; "Quiet speech" }, deliver = { results.add(it) })
        queue.offer("And", ShortArray(16000))
        queue.offer("Quiet speech", shortArrayOf(0, 1, 0, -1))
        advanceUntilIdle()
        assertEquals(1, decodes)
        assertEquals(listOf("Quiet speech"), results)
        queue.close()
    }
}
