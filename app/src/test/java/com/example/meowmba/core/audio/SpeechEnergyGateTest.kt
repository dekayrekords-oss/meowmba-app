package com.example.meowmba.core.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate that decides whether the always-on recognizer spends encoder time on
 * a chunk. These tests pin the two failure modes it replaced:
 *
 * 1. The old digital-zero test opened on any non-zero sample and latched, so a
 *    quiet room kept the zipformer decoding forever. Hence the room-tone case.
 * 2. A gate that releases too eagerly stops the decoder before sherpa's own
 *    1.6 s trailing-silence endpoint rule can fire, and the transcript never
 *    lands at all. Hence the hangover cases — they are the ones that matter if
 *    someone is tempted to shorten [SpeechEnergyGate] for a CPU win.
 */
class SpeechEnergyGateTest {

    private val gate get() = SpeechEnergyGate(SAMPLE_RATE)

    @Test
    fun `room tone never opens the gate`() {
        val g = gate
        // Roughly -48 dBFS: what a phone mic picks up in a silent room, and
        // comfortably non-zero. The old gate latched open on this.
        val tone = chunk(rms = 0.004)
        repeat(200) { assertFalse("opened on ambient noise", g.shouldDecode(tone, tone.size)) }
    }

    @Test
    fun `speech opens the gate`() {
        val g = gate
        assertTrue(g.shouldDecode(chunk(rms = 0.05), CHUNK))
    }

    @Test
    fun `a pause between words does not close the gate`() {
        val g = gate
        assertTrue(g.shouldDecode(chunk(rms = 0.05), CHUNK))
        // A breath or a gap between words, well inside the hangover.
        repeat(5) { assertTrue(g.shouldDecode(chunk(rms = 0.0005), CHUNK)) }
        assertTrue(g.shouldDecode(chunk(rms = 0.05), CHUNK))
    }

    @Test
    fun `the hangover still covers the recognizer endpoint rule`() {
        val g = gate
        assertTrue(g.shouldDecode(chunk(rms = 0.05), CHUNK))
        // Sherpa endpoints on 1.6 s of trailing silence. The decoder must still
        // be running when that moment arrives or the utterance is dropped.
        val quiet = chunk(rms = 0.0005)
        repeat(16) { assertTrue(g.shouldDecode(quiet, CHUNK)) }   // 1.6 s
        assertTrue("closed before the endpoint rule could fire", g.shouldDecode(quiet, CHUNK))
    }

    @Test
    fun `sustained quiet eventually releases the decoder`() {
        val g = gate
        assertTrue(g.shouldDecode(chunk(rms = 0.05), CHUNK))
        val quiet = chunk(rms = 0.0005)
        // 1.8 s hangover, then it lets go. This is the CPU saving.
        repeat(17) { g.shouldDecode(quiet, CHUNK) }
        assertFalse("still decoding after the hangover", g.shouldDecode(quiet, CHUNK))
    }

    @Test
    fun `reset closes the gate immediately`() {
        val g = gate
        assertTrue(g.shouldDecode(chunk(rms = 0.05), CHUNK))
        g.reset()
        assertFalse(g.shouldDecode(chunk(rms = 0.004), CHUNK))
    }

    @Test
    fun `an empty chunk keeps the current state`() {
        val g = gate
        assertFalse(g.shouldDecode(ShortArray(0), 0))
        assertTrue(g.shouldDecode(chunk(rms = 0.05), CHUNK))
        assertTrue(g.shouldDecode(ShortArray(0), 0))
    }

    @Test
    fun `thermal scale raises the floor for quiet without muting real speech`() {
        val g = gate
        g.setThresholdScale(2.0f)
        // Above the 0.020 default, below the 0.040 raised floor: a hot device
        // declines to spend decoder time on this.
        assertFalse(g.shouldDecode(chunk(rms = 0.025), CHUNK))
        assertTrue(g.shouldDecode(chunk(rms = 0.10), CHUNK))
    }

    @Test
    fun `thermal scale is reversible when the device cools`() {
        val g = gate
        g.setThresholdScale(2.0f)
        assertFalse(g.shouldDecode(chunk(rms = 0.025), CHUNK))
        g.setThresholdScale(1.0f)
        assertTrue(g.shouldDecode(chunk(rms = 0.025), CHUNK))
    }

    @Test
    fun `a scale below one is clamped so nobody can tune the gate looser than designed`() {
        val g = gate
        g.setThresholdScale(0.5f)
        assertTrue(g.shouldDecode(chunk(rms = 0.05), CHUNK))
        g.reset()
        // 0.015 would clear a halved 0.010 threshold. It must not: the default
        // floor is the one tuned against the endpoint rule.
        assertFalse(g.shouldDecode(chunk(rms = 0.015), CHUNK))

        val nan = gate
        nan.setThresholdScale(Float.NaN)
        assertFalse(nan.shouldDecode(chunk(rms = 0.015), CHUNK))
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK = SAMPLE_RATE / 10

        /**
         * A chunk whose RMS is exactly [rms]: alternating signs of a constant
         * amplitude, so the test does not depend on a waveform's shape.
         */
        private fun chunk(rms: Double, samples: Int = CHUNK): ShortArray {
            val amp = (rms * 32_767.0).toInt().coerceIn(-32_767, 32_767)
            return ShortArray(samples) { i -> if (i % 2 == 0) amp.toShort() else (-amp).toShort() }
        }
    }
}
