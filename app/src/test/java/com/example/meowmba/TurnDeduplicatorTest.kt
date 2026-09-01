package com.example.meowmba

import com.example.meowmba.core.audio.TurnDeduplicator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnDeduplicatorTest {
    @Test
    fun `same normalized callback is accepted only once inside transport window`() {
        var now = 1_000L
        val gate = TurnDeduplicator(now = { now })

        assertTrue(gate.accept("What's the weather?"))
        now += 40
        assertFalse(gate.accept("whats the weather"))
    }

    @Test
    fun `same words later are a legitimate new turn`() {
        var now = 1_000L
        val gate = TurnDeduplicator(now = { now })

        assertTrue(gate.accept("repeat that"))
        now += TurnDeduplicator.DUPLICATE_WINDOW_MS + 1
        assertTrue(gate.accept("repeat that"))
    }

    @Test
    fun `different follow up is never suppressed`() {
        var now = 1_000L
        val gate = TurnDeduplicator(now = { now })

        assertTrue(gate.accept("turn on the lights"))
        now += 20
        assertTrue(gate.accept("and make them blue"))
    }

    @Test
    fun `near duplicate reworded turn is rejected inside window`() {
        var now = 1_000L
        val gate = TurnDeduplicator(now = { now })

        assertTrue(gate.accept("what's the weather"))
        now += 4_000
        // Same question, ASR re-worded — the double-delivery double-answer path.
        assertFalse(gate.accept("what is the weather"))
    }

    @Test
    fun `same fragment of the last turn is rejected inside window`() {
        var now = 1_000L
        val gate = TurnDeduplicator(now = { now })

        assertTrue(gate.accept("one of the fifteen I was making"))
        now += 3_000
        // A cut-off stutter fragment of the same turn.
        assertFalse(gate.accept("one of the fifteen"))
    }

    @Test
    fun `genuinely different short follow up inside window still passes`() {
        var now = 1_000L
        val gate = TurnDeduplicator(now = { now })

        assertTrue(gate.accept("set a timer"))
        now += 1_000
        assertTrue(gate.accept("make it five minutes"))
    }
}
