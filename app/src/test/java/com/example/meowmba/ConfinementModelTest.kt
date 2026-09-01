package com.example.meowmba

import com.example.meowmba.core.robot.BodyProfile
import com.example.meowmba.core.robot.ConfinementModel
import com.example.meowmba.core.robot.SensorEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The envelope widens only on evidence the robot gathered from motion it was
 * going to make anyway, and collapses on anything surprising. Every rule here
 * is a safety rule, so they are all pinned.
 */
class ConfinementModelTest {

    /**
     * Runs [count] clean windows of [durationMs] each, starting at [from].
     * Named `runCleanWindows` rather than `cleanWindows` so it cannot be
     * confused with the model's property of that name.
     */
    private fun ConfinementModel.runCleanWindows(
        count: Int,
        durationMs: Long = 2_000L,
        from: Long = 0L,
        gapMs: Long = 1_000L,
    ): BodyProfile {
        var t = from
        var result = profile
        repeat(count) {
            onMotionWindowStart(t)
            t += durationMs
            result = onMotionWindowEnd(t)
            t += gapMs
        }
        return result
    }

    @Test
    fun `starts on the desk`() {
        assertEquals(BodyProfile.DESK, ConfinementModel().profile)
    }

    @Test
    fun `four clean windows unlock the floor`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        assertEquals(BodyProfile.FLOOR, m.runCleanWindows(4))
    }

    @Test
    fun `three clean windows are not enough`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        assertEquals(BodyProfile.DESK, m.runCleanWindows(3))
    }

    @Test
    fun `a cliff collapses to desk immediately and zeroes evidence`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        m.runCleanWindows(4)
        assertEquals(BodyProfile.FLOOR, m.profile)

        assertEquals(BodyProfile.DESK, m.onSensor(SensorEvent.Cliff(byteArrayOf(1)), 20_000L))
        assertEquals(0, m.cleanWindows)
    }

    @Test
    fun `an unknown frame collapses to desk, because undecoded means dangerous`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        m.runCleanWindows(4)
        assertEquals(BodyProfile.DESK, m.onSensor(SensorEvent.Unknown(byteArrayOf(9)), 20_000L))
        assertEquals(0, m.cleanWindows)
    }

    @Test
    fun `touch is harmless and changes nothing`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        m.runCleanWindows(4)
        assertEquals(BodyProfile.FLOOR, m.onSensor(SensorEvent.Touch, 20_000L))
        assertEquals(4, m.cleanWindows)
    }

    @Test
    fun `a cliff during a window poisons that window`() {
        val m = ConfinementModel(cleanWindowsForFloor = 2)
        m.onMotionWindowStart(0L)
        m.onSensor(SensorEvent.Cliff(byteArrayOf(1)), 500L)
        m.onMotionWindowEnd(2_000L)
        assertEquals(0, m.cleanWindows)
        assertEquals(BodyProfile.DESK, m.profile)
    }

    @Test
    fun `a window too short to be evidence does not count`() {
        val m = ConfinementModel(cleanWindowsForFloor = 1, minWindowMs = 1_500L)
        m.onMotionWindowStart(0L)
        assertEquals(BodyProfile.DESK, m.onMotionWindowEnd(400L))
        assertEquals(0, m.cleanWindows)
    }

    @Test
    fun `being picked up resets everything`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        m.runCleanWindows(4)
        m.onPickedUp(30_000L)
        assertEquals(BodyProfile.DESK, m.profile)
        assertEquals(0, m.cleanWindows)
    }

    @Test
    fun `losing the link resets everything`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        m.runCleanWindows(4)
        m.onLinkLost(30_000L)
        assertEquals(BodyProfile.DESK, m.profile)
        assertEquals(0, m.cleanWindows)
    }

    @Test
    fun `stale evidence decays and the floor re-locks`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4, evidenceDecayMs = 60_000L)
        m.runCleanWindows(4)
        assertEquals(BodyProfile.FLOOR, m.profile)

        // A window starting long after the last evidence finds none of it left.
        m.onMotionWindowStart(10 * 60_000L)
        assertEquals(BodyProfile.DESK, m.onMotionWindowEnd(10 * 60_000L + 2_000L))
        assertEquals(1, m.cleanWindows)
    }

    @Test
    fun `evidence does not double count an unclosed window`() {
        val m = ConfinementModel(cleanWindowsForFloor = 4)
        m.onMotionWindowStart(0L)
        m.onMotionWindowStart(100L) // spurious restart
        assertEquals(BodyProfile.DESK, m.onMotionWindowEnd(3_000L))
        assertEquals(1, m.cleanWindows)
    }

    @Test
    fun `ending a window that never started is ignored`() {
        val m = ConfinementModel(cleanWindowsForFloor = 1)
        assertEquals(BodyProfile.DESK, m.onMotionWindowEnd(5_000L))
        assertEquals(0, m.cleanWindows)
    }

    @Test
    fun `a cliff outside any window still collapses the profile`() {
        val m = ConfinementModel(cleanWindowsForFloor = 2)
        m.runCleanWindows(2)
        assertEquals(BodyProfile.FLOOR, m.profile)
        assertTrue(m.onSensor(SensorEvent.Cliff(byteArrayOf(0)), 99_000L) == BodyProfile.DESK)
    }
}