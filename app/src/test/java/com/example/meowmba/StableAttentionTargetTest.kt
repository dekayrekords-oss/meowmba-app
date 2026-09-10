package com.example.meowmba

import com.example.meowmba.core.vision.SeenFace
import com.example.meowmba.core.vision.StableAttentionTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StableAttentionTargetTest {
    private fun face(id: Int, x: Float, area: Float = 100f, mouth: Float = 0f) =
        SeenFace(id, x, 0f, 1f, mouth, area)

    @Test
    fun `area jitter between two people does not change attention`() {
        val selector = StableAttentionTarget()
        selector.select(listOf(face(1, -0.5f), face(2, 0.5f, 95f)), false, 1_000L)
        repeat(40) { index ->
            val secondArea = if (index % 2 == 0) 108f else 96f
            val result = selector.select(listOf(face(2, 0.5f, secondArea), face(1, -0.5f)), false, 1_100L + index * 100L)
            assertEquals(1, result.face?.trackingId)
            assertFalse(result.changed)
        }
    }

    @Test
    fun `larger challenger must remain eligible for the whole sustain window`() {
        val selector = StableAttentionTarget()
        selector.select(listOf(face(1, -0.5f)), false, 1_000L)
        val both = listOf(face(1, -0.5f), face(2, 0.5f, 160f))
        for (time in listOf(1_100L, 1_300L, 1_500L, 1_749L)) {
            assertEquals(1, selector.select(both, false, time).face?.trackingId)
        }
        val switched = selector.select(both, false, 1_750L)
        assertEquals(2, switched.face?.trackingId)
        assertTrue(switched.changed)
    }

    @Test
    fun `an interrupted challenger does not accumulate evidence`() {
        val selector = StableAttentionTarget()
        selector.select(listOf(face(1, -0.5f)), false, 1_000L)
        val both = listOf(face(1, -0.5f), face(2, 0.5f, 160f))
        selector.select(both, false, 1_100L)
        selector.select(listOf(face(1, -0.5f)), false, 1_400L)
        assertEquals(1, selector.select(both, false, 1_750L).face?.trackingId)
    }

    @Test
    fun `missing target is immediately absent even when another person remains`() {
        val selector = StableAttentionTarget()
        selector.select(listOf(face(1, -0.5f)), false, 1_000L)
        assertNull(selector.select(listOf(face(2, 0.5f)), false, 1_200L).face)
        val reacquired = selector.select(listOf(face(1, -0.45f), face(2, 0.5f)), false, 1_350L)
        assertEquals(1, reacquired.face?.trackingId)
        assertFalse(reacquired.changed)
    }

    @Test
    fun `persistent target loss permits reacquisition after bounded grace`() {
        val selector = StableAttentionTarget()
        selector.select(listOf(face(1, -0.5f)), false, 1_000L)
        assertNull(selector.select(emptyList(), false, 1_100L).face)
        val reacquired = selector.select(listOf(face(2, 0.5f)), false, 1_400L)
        assertEquals(2, reacquired.face?.trackingId)
        assertTrue(reacquired.changed)
    }

    @Test
    fun `unknown tracking IDs follow position instead of list order`() {
        val selector = StableAttentionTarget()
        selector.select(listOf(face(-1, -0.6f, 105f), face(-1, 0.6f)), false, 1_000L)
        val result = selector.select(listOf(face(-1, 0.6f, 110f), face(-1, -0.58f)), false, 1_100L)
        assertEquals(-0.58f, result.face!!.offsetX, 0.001f)
        assertFalse(result.changed)
    }

    @Test
    fun `known target ID wins over a nearer unknown box`() {
        val selector = StableAttentionTarget()
        selector.select(listOf(face(1, -0.5f)), false, 1_000L)
        val result = selector.select(listOf(face(-1, -0.5f), face(1, -0.4f)), false, 1_100L)
        assertEquals(1, result.face?.trackingId)
    }

    @Test
    fun `mouth evidence switches only after sustained lead`() {
        val selector = StableAttentionTarget()
        selector.select(listOf(face(1, -0.5f)), false, 1_000L)
        val both = listOf(face(1, -0.5f, mouth = 0.02f), face(2, 0.5f, mouth = 0.15f))
        for (time in listOf(1_100L, 1_300L, 1_500L)) {
            assertEquals(1, selector.select(both, true, time).face?.trackingId)
        }
        assertEquals(2, selector.select(both, true, 1_750L).face?.trackingId)
    }

    @Test
    fun `a stalled camera does not count as sustained challenger evidence`() {
        val selector = StableAttentionTarget()
        selector.select(listOf(face(1, -0.5f)), false, 1_000L)
        val both = listOf(face(1, -0.5f), face(2, 0.5f, 160f))
        selector.select(both, false, 1_100L)
        assertEquals(1, selector.select(both, false, 2_100L).face?.trackingId)
    }

    @Test
    fun `reset forgets the prior persons target`() {
        val selector = StableAttentionTarget()
        selector.select(listOf(face(1, -0.5f)), false, 1_000L)
        selector.reset()
        val result = selector.select(listOf(face(2, 0.5f)), false, 1_100L)
        assertEquals(2, result.face?.trackingId)
        assertTrue(result.changed)
    }

    @Test
    fun `non-finite and degenerate detections are never targets`() {
        val selector = StableAttentionTarget()
        assertNull(selector.select(listOf(face(1, Float.NaN), face(2, 0f, 0f)), false, 1_000L).face)
    }
}
