package com.example.meowmba

import com.example.meowmba.core.robot.SimulatedMotorController
import com.example.meowmba.ui.companion.CompanionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The microphone must always be able to come back.
 *
 * Measured on device: the always-on mic sat in an endless
 * `code=11` / `code=8` loop against Google's AiAi recognizer, retrying on a
 * fixed 2.5s gap. Each retry re-wedged the service it was waiting for, so the
 * mic, the mic button and the wake word were all dead until an app restart.
 */
class MicRecoveryTest {

    // ── recognizer backoff ──────────────────────────────────────────────────

    @Test
    fun `backoff doubles with each consecutive refusal`() {
        assertEquals(2_500L, CompanionViewModel.contentionBackoffMs(1))
        assertEquals(5_000L, CompanionViewModel.contentionBackoffMs(2))
        assertEquals(10_000L, CompanionViewModel.contentionBackoffMs(3))
        assertEquals(20_000L, CompanionViewModel.contentionBackoffMs(4))
        assertEquals(40_000L, CompanionViewModel.contentionBackoffMs(5))
    }

    @Test
    fun `backoff is capped so the mic always retries eventually`() {
        // Never give up entirely: a permanent stop would need an app restart.
        for (streak in 6..500) {
            val wait = CompanionViewModel.contentionBackoffMs(streak)
            assertTrue("streak $streak waited $wait", wait <= 60_000L)
            assertTrue("streak $streak must still retry", wait > 0L)
        }
    }

    @Test
    fun `the first refusal still retries quickly`() {
        // A one-off hiccup must not cost the user 40 seconds of deaf mic.
        assertTrue(CompanionViewModel.contentionBackoffMs(1) <= 2_500L)
        // Defensive: a zero or negative streak is treated as the first try.
        assertEquals(2_500L, CompanionViewModel.contentionBackoffMs(0))
        assertEquals(2_500L, CompanionViewModel.contentionBackoffMs(-3))
    }

    @Test
    fun `backoff never regresses as the streak grows`() {
        var previous = 0L
        for (streak in 1..50) {
            val wait = CompanionViewModel.contentionBackoffMs(streak)
            assertTrue("wait must not shrink at streak $streak", wait >= previous)
            previous = wait
        }
    }

    // ── motor spam ──────────────────────────────────────────────────────────

    @Test
    fun `repeated identical servo commands are dropped`() {
        // The IMU drives this at ~50Hz from the main thread; on device it was
        // emitting 55 identical lines a second.
        val logged = mutableListOf<String>()
        val motor = SimulatedMotorController(log = { logged.add(it) })
        repeat(100) { motor.setServo(0, 89.2f) }
        assertEquals("only the first command is a motion", 1, logged.size)
        assertEquals(1, motor.history.size)
    }

    @Test
    fun `a real change still gets through`() {
        val logged = mutableListOf<String>()
        val motor = SimulatedMotorController(log = { logged.add(it) })
        motor.setServo(0, 90f)
        motor.setServo(0, 120f)
        motor.setServo(0, 120f)
        motor.setServo(1, 120f)
        assertEquals(3, logged.size)
    }

    @Test
    fun `the simulated motor is silent unless a collector is supplied`() {
        // The default used to be println — blocking I/O on the UI thread at
        // 50Hz, and it buried every other line in logcat.
        val motor = SimulatedMotorController()
        repeat(10) { motor.setServo(0, it.toFloat() * 10f) }
        assertEquals("history is still recorded", 10, motor.history.size)
    }

    @Test
    fun `servo angles stay clamped to the physical range`() {
        val motor = SimulatedMotorController()
        motor.setServo(0, -50f)
        motor.setServo(0, 400f)
        assertTrue(motor.history.all { entry ->
            val angle = entry.substringAfter("-> ").toFloat()
            angle in 0f..180f
        })
    }
}
