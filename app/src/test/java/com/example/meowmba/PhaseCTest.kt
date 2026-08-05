package com.example.meowmba

import com.example.meowmba.core.context.ContextDirector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase C: context classification for screen awareness. */
class PhaseCTest {

    @Test
    fun `classifies gameplay screens`() {
        assertEquals("game", ContextDirector.classify("A video game with a health bar and score"))
        assertEquals("game", ContextDirector.classify("gameplay view of level 3"))
    }

    @Test
    fun `classifies errors documents browsers media`() {
        assertEquals("error", ContextDirector.classify("An error dialog: app not responding"))
        assertEquals("document", ContextDirector.classify("A Gmail email composition window"))
        assertEquals("browser", ContextDirector.classify("A browser showing search results"))
        assertEquals("media", ContextDirector.classify("A YouTube video playing"))
        assertEquals("general", ContextDirector.classify("The home screen with app icons"))
    }

    @Test
    fun `comments are short and kind-aware`() {
        val c = ContextDirector.shortComment("error", "crash dialog")
        assertTrue(c.length < 160)
        assertTrue(c.contains("error"))
    }
}
