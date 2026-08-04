package com.example.meowmba

import com.example.meowmba.core.audio.WindowCarry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the VAD window carry. The bug being guarded against: a 1600-sample
 * capture frame leaves 64 samples after 512-sample window alignment, and the old code
 * counted them but threw them away — 4% of the audio never reached the VAD and every
 * window boundary drifted. The carry must keep every sample and replay it exactly once.
 */
class VadGateCarryTest {

    @Test
    fun `a 1600-sample frame delivers 1536 and keeps the 64 remainder`() {
        val carry = WindowCarry(512)
        val out = carry.feed(FloatArray(1600))
        assertEquals(1536, out.size)
        assertEquals(64, carry.pendingSamples)
    }

    @Test
    fun `every sample reaches the VAD exactly once, in order`() {
        val carry = WindowCarry(512)
        fun frame(offset: Int) = FloatArray(1600) { (((offset + it) % 1000) / 1000f) * 0.5f }

        val input = ArrayList<Float>()
        val delivered = ArrayList<Float>()
        repeat(10) { f ->
            val frame = frame(f * 1600)
            input.addAll(frame.toList())
            delivered.addAll(carry.feed(frame).toList())
        }
        // Conservation: everything fed is either delivered or still pending — nothing lost.
        assertEquals(input.size, delivered.size + carry.pendingSamples)
        // Delivered stream matches the input order within int16 quantisation tolerance.
        for (i in delivered.indices) {
            assertEquals(input[i], delivered[i], 1.0f / 32_768f)
        }
    }

    @Test
    fun `an exact multiple of the window leaves nothing pending`() {
        val carry = WindowCarry(512)
        assertEquals(1024, carry.feed(FloatArray(1024)).size)
        assertEquals(0, carry.pendingSamples)
    }

    @Test
    fun `sub-window frames accumulate until a window fills`() {
        val carry = WindowCarry(512)
        assertEquals(0, carry.feed(FloatArray(200)).size)
        assertEquals(200, carry.pendingSamples)
        assertEquals(512, carry.feed(FloatArray(400)).size)
        assertEquals(88, carry.pendingSamples)
    }

    @Test
    fun `clear drops the pending remainder`() {
        val carry = WindowCarry(512)
        carry.feed(FloatArray(1600))
        assertEquals(64, carry.pendingSamples)
        carry.clear()
        assertEquals(0, carry.pendingSamples)
        assertEquals(1536, carry.feed(FloatArray(1600)).size)
    }
}
