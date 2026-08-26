package com.example.meowmba

import com.example.meowmba.core.audio.TranscriptSelector
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptSelectorTest {
    @Test
    fun `highest confidence final wins`() {
        assertEquals(
            "open Spotify",
            TranscriptSelector.select(
                finals = listOf("open spot a fly", "open Spotify"),
                confidences = floatArrayOf(0.31f, 0.92f),
            ),
        )
    }

    @Test
    fun `longer compatible partial repairs a truncated final`() {
        assertEquals(
            "send a message to Wyatt saying I am home",
            TranscriptSelector.select(
                finals = listOf("send a message to Wyatt"),
                lastPartial = "send a message to Wyatt saying I am home",
            ),
        )
    }

    @Test
    fun `unrelated partial never replaces a final`() {
        assertEquals(
            "turn on the lights",
            TranscriptSelector.select(
                finals = listOf("turn on the lights"),
                lastPartial = "play something completely different now",
            ),
        )
    }
}
