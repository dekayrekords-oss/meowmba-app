package com.example.meowmba.core.ring

import org.junit.Assert.*
import org.junit.Test

class RingIdleSleepTest {
    private class Harness {
        data class Timer(val due: Long, val action: () -> Unit, var cancelled: Boolean = false)
        var time = 0L
        val timers = mutableListOf<Timer>()
        val states = mutableListOf<Boolean>()
        val sleep = RingIdleSleep(now = { time }, schedule = { delay, action ->
            val timer = Timer(time + delay, action); timers.add(timer)
            val cancel: () -> Unit = { timer.cancelled = true }; cancel
        }, publish = states::add)
        fun advance(delta: Long) {
            time += delta
            while (true) {
                val due = timers.firstOrNull { !it.cancelled && it.due <= time } ?: break
                due.cancelled = true; due.action()
            }
        }
    }
    @Test fun defaultsToThirtySecondsAndBlocksActionsWhileAsleep() {
        val state = RingState()
        assertTrue(state.sleepEnabled); assertEquals(30, state.sleepAfterSeconds)
        val h = Harness(); h.sleep.configure(true, state.sleepAfterSeconds)
        h.advance(29_999); assertFalse(h.sleep.sleeping)
        h.advance(1); assertTrue(h.sleep.sleeping); assertEquals(listOf(true), h.states)
        val ready = state.copy(connected = true, paused = false, sleeping = true,
            verifiedGestures = setOf("Next", "Single tap", "Double tap"), mappings = ringScrollClickMappings())
        assertEquals(RingAction.NONE, ringActionFor(ready, "Next", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready, "Single tap", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready, "Double tap", "com.example.meowmba"))
    }
    @Test fun activityResetsIdleAndStaleTimersCannotPutControlsToSleep() {
        val h = Harness(); h.sleep.configure(true, 30)
        val stale = h.timers.last().action
        h.advance(29_000); h.sleep.activity(); h.advance(1_000)
        stale(); assertFalse(h.sleep.sleeping)
        h.advance(28_999); assertFalse(h.sleep.sleeping)
        h.advance(1); assertTrue(h.sleep.sleeping)
    }
    @Test fun onlyDoubleTapWakesAndWakeGestureIsConsumed() {
        val h = Harness(); h.sleep.configure(true, 30); h.sleep.sleepNow()
        assertTrue(h.sleep.consumeSleepingGesture("Single tap")); assertTrue(h.sleep.sleeping)
        assertTrue(h.sleep.consumeSleepingGesture("Next")); assertTrue(h.sleep.sleeping)
        h.sleep.activity(); assertTrue(h.sleep.sleeping)
        assertTrue(h.sleep.consumeSleepingGesture("Double tap")); assertFalse(h.sleep.sleeping)
        assertFalse(h.sleep.consumeSleepingGesture("Double tap")) // next gesture may use normal mapping
        assertEquals(listOf(true, false), h.states)
        h.advance(29_999); assertFalse(h.sleep.sleeping)
        h.advance(1); assertTrue(h.sleep.sleeping)
    }
    @Test fun timeoutChangesAndDisablingCancelStandbyAndPendingCallbacks() {
        val h = Harness(); h.sleep.configure(true, 30); h.advance(10_000)
        h.sleep.configure(true, 60); h.advance(59_999); assertFalse(h.sleep.sleeping)
        h.advance(1); assertTrue(h.sleep.sleeping)
        h.sleep.configure(false, 60); assertFalse(h.sleep.sleeping)
        h.advance(90_000); assertFalse(h.sleep.sleeping)
        h.sleep.configure(true, 5); val stale = h.timers.last().action
        h.sleep.configure(false, 5); h.advance(5_000); stale(); assertFalse(h.sleep.sleeping)
        val ready = RingState(connected = true, accessibility = true, paused = false)
        assertTrue(ringSleepEligible(ready)); assertTrue(ringSleepEligible(ready.copy(sleeping = true)))
        assertFalse(ringSleepEligible(ready.copy(testing = true)))
        assertFalse(ringSleepEligible(ready.copy(paused = true)))
        assertFalse(ringSleepEligible(ready.copy(connected = false)))
        assertFalse(ringSleepEligible(ready.copy(sleepEnabled = false)))
    }
    @Test fun elapsedDeadlineSurvivesDelayedHandlerAndWakeStartsFreshTimeout() {
        val h = Harness(); h.time = 100_000; h.sleep.configure(true, 30)
        assertFalse(h.sleep.sleeping)
        h.time += 31_000 // elapsed time advanced without running the Handler callback
        h.sleep.activity(); assertTrue(h.sleep.sleeping)
        assertTrue(h.sleep.consumeSleepingGesture("Double tap")); assertFalse(h.sleep.sleeping)
        h.advance(29_999); assertFalse(h.sleep.sleeping)
        h.advance(1); assertTrue(h.sleep.sleeping)
    }

}
