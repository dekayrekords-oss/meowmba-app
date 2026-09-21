package com.example.meowmba

import com.example.meowmba.core.audio.SelfVoiceFilter
import org.junit.Assert.*
import org.junit.Test

class SpeechEchoTailTest {
    @Test fun shortConfirmationIsNotAnEchoOfALongerPrompt() {
        val filter = SelfVoiceFilter()
        filter.begin("You can say yes or cancel to continue.")
        filter.finish()
        assertFalse(filter.isOwnVoice("cancel", filterShortNoise = false))
        assertFalse(filter.isOwnVoice("yes", filterShortNoise = false))
        filter.begin("Yes?")
        filter.finish()
        assertTrue(filter.isOwnVoice("Yes", filterShortNoise = false))
    }

    @Test fun streamedReplyKeepsEarlierSentencesAndTail() {
        var now = 0L
        val filter = SelfVoiceFilter { now }
        filter.begin("Meow can help you find the next song.")
        filter.append("Choose your favorite artist and enjoy the music.")
        filter.onWordSpoken(40, 55)
        assertTrue(filter.isOwnVoice("Meow can help you find the next song"))
        filter.finish()
        now = 3_000L
        assertTrue(filter.isOwnVoice("choose your favorite artist"))
        assertFalse(filter.isOwnVoice("Meow stop and open settings"))
        now = 8_001L
        assertFalse(filter.isOwnVoice("choose your favorite artist"))
    }
}
