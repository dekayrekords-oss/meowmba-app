package com.example.meowmba

import com.example.meowmba.core.personality.IdleChatter
import com.example.meowmba.core.personality.Mood
import com.example.meowmba.core.personality.MoodEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleChatterTest {

    private fun morning(): Long =
        java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.AUGUST, 12, 8, 15, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `recognises the usual ways people ask for a fact`() {
        listOf(
            "tell me a fact",
            "tell me a fun fact",
            "give me a random fact",
            "can you tell me a fact",
            "hey meow tell me a fact",
            "fun fact",
            "another fact",
            "got any facts",
            "say something interesting",
            "share a fun fact about cats",
        ).forEach {
            assertTrue("should route: $it", IdleChatter.isFactRequest(it))
        }
    }

    @Test
    fun `does not steal questions that only mention facts`() {
        listOf(
            "what are the facts of my case",
            "remember the fact that I hate cilantro",
            "how are you",
            "what time is it",
        ).forEach {
            assertFalse("should NOT route: $it", IdleChatter.isFactRequest(it))
        }
    }

    @Test
    fun `same seed in the same hour is stable`() {
        val t = morning()
        val a = IdleChatter.line(t, 42, Mood.CONTENT, preferFact = true)
        val b = IdleChatter.line(t + 60_000L, 42, Mood.CONTENT, preferFact = true)
        assertEquals(a, b)
    }

    @Test
    fun `different seeds remix instead of looping four lines`() {
        val t = morning()
        val first = IdleChatter.line(t, 0, Mood.CONTENT, preferFact = true)
        assertTrue(first.isNotBlank())
        assertNotEquals(first, IdleChatter.line(t, 1, Mood.CONTENT, preferFact = true))
        val unique = (0 until 80).map {
            IdleChatter.line(t, it * 31, Mood.CONTENT, preferFact = true)
        }.toSet()
        assertTrue("expected lots of unique facts, got ${unique.size}", unique.size >= 50)
    }

    @Test
    fun `mood engine de-dupes recent facts`() {
        val e = MoodEngine()
        val t = morning()
        val seen = (0 until 20).map { e.pickFact(t, it) }.toSet()
        assertTrue("de-dupe should still leave variety, got ${seen.size}", seen.size >= 16)
    }

    @Test
    fun `moon name is a readable phase`() {
        val name = IdleChatter.moonName(morning())
        assertTrue(name.contains("moon") || name.contains("quarter"))
    }
}
