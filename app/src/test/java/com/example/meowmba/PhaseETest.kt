package com.example.meowmba

import com.example.meowmba.core.database.RoutineEntity
import com.example.meowmba.core.routines.RoutineScheduler
import com.example.meowmba.ui.companion.CompanionState
import com.example.meowmba.ui.companion.CompanionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Phase E: routine scheduling math + barge-in state machine. */
class PhaseETest {

    private fun at(hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 29, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ── E1: nextTrigger ────────────────────────────────────────────────────

    @Test
    fun `time-of-day routine fires later today when the time has not passed`() {
        val routine = RoutineEntity(name = "brief", timeOfDay = "08:00", action = "morning_brief")
        val now = at(7, 30)
        assertEquals(at(8, 0), RoutineScheduler.nextTrigger(routine, now))
    }

    @Test
    fun `time-of-day routine rolls to tomorrow after its time passed`() {
        val routine = RoutineEntity(name = "brief", timeOfDay = "08:00", action = "morning_brief")
        val now = at(9, 15)
        val expected = Calendar.getInstance().apply {
            timeInMillis = at(8, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        assertEquals(expected, RoutineScheduler.nextTrigger(routine, now))
    }

    @Test
    fun `interval routine lands strictly in the future even when lastRun is old`() {
        val routine = RoutineEntity(name = "ping", intervalMinutes = 30, lastRunAt = at(10, 0))
        val now = at(11, 45) // 105 min later → 30/60/90 all past, next is 120
        assertEquals(at(12, 0), RoutineScheduler.nextTrigger(routine, now))
    }

    @Test
    fun `never-run interval routine starts from now`() {
        val routine = RoutineEntity(name = "ping", intervalMinutes = 10)
        val now = at(12, 0)
        assertEquals(at(12, 10), RoutineScheduler.nextTrigger(routine, now))
    }

    // ── E3: barge-in state machine ────────────────────────────────────────

    private fun state(
        mic: Boolean = true, muted: Boolean = false, wake: Boolean = true,
        barge: Boolean = true, convo: Boolean = false
    ) = CompanionState(
        isMicEnabled = mic, isMuted = muted, wakeWordEnabled = wake,
        bargeInEnabled = barge, conversationMode = convo
    )

    @Test
    fun `barge-in keeps listening when wake word is armed`() {
        assertTrue(CompanionViewModel.shouldEnterFollowUp(state()))
    }

    @Test
    fun `barge-in off and no conversation mode returns to standby`() {
        assertFalse(CompanionViewModel.shouldEnterFollowUp(state(barge = false)))
    }

    @Test
    fun `conversation mode keeps listening even without barge-in`() {
        assertTrue(CompanionViewModel.shouldEnterFollowUp(state(barge = false, convo = true)))
    }

    @Test
    fun `muted or mic-off never enters follow-up`() {
        assertFalse(CompanionViewModel.shouldEnterFollowUp(state(muted = true)))
        assertFalse(CompanionViewModel.shouldEnterFollowUp(state(mic = false)))
    }

    @Test
    fun `wake word disarmed means barge-in alone does not keep listening`() {
        assertFalse(CompanionViewModel.shouldEnterFollowUp(state(wake = false)))
    }
}
