package com.example.meowmba

import com.example.meowmba.core.agent.runtime.GreetingAnswers
import com.example.meowmba.core.audio.WakePhraseMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingAnswersTest {

    private fun n(raw: String) = WakePhraseMatcher.normalize(raw)

    @Test
    fun `yo whats up buddy is a greeting even when zipformer mangles it`() {
        assertTrue(GreetingAnswers.isGreeting(n("yo what's up buddy")))
        assertTrue(GreetingAnswers.isGreeting(n("Ulat's up buddy")))
        assertTrue(GreetingAnswers.isGreeting(n("Ovites up body")))
        assertTrue(GreetingAnswers.isGreeting(n("what's up")))
        assertTrue(GreetingAnswers.isGreeting(n("hey buddy what's up")))
        assertTrue(GreetingAnswers.isGreeting(n("how are you")))
    }

    @Test
    fun `commands are not greetings`() {
        assertFalse(GreetingAnswers.isGreeting(n("open spotify")))
        assertFalse(GreetingAnswers.isGreeting(n("set a timer for five minutes")))
        assertFalse(GreetingAnswers.isGreeting(n("what time is it")))
        assertFalse(GreetingAnswers.isGreeting(n("what's the weather")))
    }

    @Test
    fun `reply uses the enrolled name`() {
        GreetingAnswers.resetVariantCounter(0)
        assertEquals("Hey Leigh. What's up?", GreetingAnswers.reply("leigh"))
        GreetingAnswers.resetVariantCounter(0)
        assertEquals("Hey. What's up?", GreetingAnswers.reply(null))
        GreetingAnswers.resetVariantCounter(0)
        assertEquals("Hey. What's up?", GreetingAnswers.reply("  "))
    }

    @Test
    fun `reply rotates instead of repeating one line`() {
        val lines = (0 until 4).map { idx ->
            GreetingAnswers.resetVariantCounter(idx)
            GreetingAnswers.reply(null)
        }
        assertEquals(lines.toSet().size, 4)
    }

    @Test
    fun `short garbled ups are greetings but long ones are not`() {
        // Measured ASR outputs for "yo, what's up buddy" — the model mangles
        // the opening differently every session.
        assertTrue(GreetingAnswers.isGreeting(n("no it's up buddy")))
        assertTrue(GreetingAnswers.isGreeting(n("oots up")))
        assertTrue(GreetingAnswers.isGreeting(n("ulat's up buddy")))
        assertTrue(GreetingAnswers.isGreeting(n("yo what's up")))
        assertFalse(GreetingAnswers.isGreeting(n("what's up with my calendar today")))
    }
}
