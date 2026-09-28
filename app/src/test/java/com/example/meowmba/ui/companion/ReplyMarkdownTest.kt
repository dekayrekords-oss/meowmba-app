package com.example.meowmba.ui.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Found recording the Android demo (2026-09-27): replies showed "**Design review at 2:30**" with raw asterisks. */
class ReplyMarkdownTest {
    @Test fun boldItalicCodeAndHeadingsRenderWithoutMarkers() {
        val p = ReplyMarkdown.parse("Here's the shape of today:\n\n1. **Design review at 2:30** — finish the *slides*.\n## Later\nRun `git pull`.")
        assertEquals("Here's the shape of today:\n\n1. Design review at 2:30 — finish the slides.\nLater\nRun git pull.", p.text)
        val styled = p.spans.map { p.text.substring(it.start, it.end) to it.style }
        assertTrue(("Design review at 2:30" to ReplyMarkdown.Style.BOLD) in styled)
        assertTrue(("slides" to ReplyMarkdown.Style.ITALIC) in styled)
        assertTrue(("Later" to ReplyMarkdown.Style.BOLD) in styled)
        assertTrue(("git pull" to ReplyMarkdown.Style.CODE) in styled)
    }

    @Test fun plainTextAndLoneAsterisksAreLeftAlone() {
        assertEquals("5 * 3 = 15, and 2*x", ReplyMarkdown.parse("5 * 3 = 15, and 2*x").text)
        assertEquals("No markdown here.", ReplyMarkdown.parse("No markdown here.").text)
        // Links are still found in the formatted text.
        assertEquals("See https://example.com today", chatSourceLinks("See **https://example.com** today").text)
    }
}

/** Found recording the Android demo: with chat open, every reply was printed twice. */
class ChatListenLineTest {
    @Test fun replyIsNotRepeatedAsATranscript() {
        val reply = "Done. Anytime."
        assertEquals(false, showListenLine(listening = false, partial = reply, reply = reply))
        assertEquals(true, showListenLine(listening = false, partial = "What should I focus on?", reply = reply))
        assertEquals(true, showListenLine(listening = true, partial = null, reply = reply))
        assertEquals(false, showListenLine(listening = false, partial = null, reply = reply))
    }
}
