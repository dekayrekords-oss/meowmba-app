package com.example.meowmba

import com.example.meowmba.ui.companion.AvatarFrameClock
import org.junit.Assert.*
import org.junit.Test

class AvatarFrameClockTest {
    @Test fun everyVsyncIsRepresentedAt60And120Hz() {
        for (hz in listOf(60,120)) {
            val clock = AvatarFrameClock()
            var previous = clock.step(1_000_000_000, null)
            repeat(hz) { i ->
                val next = clock.step(1_000_000_000 + (i+1)*1_000_000_000L/hz, null)
                assertTrue(next.seconds > previous.seconds)
                previous = next
            }
            assertEquals(1f, previous.seconds, .00001f)
        }
    }
    @Test fun replayUsesSequenceIdentityAndHitchesDoNotRestartTheEntrance() {
        val clock = AvatarFrameClock()
        clock.step(1_000_000_000, 1)
        assertEquals(1200L, clock.step(2_200_000_000, 1).performanceMillis)
        assertEquals(0L, clock.step(2_210_000_000, 2).performanceMillis)
        assertEquals(10L, clock.step(2_220_000_000, 2).performanceMillis)
    }
    @Test fun pauseFreezesPhysiologyAndNewPerformanceHasAFreshPhase() {
        val clock = AvatarFrameClock()
        clock.step(1_000_000_000, 1)
        val before = clock.step(1_020_000_000, 1)
        clock.pause()
        val after = clock.step(99_000_000_000, null)
        assertEquals(before.seconds, after.seconds, 0f)
        assertEquals(0f, after.dt, 0f)
        assertEquals(0L, clock.step(99_020_000_000, 2).performanceMillis)
    }
}
