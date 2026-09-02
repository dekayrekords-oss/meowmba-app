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
        val onScreenRight = KeepInFrame.step(0.40f, 0f, 1f, 1_000, 1_000, 0f, t)
        val t2 = KeepInFrame.Tracker()
        val onScreenLeft = KeepInFrame.step(-0.40f, 0f, 1f, 1_000, 1_000, 0f, t2)
        assertTrue(
            "person on the right of a facing camera is the robot's right",
            onScreenRight.turnSpeed > 0,
        )
        assertTrue(
            "person on the left of a facing camera is the robot's left",
            onScreenLeft.turnSpeed < 0,
        )
        assertTrue(onScreenRight.turning)
    }

    @Test
    fun `small face-box jitter does not start a turn`() {
        val t = KeepInFrame.Tracker()
        val c = KeepInFrame.step(0.06f, 0f, 1f, 1_000, 1_000, 0f, t)
        assertFalse(c.turning)
    }

    @Test
    fun `a clearly off-center face starts a turn`() {
        val t = KeepInFrame.Tracker()
        val c = KeepInFrame.step(0.28f, 0f, 1f, 1_000, 1_000, 0f, t)
        assertTrue(c.turning)
    }

    @Test
    fun `once turning it holds through the start band then stops when centered`() {
        val t = KeepInFrame.Tracker()
        assertTrue(KeepInFrame.command(0.45f, 0f, 1_000L, t).turning)
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
        t.sign = 1
        t.signAtMs = 1_000L
        val flip = KeepInFrame.command(-0.45f, 0f, 1_200L, t)
        assertTrue(
            "must not reverse inside the lock window, got ${flip.turnSpeed}",
            flip.turnSpeed >= 0,
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
        assertTrue(KeepInFrame.step(0.40f, 0f, 1f, 1_000, 1_000, 0.40f, t).turning)
        val miss = KeepInFrame.step(null, null, null, nowMs = 1_200, lastSeenMs = 1_000, lastX = 0.40f, tracker = t)
        assertFalse("lost lock must not keep turning", miss.turning)
        assertFalse(miss.searching)
        assertTrue("tracker must survive a 200ms blink", t.hasBody)
    }

    @Test
    fun `a lost face sweeps the way they left, then rests`() {
        val t = KeepInFrame.Tracker()
        assertTrue(KeepInFrame.step(0.40f, 0f, 1f, 1_000, 1_000, 0.40f, t).turning)
        // A 200ms blink is still a lock, not a loss.
        val blink = KeepInFrame.step(null, null, null, nowMs = 1_200, lastSeenMs = 1_000, lastX = 0.40f, tracker = t)
        assertFalse(blink.searching)
        assertFalse(blink.turning)
        // Past the hold they left to the right, so sweep right.
        val sweep = KeepInFrame.step(null, null, null, nowMs = 1_700, lastSeenMs = 1_000, lastX = 0.40f, tracker = t)
        assertTrue("a lost face must be searched for, got $sweep", sweep.searching)
        assertTrue(sweep.turnSpeed > 0)
        // One burst is bounded: SEARCH_GIVE_UP_MS after it started the base
        // rests instead of hunting (started 1_700, so 4_600 is past it).
        val rested = KeepInFrame.step(null, null, null, nowMs = 4_600, lastSeenMs = 1_000, lastX = 0.40f, tracker = t)
        assertFalse("search must rest between bursts", rested.turning)
        // ...and sweeps back the other way once the SEARCH_PAUSE_MS rest is over.
        val back = KeepInFrame.step(null, null, null, nowMs = 6_600, lastSeenMs = 1_000, lastX = 0.40f, tracker = t)
        assertTrue("the second sweep goes back the other way, got $back", back.turnSpeed < 0)
    }

    @Test
    fun `searching eventually stops instead of hunting forever`() {
        val t = KeepInFrame.Tracker()
        assertTrue(KeepInFrame.step(0.40f, 0f, 1f, 1_000, 1_000, 0.40f, t).turning)
        var now = 1_500L
        var bursts = 0
        while (now < 60_000L) {
            val c = KeepInFrame.step(null, null, null, nowMs = now, lastSeenMs = 1_000, lastX = 0.40f, tracker = t)
            if (c.turning) bursts++
            now += 100L
        }
        // 3 bursts x 2.8s of sweeping, sampled every 100ms, and nothing after.
        assertTrue("expected a bounded number of search bursts, got $bursts", bursts in 70..95)
        val done = KeepInFrame.step(null, null, null, nowMs = 60_000, lastSeenMs = 1_000, lastX = 0.40f, tracker = t)
        assertFalse("after the burst budget the base stands still", done.turning)
        assertFalse("and the lock is dropped so a new face starts clean", t.hasBody)
    }

    @Test
    fun `a face at the lip of the frame is turned back in, not braked`() {
        val t = KeepInFrame.Tracker()
        assertTrue(KeepInFrame.command(0.40f, 0f, 1_000L, t).turning)
        val edge = KeepInFrame.command(0.55f, 0f, 1_200L, t)
        assertTrue("half out of frame is exactly when to turn, got ${edge.turnSpeed}", edge.turning)
        assertTrue("the edge turn is capped, got ${edge.turnSpeed}", kotlin.math.abs(edge.turnSpeed) <= KeepInFrame.EDGE_SPEED)
        assertFalse(edge.driving)
    }
}
