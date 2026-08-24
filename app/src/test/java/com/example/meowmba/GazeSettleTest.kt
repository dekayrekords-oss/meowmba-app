package com.example.meowmba

import com.example.meowmba.core.vision.FollowSource
import com.example.meowmba.core.vision.GazeSettle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GazeSettleTest {

    @Test
    fun `a centered face looks straight ahead`() {
        val r = GazeSettle.apply(0.08f, -0.05f, settled = false, FollowSource.FACE)
        assertEquals(0f, r.x, 1e-4f)
        assertEquals(-0.05f, r.y, 1e-4f)
        assertTrue(r.settled)
    }

    @Test
    fun `a high selfie face still settles on X`() {
        val r = GazeSettle.apply(0.05f, -0.50f, settled = false, FollowSource.FACE)
        assertTrue(r.settled)
        assertEquals(0f, r.x, 1e-4f)
        // Zeroing Y here was "tracking is dead" in portrait and landscape:
        // a face in the upper third never moved the head or the neck.
        assertEquals(-0.50f, r.y, 1e-4f)
    }

    @Test
    fun `landscape vertical motion is not erased just because X is near center`() {
        val r = GazeSettle.apply(0.04f, 0.62f, settled = false, FollowSource.FACE)
        assertTrue(r.settled)
        assertEquals(0.62f, r.y, 1e-4f)
    }

    @Test
    fun `once settled it holds through the inner band`() {
        val hold = GazeSettle.apply(0.20f, 0f, settled = true, FollowSource.FACE)
        assertTrue(hold.settled)
        assertEquals(0f, hold.x, 1e-4f)
        val leave = GazeSettle.apply(0.40f, 0f, settled = true, FollowSource.FACE)
        assertFalse(leave.settled)
        assertEquals(0.40f, leave.x, 1e-4f)
    }

    @Test
    fun `a hand is never settled to center`() {
        val r = GazeSettle.apply(0.08f, 0.04f, settled = true, FollowSource.HAND)
        assertFalse(r.settled)
        assertEquals(0.08f, r.x, 1e-4f)
    }
}
