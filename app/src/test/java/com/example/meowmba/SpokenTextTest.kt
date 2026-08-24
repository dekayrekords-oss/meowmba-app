package com.example.meowmba

import com.example.meowmba.core.audio.SpokenText
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTextTest {

    @Test
    fun `all-caps zipformer output becomes a sentence`() {
        assertEquals("What time is it", SpokenText.normalize("WHAT TIME IS IT"))
        assertEquals("It", SpokenText.normalize("IT"))
    }

    @Test
    fun `mixed case is left alone`() {
        assertEquals("Hey Meow, play jazz", SpokenText.normalize("Hey Meow, play jazz"))
    }

    @Test
    fun `blank stays blank`() {
        assertEquals("", SpokenText.normalize("   "))
    }

    @Test
    fun `stray letters and fillers are not usable turns`() {
        org.junit.Assert.assertFalse(SpokenText.isUsable("e"))
        org.junit.Assert.assertFalse(SpokenText.isUsable("a b"))
        org.junit.Assert.assertFalse(SpokenText.isUsable("uh"))
        org.junit.Assert.assertFalse(SpokenText.isUsable("the"))
        org.junit.Assert.assertFalse(SpokenText.isUsable("IT"))
        org.junit.Assert.assertFalse(SpokenText.isUsable("ular"))
        org.junit.Assert.assertFalse(SpokenText.isUsable("ulas"))
        org.junit.Assert.assertFalse(SpokenText.isUsable("Ular"))
        org.junit.Assert.assertFalse(SpokenText.isUsable("tion"))
        org.junit.Assert.assertTrue(SpokenText.isUsable("ok"))
        org.junit.Assert.assertTrue(SpokenText.isUsable("set a timer"))
    }

    @Test
    fun `unfinished tails are not treated as a finished turn`() {
        org.junit.Assert.assertFalse(SpokenText.looksFinished("set a"))
        org.junit.Assert.assertFalse(SpokenText.looksFinished("turn on the"))
        org.junit.Assert.assertTrue(SpokenText.looksFinished("set a timer"))
        org.junit.Assert.assertTrue(SpokenText.looksFinished("what time is it"))
        org.junit.Assert.assertTrue(SpokenText.looksFinished("ok"))
    }

    @Test
    fun `best transcript prefers higher confidence and longer usable text`() {
        val picked = SpokenText.bestTranscript(
            listOf("uh", "set a timer for five", "set a timer"),
            floatArrayOf(0.9f, 0.4f, 0.8f),
        )
        org.junit.Assert.assertEquals("set a timer", picked)
        val longest = SpokenText.bestTranscript(
            listOf("set a timer", "set a timer for five minutes"),
        )
        org.junit.Assert.assertEquals("set a timer for five minutes", longest)
    }

    @Test
    fun `zipformer greeting mangling becomes what's up buddy`() {
        assertEquals("What's up buddy", SpokenText.polish("Ovites up body"))
        assertEquals("What's up buddy", SpokenText.polish("Ulat's up buddy"))
        assertEquals("What's up buddy", SpokenText.polish("Ulat's up budd"))
        assertEquals("Yo what's up buddy", SpokenText.polish("yo oot's up buddy"))
        assertEquals("Set a timer", SpokenText.polish("Set a timer"))
    }

    @Test
    fun `empty official result falls back to a complete partial`() {
        org.junit.Assert.assertEquals(
            "turn on the lights",
            SpokenText.preferFinal("", "turn on the lights"),
        )
        org.junit.Assert.assertEquals(
            "turn on the lights",
            SpokenText.preferFinal("turn on the lights", "turn on"),
        )
    }
}
