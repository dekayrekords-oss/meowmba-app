package com.example.meowmba

import com.example.meowmba.core.audio.AudioPreRoll
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pre-roll is what lets "hey meow what time is it" keep working once a keyword
 * spotter replaces the ASR loop: the spotter reports only that the phrase fired, so the
 * command exists solely as audio. Wrap-around bugs here would silently truncate or
 * scramble commands, which is why every boundary is pinned.
 */
class AudioPreRollTest {

    private fun ramp(from: Int, count: Int) =
        ShortArray(count) { (from + it).toShort() }

    @Test
    fun `starts empty`() {
        val r = AudioPreRoll(8)
        assertEquals(0, r.size)
        assertArrayEquals(ShortArray(0), r.snapshot())
    }

    @Test
    fun `holds a partial fill in order`() {
        val r = AudioPreRoll(8)
        r.append(ramp(1, 3))
        assertEquals(3, r.size)
        assertArrayEquals(shortArrayOf(1, 2, 3), r.snapshot())
    }

    @Test
    fun `exact fill keeps everything`() {
        val r = AudioPreRoll(4)
        r.append(ramp(1, 4))
        assertArrayEquals(shortArrayOf(1, 2, 3, 4), r.snapshot())
    }

    @Test
    fun `overwrites oldest once full`() {
        val r = AudioPreRoll(4)
        r.append(ramp(1, 6))          // 1..6 into a 4-slot buffer
        assertEquals(4, r.size)
        assertArrayEquals(shortArrayOf(3, 4, 5, 6), r.snapshot())
    }

    @Test
    fun `wraps correctly across several appends`() {
        val r = AudioPreRoll(5)
        r.append(ramp(1, 3))          // [1 2 3]
        r.append(ramp(4, 4))          // 4 5 6 7 -> keeps last 5: 3..7
        assertEquals(5, r.size)
        assertArrayEquals(shortArrayOf(3, 4, 5, 6, 7), r.snapshot())
    }

    @Test
    fun `a single append larger than capacity keeps the tail`() {
        val r = AudioPreRoll(3)
        r.append(ramp(1, 10))
        assertArrayEquals(shortArrayOf(8, 9, 10), r.snapshot())
    }

    @Test
    fun `honours a count smaller than the array`() {
        // AudioRecord.read returns how many samples were actually read; the rest of the
        // array is stale and must not be captured.
        val r = AudioPreRoll(8)
        val chunk = ramp(1, 8)
        r.append(chunk, count = 3)
        assertArrayEquals(shortArrayOf(1, 2, 3), r.snapshot())
    }

    @Test
    fun `clear resets`() {
        val r = AudioPreRoll(4)
        r.append(ramp(1, 4))
        r.clear()
        assertEquals(0, r.size)
        assertArrayEquals(ShortArray(0), r.snapshot())
    }

    @Test
    fun `empty and zero-count appends are no-ops`() {
        val r = AudioPreRoll(4)
        r.append(ShortArray(0))
        r.append(ramp(1, 4), count = 0)
        assertEquals(0, r.size)
    }

    @Test
    fun `forSeconds sizes by sample rate`() {
        assertEquals(0, AudioPreRoll.forSeconds(2f, 16_000).size)
        // 2s @ 16kHz = 32000 samples; fill past it and confirm the cap.
        val r = AudioPreRoll.forSeconds(2f, 16_000)
        r.append(ShortArray(40_000) { 7 })
        assertEquals(32_000, r.size)
    }
}
