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
    fun `face on the right turns right, face on the left turns left`() {
        val right = KeepInFrame.step(0.6f, 0f, 1f, 1_000, 1_000, 0f)
        val left = KeepInFrame.step(-0.6f, 0f, 1f, 1_000, 1_000, 0f)
        assertTrue(right.turnSpeed > 0)
        assertTrue(left.turnSpeed < 0)
        assertTrue(right.turnTicks >= 7)
    }

    @Test
    fun `face low nods down, face high looks up`() {
        val down = KeepInFrame.step(0f, 0.7f, 1f, 1_000, 1_000, 0f)
        val up = KeepInFrame.step(0f, -0.7f, 1f, 1_000, 1_000, 0f)
        assertTrue(down.neckPitch > 0f)
        assertTrue(up.neckPitch < 0f)
    }

    @Test
    fun `far face is approached only when aimed`() {
        val aimed = KeepInFrame.step(0.1f, 0f, 0.55f, 1_000, 1_000, 0f)
        val off = KeepInFrame.step(0.7f, 0f, 0.55f, 1_000, 1_000, 0f)
        assertTrue(aimed.driving)
        assertFalse(off.driving)
    }

    @Test
    fun `lost face searches toward the last side then gives up`() {
        val hold = KeepInFrame.step(null, null, null, nowMs = 1_200, lastSeenMs = 1_000, lastX = 0.4f)
        assertFalse(hold.searching)
        val search = KeepInFrame.step(null, null, null, nowMs = 1_700, lastSeenMs = 1_000, lastX = 0.4f)
        assertTrue(search.searching)
        assertTrue(search.turnSpeed > 0)
        val giveUp = KeepInFrame.step(null, null, null, nowMs = 5_000, lastSeenMs = 1_000, lastX = 0.4f)
        assertFalse(giveUp.searching)
        assertFalse(giveUp.turning)
    }
}
