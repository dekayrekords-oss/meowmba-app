package com.example.meowmba

import com.example.meowmba.core.audio.SpeechChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The chunker decides when a streamed reply is safe to start speaking. Getting it
 * wrong is audible — either dead air (never releasing) or stuttering (releasing
 * fragments) — so the edge cases are pinned here.
 */
class SpeechChunkerTest {

    @Test
    fun `holds back an unfinished sentence`() {
        assertNull(SpeechChunker.nextChunk("Let me check that for", 0))
    }

    @Test
    fun `releases a complete sentence`() {
        val c = SpeechChunker.nextChunk("The nearest one is Culver's. Want directions", 0)
        assertEquals("The nearest one is Culver's.", c?.trim())
    }

    @Test
    fun `releases several sentences as one utterance`() {
        // A burst containing two sentences should be spoken together, not clipped.
        val c = SpeechChunker.nextChunk("First thing here. Second thing here. And a par", 0)
        assertEquals("First thing here. Second thing here.", c?.trim())
    }

    @Test
    fun `advances past what was already spoken`() {
        // Streaming arrives incrementally: the second sentence only completes on a
        // later token, so each call releases what is newly finished.
        val afterFirst = "First sentence here. Second sen"
        val first = SpeechChunker.nextChunk(afterFirst, 0)!!
        assertEquals("First sentence here.", first.trim())

        val afterSecond = "First sentence here. Second sentence here. Third"
        val second = SpeechChunker.nextChunk(afterSecond, first.length)
        assertEquals("Second sentence here.", second?.trim())
    }

    @Test
    fun `returns raw substring so offsets stay aligned`() {
        // The caller adds chunk.length to its cursor; trimming here would desync it.
        val full = "One done here. Two"
        val c = SpeechChunker.nextChunk(full, 0)!!
        assertEquals("One done here.", c)
        assertEquals("One done here.".length, c.length)
    }

    @Test
    fun `a list marker is not a sentence end`() {
        // "1." would otherwise release a 2-char fragment.
        assertNull(SpeechChunker.nextChunk("1. ", 0))
    }

    @Test
    fun `question and exclamation both end sentences`() {
        assertEquals(
            "Do you want the closest one?",
            SpeechChunker.nextChunk("Do you want the closest one? Or", 0)?.trim(),
        )
        assertEquals(
            "Found it, routing you there!",
            SpeechChunker.nextChunk("Found it, routing you there! Next", 0)?.trim(),
        )
    }

    @Test
    fun `newline ends a chunk for list output`() {
        val c = SpeechChunker.nextChunk("Here are the closest three\nCulver's", 0)
        assertEquals("Here are the closest three", c?.trim())
    }

    @Test
    fun `out of range start is safe`() {
        assertNull(SpeechChunker.nextChunk("Anything.", 99))
        assertNull(SpeechChunker.nextChunk("", 0))
        assertNull(SpeechChunker.nextChunk("Anything.", -1))
    }

    @Test
    fun `sentence ending exactly at end of buffer is released`() {
        assertEquals(
            "That is all done.",
            SpeechChunker.nextChunk("That is all done.", 0)?.trim(),
        )
    }
}
