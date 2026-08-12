package com.example.meowmba

import com.example.meowmba.core.agent.runtime.ClockAnswers
import com.example.meowmba.core.audio.WakePhraseMatcher
import com.example.meowmba.core.memory.PromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * The clock answer must come from the device, not the model. Pinning the phrase
 * recognition and the formatting keeps the chat brain out of the path that
 * produced "confidently wrong" times on device.
 */
class ClockAnswersTest {

    private fun asks(q: String) = ClockAnswers.isClockQuestion(WakePhraseMatcher.normalize(q))

    @Test
    fun `recognises the ways people ask the time`() {
        listOf(
            "what time is it", "what's the time", "whats the time",
            "what is the time", "what time", "tell me the time",
            "current time", "what time is it right now",
        ).forEach { assertTrue("should route: $it", asks(it)) }
    }

    @Test
    fun `recognises the ways people ask the day or date`() {
        listOf(
            "what day is it", "what day is today", "what is today",
            "what's today", "whats today", "what date is it",
            "what is the date", "what's the date", "what is today's date",
            "today's date", "what day is this",
        ).forEach { assertTrue("should route: $it", asks(it)) }
    }

    @Test
    fun `does not hijack longer questions that merely mention time`() {
        listOf(
            "what time is the movie", "what time do you wake up",
            "what time is it in new york", "what date is my dentist appointment",
            "how are you", "set a timer for ten minutes",
        ).forEach { assertFalse("should NOT route: $it", asks(it)) }
    }

    @Test
    fun `answers time from the given instant in the device timezone`() {
        val zone = TimeZone.getTimeZone("America/Los_Angeles")
        val now = java.util.Calendar.getInstance(zone).apply {
            set(2026, java.util.Calendar.AUGUST, 12, 15, 42, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("It's 3:42 PM.", ClockAnswers.answer("what time is it", now, zone))
    }

    @Test
    fun `answers the date from the given instant in the device timezone`() {
        val zone = TimeZone.getTimeZone("America/Los_Angeles")
        val now = java.util.Calendar.getInstance(zone).apply {
            set(2026, java.util.Calendar.AUGUST, 12, 15, 42, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals("Today is Wednesday, August 12.", ClockAnswers.answer("what day is it", now, zone))
    }

    @Test
    fun `the prompt carries the current time so the model never guesses`() {
        val bare = PromptBuilder.build("Meow", "hi", emptyList(), emptyList())
        assertTrue(bare.contains("The current time and date right now"))
        assertTrue(bare.contains("never guess"))
    }

    @Test
    fun `explicit remember is not a clock question`() {
        assertFalse(asks("remember the time I went to the store"))
    }
}
