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
    fun `screen side produces matching signed intent without claiming verified motor direction`() {
        val t = KeepInFrame.Tracker()
        val onScreenRight = KeepInFrame.step(0.40f, 0f, 1f, 1_000, 1_000, 0f, t)
        val t2 = KeepInFrame.Tracker()
        val onScreenLeft = KeepInFrame.step(-0.40f, 0f, 1f, 1_000, 1_000, 0f, t2)
        assertTrue(
            "screen-right produces positive intent",
            onScreenRight.turnSpeed > 0,
        )
        assertTrue(
            "screen-left produces negative intent",
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
    fun `a lost face holds only briefly and never searches`() {
        val t = KeepInFrame.Tracker()
        assertTrue(KeepInFrame.step(0.40f, -0.5f, 1f, 1_000, 1_000, 0.40f, t).turning)
        val blink = KeepInFrame.step(null, null, null, nowMs = 1_200, lastSeenMs = 1_000, lastX = 0.40f, tracker = t)
        assertStopped(blink)
        assertEquals(-0.2f, blink.neckPitch, 0.0001f)
        assertTrue(t.hasBody)
        val expired = KeepInFrame.step(null, null, null, 1_400, 1_000, 0.40f, t)
        assertStopped(expired)
        assertEquals(0f, expired.neckPitch, 0f)
        assertFalse(t.hasBody)
        assertEquals(0, t.sign)
    }

    @Test
    fun `missing face never emits a search pulse even after repeated long gaps`() {
        val t = KeepInFrame.Tracker()
        assertTrue(KeepInFrame.step(0.40f, 0f, 1f, 1_000, 1_000, 0.40f, t).turning)
        var now = 1_001L
        while (now < 120_000L) {
            val c = KeepInFrame.step(null, null, null, nowMs = now, lastSeenMs = 1_000, lastX = 0.40f, tracker = t)
            assertStopped(c)
            now += 100L
        }
        assertFalse(t.hasBody)
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

    @Test
    fun `partially missing coordinates stop and do not start a search`() {
        listOf(null to 0.7f, 0.7f to null).forEach { (x, y) ->
            val t = KeepInFrame.Tracker()
            KeepInFrame.step(0.7f, 0.3f, 1f, 1_000, 1_000, 0f, t)
            assertStopped(KeepInFrame.step(x, y, 1f, 1_100, 1_000, 0.7f, t))
            assertFalse(t.turning)
        }
    }

    @Test
    fun `nonfinite samples reset the filter and the next valid observation recovers`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            listOf(
                listOf(invalid, 0.4f, 1f, 0.4f),
                listOf(0.4f, invalid, 1f, 0.4f),
                listOf(0.4f, 0.4f, invalid, 0.4f),
                listOf(0.4f, 0.4f, 1f, invalid),
            ).forEach { (x, y, scale, lastX) ->
                val t = KeepInFrame.Tracker()
                KeepInFrame.step(0.7f, 0.5f, 1f, 1_000, 1_000, 0f, t)
                assertStopped(KeepInFrame.step(x, y, scale, 1_100, 1_000, lastX, t))
                assertFalse(t.hasBody)
                assertEquals(0f, t.bodyX, 0f)
                assertEquals(0f, t.bodyY, 0f)
                val recovered = KeepInFrame.step(-0.4f, -0.5f, 1f, 1_200, 1_200, 0f, t)
                assertTrue(recovered.turnSpeed < 0)
                assertEquals(-0.2f, recovered.neckPitch, 0.0001f)
            }
        }
    }

    @Test
    fun `poisoned mutable state is cleared on valid or missing samples`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -2f).forEach { poison ->
            listOf(true, false).forEach { observed ->
                val t = KeepInFrame.Tracker().apply {
                    hasBody = true
                    turning = true
                    bodyY = poison
                }
                val c = KeepInFrame.step(if (observed) 0.6f else null, if (observed) 0.5f else null,
                    1f, 1_000, 900, 0.6f, t)
                assertStopped(c)
                assertEquals(0f, c.neckPitch, 0f)
                assertFalse(t.hasBody)
            }
        }
    }

    @Test
    fun `direct command also rejects nonfinite input and clears a direction lock`() {
        val t = KeepInFrame.Tracker()
        KeepInFrame.command(0.6f, 0.4f, 1_000, t)
        assertStopped(KeepInFrame.command(Float.NaN, 0.4f, 1_100, t))
        assertEquals(0, t.sign)
        assertStopped(KeepInFrame.command(0.4f, Float.POSITIVE_INFINITY, 1_200, t))
    }

    @Test
    fun `time based smoothing agrees across 10 20 50 and 62 Hz callbacks`() {
        val results = listOf(100L, 50L, 20L, 16L).map { interval ->
            val t = KeepInFrame.Tracker()
            KeepInFrame.step(0f, 0f, 1f, 0, 0, 0f, t)
            var now = interval
            while (now <= 400L) {
                KeepInFrame.step(0.6f, -0.6f, 1f, now, now, 0f, t)
                now += interval
            }
            t.bodyX to t.bodyY
        }
        results.forEach { (x, y) ->
            assertEquals(results.first().first, x, 0.00001f)
            assertEquals(results.first().second, y, 0.00001f)
            assertTrue(x in 0.51f..0.53f)
        }
    }

    @Test
    fun `duplicate timestamps neither accelerate the filter nor replay turns`() {
        val t = KeepInFrame.Tracker()
        KeepInFrame.step(0.4f, 0.5f, 1f, 1_000, 1_000, 0f, t)
        repeat(100) {
            assertStopped(KeepInFrame.step(-0.9f, -0.9f, 1f, 1_000, 1_000, 0f, t))
        }
        assertEquals(0.4f, t.bodyX, 0f)
        assertEquals(0.5f, t.bodyY, 0f)
    }

    @Test
    fun `regressed time stops and clears stale filter before reacquisition`() {
        val t = KeepInFrame.Tracker()
        KeepInFrame.step(0.6f, 0.5f, 1f, 1_000, 1_000, 0f, t)
        assertStopped(KeepInFrame.step(-0.6f, 0f, 1f, 900, 900, 0f, t))
        assertFalse(t.hasBody)
        assertTrue(KeepInFrame.step(-0.6f, 0f, 1f, 950, 950, 0f, t).turnSpeed < 0)
    }

    @Test
    fun `finite out of range samples are bounded and smoothing does not overshoot`() {
        val t = KeepInFrame.Tracker()
        val extreme = KeepInFrame.step(20f, -20f, 1f, 1_000, 1_000, 0f, t)
        assertTrue(extreme.turnSpeed <= KeepInFrame.EDGE_SPEED)
        assertEquals(-0.4f, extreme.neckPitch, 0.0001f)
        var previous = t.bodyX
        for (now in 1_020L..2_000L step 20) {
            val c = KeepInFrame.step(0f, 0f, 1f, now, now, 0f, t)
            assertTrue(t.bodyX in 0f..previous)
            assertTrue(c.neckPitch.isFinite())
            assertFalse(c.driving)
            previous = t.bodyX
        }
        assertFalse(t.turning)
    }

    @Test
    fun `face reacquisition after expiry cannot reuse the previous turn direction`() {
        val t = KeepInFrame.Tracker()
        KeepInFrame.step(0.6f, 0f, 1f, 1_000, 1_000, 0f, t)
        assertStopped(KeepInFrame.step(null, null, null, 1_400, 1_000, 0.6f, t))
        assertTrue(KeepInFrame.step(-0.4f, 0f, 1f, 1_410, 1_410, 0f, t).turnSpeed < 0)
    }

    private fun assertStopped(command: KeepInFrame.Command) {
        assertFalse(command.turning)
        assertFalse(command.driving)
        assertFalse(command.searching)
        assertEquals(0, command.turnTicks)
        assertTrue(command.neckPitch.isFinite())
    }
}
