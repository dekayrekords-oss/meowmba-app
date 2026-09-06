package com.example.meowmba

import com.example.meowmba.ui.companion.CompanionViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Show it in the browser" intent routing.
 *
 * The matcher must fire for display-only requests and stay out of the way
 * when the user is asking meow to BUILD something in the browser — those
 * belong to the browser agent loop.
 */
class ShowInBrowserIntentTest {

    private fun matches(text: String): Boolean =
        CompanionViewModel.looksLikeShowInBrowser(text.lowercase())

    @Test
    fun `classic show request matches`() {
        assertTrue(matches("show it in the browser"))
        assertTrue(matches("show me in the browser"))
        assertTrue(matches("show that in the browser"))
        assertTrue(matches("open it in chrome"))
        assertTrue(matches("show it in the file viewer"))
    }

    @Test
    fun `other display verbs match`() {
        assertTrue(matches("open it in the browser"))
        assertTrue(matches("display the page in the browser"))
        assertTrue(matches("put that in the browser"))
        assertTrue(matches("bring it up in the browser"))
    }

    @Test
    fun `side panel phrasing matches`() {
        assertTrue(matches("show it in the side browser"))
        assertTrue(matches("open that on the browser"))
        assertTrue(matches("show it in browser"))
    }

    @Test
    fun `creation requests stay with the browser agent`() {
        assertFalse(matches("make me a web page and show it in the browser"))
        assertFalse(matches("build a page and show me in the browser"))
        assertFalse(matches("create an image and show it in the browser"))
        assertFalse(matches("generate a video and open it in the browser"))
    }

    @Test
    fun `no browser mention never matches`() {
        assertFalse(matches("show me the weather"))
        assertFalse(matches("open the chat"))
        assertFalse(matches("display my calendar"))
    }

    @Test
    fun `browser without a display verb never matches`() {
        assertFalse(matches("what is the browser for"))
        assertFalse(matches("close the browser"))
    }
}
