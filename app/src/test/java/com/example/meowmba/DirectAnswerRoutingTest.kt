package com.example.meowmba

import com.example.meowmba.ui.companion.CompanionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun `what do you see is vision not a chat guess`() {
        assertFalse(CompanionViewModel.looksLikeDirectQuestion("what do you see"))
        assertFalse(CompanionViewModel.looksLikeDirectQuestion("hey meow what do you see"))
        assertNotNull(com.example.meowmba.core.vision.VisionRouter.parse("what do you see"))
    }

    @Test
    fun `how fast is a speed question not a chat guess`() {
        assertTrue(com.example.meowmba.ui.companion.SpeechIntents.isSpeedQuestion("how fast am i going"))
        assertTrue(com.example.meowmba.ui.companion.SpeechIntents.isSpeedQuestion("how fast are we going"))
        assertTrue(com.example.meowmba.ui.companion.SpeechIntents.isSpeedQuestion("what speed am i doing"))
        assertFalse(com.example.meowmba.ui.companion.SpeechIntents.isSpeedQuestion("how do i install ollama"))
        assertFalse(CompanionViewModel.looksLikeDirectQuestion("how fast am i going"))
    }

    @Test
    fun `casual check-ins are answered not parked on the yellow mic`() {
        // Measured: "Hey buddy what's up" stayed in THINKING 45s because
        // stripAddress left "buddy what's up" and "what's" was not a starter.
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("hey buddy what's up"))
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("what's up"))
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("whats up"))
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("how are you"))
        assertTrue(CompanionViewModel.looksLikeDirectQuestion("how's it going"))
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

    // ── transient speech errors ────────────────────────────────────────────

    @Test
    fun `error 11 and network codes are transient`() {
        // ERROR_SERVER_DISCONNECTED is the infamous "error 11" the user sees
        // when the recognition service drops; it must be retried, not shown.
        assertTrue(CompanionViewModel.isTransientSpeechError(
            android.speech.SpeechRecognizer.ERROR_SERVER_DISCONNECTED))
        assertTrue(CompanionViewModel.isTransientSpeechError(
            android.speech.SpeechRecognizer.ERROR_NETWORK))
        assertTrue(CompanionViewModel.isTransientSpeechError(
            android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertTrue(CompanionViewModel.isTransientSpeechError(
            android.speech.SpeechRecognizer.ERROR_SERVER))
        assertTrue(CompanionViewModel.isTransientSpeechError(
            android.speech.SpeechRecognizer.ERROR_TOO_MANY_REQUESTS))
    }

    @Test
    fun `setup and silence errors are not transient`() {
        assertFalse(CompanionViewModel.isTransientSpeechError(
            android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertFalse(CompanionViewModel.isTransientSpeechError(
            android.speech.SpeechRecognizer.ERROR_NO_MATCH))
        assertFalse(CompanionViewModel.isTransientSpeechError(
            android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertFalse(CompanionViewModel.isTransientSpeechError(
            android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
    }
}
