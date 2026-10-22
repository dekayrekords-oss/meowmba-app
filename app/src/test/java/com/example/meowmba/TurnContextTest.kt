package com.example.meowmba

import com.example.meowmba.core.agent.runtime.TurnContext
import org.junit.Assert.*
import org.junit.Test

class TurnContextTest {
    @Test fun longerFollowUpsKeepHistoryBounded() {
        val context = TurnContext()
        context.scope(1)
        repeat(12) { context.add("User", "turn $it") }
        assertEquals(8, context.history()!!.lines().size)
        assertTrue(context.history()!!.contains("turn 11"))
        assertFalse(context.history()!!.contains("turn 0\n"))
        assertTrue(TurnContext.dependent("make that a little longer and use a friendly tone"))
    }
    @Test fun newConversationCannotInheritWeatherOrHistory() {
        val context = TurnContext { 100 }
        context.scope(1); context.add("User", "weather"); context.awaitWeatherPlace()
        context.scope(2)
        assertNull(context.history()); assertFalse(context.awaitingWeatherPlace())
    }
    @Test fun weatherSlotExpiresAndCancels() {
        var now = 100L
        val context = TurnContext { now }
        context.awaitWeatherPlace(); assertTrue(context.awaitingWeatherPlace())
        now += 180_001; assertFalse(context.awaitingWeatherPlace())
        context.awaitWeatherPlace(); context.clearWeather(); assertFalse(context.awaitingWeatherPlace())
    }
}
