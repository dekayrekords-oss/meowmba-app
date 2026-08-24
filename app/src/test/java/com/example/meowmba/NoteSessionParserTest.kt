package com.example.meowmba

import com.example.meowmba.core.audio.WakePhraseMatcher
import com.example.meowmba.core.vault.NoteSessionIntent
import com.example.meowmba.core.vault.NoteSessionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSessionParserTest {

    private fun p(s: String) = NoteSessionParser.parse(WakePhraseMatcher.normalize(s))

    @Test
    fun `start phrases with no topic`() {
        listOf(
            "start noting",
            "start transcribing",
            "start taking notes",
            "jot this down",
            "take note",
            "take notes",
            "begin transcribing",
        ).forEach { phrase ->
            val intent = p(phrase)
            assertTrue("$phrase should start", intent is NoteSessionIntent.Start)
            assertEquals(null, (intent as NoteSessionIntent.Start).topic)
        }
    }

    @Test
    fun `start with a topic`() {
        val a = p("start taking notes on the meeting") as NoteSessionIntent.Start
        assertEquals("the meeting", a.topic)
        val b = p("jot this down about grocery list") as NoteSessionIntent.Start
        assertEquals("grocery list", b.topic)
        val c = p("start noting about homework") as NoteSessionIntent.Start
        assertEquals("homework", c.topic)
    }

    @Test
    fun `continue on a topic`() {
        val a = p("continue taking notes on the meeting") as NoteSessionIntent.Continue
        assertEquals("the meeting", a.topic)
        val b = p("continue notes") as NoteSessionIntent.Continue
        assertEquals(null, b.topic)
        val c = p("keep taking notes") as NoteSessionIntent.Continue
        assertEquals(null, c.topic)
    }

    @Test
    fun `stop phrases`() {
        listOf(
            "stop notes",
            "stop taking notes",
            "stop transcribing",
            "stop noting",
            "that's all for notes",
            "done taking notes",
            "end notes",
        ).forEach { phrase ->
            assertEquals("$phrase", NoteSessionIntent.Stop, p(phrase))
        }
    }

    @Test
    fun `remember this stays memory not a note session`() {
        assertNull(p("remember this"))
        assertNull(p("remember that I like tea"))
        assertNull(p("remember this as home"))
        assertNull(p("start a project"))
        assertNull(p("how are you"))
        assertNull(p("take a photo"))
    }

    @Test
    fun `vault title is under Notes`() {
        assertEquals("Notes/2026-08-19", NoteSessionParser.vaultTitle("2026-08-19"))
        assertEquals("Notes/the meeting", NoteSessionParser.vaultTitle("the meeting"))
    }
}
