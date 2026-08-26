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
}
