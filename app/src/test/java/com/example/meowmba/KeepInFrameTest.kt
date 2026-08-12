package com.example.meowmba

import com.example.meowmba.core.robot.KeepInFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepInFrameTest {

    @Test
    fun `centered face is a hold`() {
        val c = KeepInFrame.step(0f, 0f, 1f, nowMs = 1_000, lastSeenMs = 1_000, lastX = 0f)
        assertEquals(0f, c.neckPitch, 0.001f)
        assertFalse(c.turning)
        assertFalse(c.driving)
        assertFalse(c.searching)
    }

    @Test
    fun `front camera subject on the right turns the robot left`() {
        val t = KeepInFrame.Tracker()
        val onScreenRight = KeepInFrame.step(0.7f, 0f, 1f, 1_000, 1_000, 0f, t)
        val t2 = KeepInFrame.Tracker()
        val onScreenLeft = KeepInFrame.step(-0.7f, 0f, 1f, 1_000, 1_000, 0f, t2)
        assertTrue(
            "person on the right of a facing camera is the robot's left",
            onScreenRight.turnSpeed < 0,
        )
        assertTrue(
            "person on the left of a facing camera is the robot's right",
            onScreenLeft.turnSpeed > 0,
        )
        assertTrue(onScreenRight.turning)
    }

    @Test
    fun `small face-box jitter does not start a turn`() {
        val t = KeepInFrame.Tracker()
        val c = KeepInFrame.step(0.28f, 0f, 1f, 1_000, 1_000, 0f, t)
        assertFalse(c.turning)
    }

    @Test
    fun `once turning it holds through the start band then stops when centered`() {
        val t = KeepInFrame.Tracker()
        assertTrue(KeepInFrame.command(0.70f, 0f, 1_000L, t).turning)
        assertTrue(
            "hysteresis must keep the turn going at 0.25",
            KeepInFrame.command(0.25f, 0f, 1_200L, t).turning,
        )
        assertFalse(KeepInFrame.command(0.05f, 0f, 2_000L, t).turning)
    }

    @Test
    fun `a brief sign flip is ignored`() {
        val t = KeepInFrame.Tracker()
        t.turning = true
        t.sign = -1
        t.signAtMs = 1_000L
        val flip = KeepInFrame.command(-0.45f, 0f, 1_200L, t)
        assertTrue(
            "must not reverse inside the lock window, got ${flip.turnSpeed}",
            flip.turnSpeed <= 0,
        )
    }

    @Test
    fun `face lock does not scoot on a far scale reading`() {
        val aimed = KeepInFrame.step(0.1f, 0f, 0.55f, 1_000, 1_000, 0f)
        assertFalse(aimed.driving)
    }

    @Test
    fun `a brief lost frame does not wipe the lock`() {
        val t = KeepInFrame.Tracker()
        assertTrue(KeepInFrame.step(0.70f, 0f, 1f, 1_000, 1_000, 0.70f, t).turning)
        val miss = KeepInFrame.step(null, null, null, nowMs = 1_200, lastSeenMs = 1_000, lastX = 0.70f, tracker = t)
        assertFalse(miss.turning)
        assertTrue("tracker must survive a 200ms blink", t.hasBody)
    }

    @Test
    fun `lost face holds still instead of search-spinning`() {
        val hold = KeepInFrame.step(null, null, null, nowMs = 1_200, lastSeenMs = 1_000, lastX = 0.4f)
        assertFalse(hold.searching)
        assertFalse(hold.turning)
        val later = KeepInFrame.step(null, null, null, nowMs = 1_700, lastSeenMs = 1_000, lastX = 0.4f)
        assertFalse(later.searching)
        assertFalse(later.turning)
    }
}
