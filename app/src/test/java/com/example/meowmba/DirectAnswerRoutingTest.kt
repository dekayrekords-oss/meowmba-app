package com.example.meowmba

import com.example.meowmba.ui.companion.CompanionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Questions get answered directly; explicit searches open in the browser.
 */
class DirectAnswerRoutingTest {

    // ── extractSearchQuery ──────────────────────────────────────────────────

    @Test
    fun `explicit search phrasings extract the query`() {
        assertEquals("best sm58 price", CompanionViewModel.extractSearchQuery("search for best sm58 price"))
        assertEquals("how to install ollama", CompanionViewModel.extractSearchQuery("look up how to install ollama"))
        assertEquals("cat facts", CompanionViewModel.extractSearchQuery("google cat facts"))
        assertEquals("meow", CompanionViewModel.extractSearchQuery("search the web for meow"))
        assertEquals("meow", CompanionViewModel.extractSearchQuery("search meow"))
    }

    @Test
    fun `local searches stay with the agent`() {
        assertNull(CompanionViewModel.extractSearchQuery("search my memory for the mixer"))
        assertNull(CompanionViewModel.extractSearchQuery("search my notes for wifi password"))
        assertNull(CompanionViewModel.extractSearchQuery("search for my photos of the cat"))
    }

    @Test
    fun `non searches return null`() {
        assertNull(CompanionViewModel.extractSearchQuery("how do i install this"))
        assertNull(CompanionViewModel.extractSearchQuery("open the browser"))
        assertNull(CompanionViewModel.extractSearchQuery("search for"))
        assertNull(CompanionViewModel.extractSearchQuery("google"))
    }

    // ── looksLikeDirectQuestion ─────────────────────────────────────────────

    @Test
    fun `informational questions match`() {
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("how do i install ollama on linux"))
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("how to install ffmpeg"))
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("what is a capacitor"))
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("why is the sky blue"))
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("is it going to rain"))
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("tell me this: are you a cat?"))
    }

    @Test
    fun `addressed questions still match`() {
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("hey meow how do i install ollama"))
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("meow what is a capacitor"))
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("tell me how to install ffmpeg"))
        assertEquals("cat facts", CompanionViewModel.extractSearchQuery("hey meow search for cat facts"))
        assertTrue(CompanionViewModel.looksLikeShowInBrowser("hey meow show it in the browser"))
    }

    @Test
    fun `commands and requests do not match`() {
        assertFalse(CompanionViewModel.looksLikeDirectQuestion("open the browser"))
        assertFalse(CompanionViewModel.looksLikeDirectQuestion("make me a web page"))
        assertFalse(CompanionViewModel.looksLikeDirectQuestion("can you open spotify"))
        assertFalse(CompanionViewModel.looksLikeDirectQuestion("could you play some music"))
        assertFalse(CompanionViewModel.looksLikeDirectQuestion("please set a timer"))
        assertFalse(CompanionViewModel.looksLikeDirectQuestion("search for cat facts"))
        assertFalse(CompanionViewModel.looksLikeDirectQuestion("play something"))
    }
}
