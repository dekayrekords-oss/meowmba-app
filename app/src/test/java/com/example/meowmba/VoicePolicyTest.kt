package com.example.meowmba

import android.speech.SpeechRecognizer
import com.example.meowmba.ui.companion.CompanionState
import com.example.meowmba.ui.companion.VoicePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePolicyTest {

    @Test
    fun `a face in view opens the mic even if wake-in-face is on`() {
        val s = CompanionState(
            userInView = true,
            requireWakeWordInFace = true,
            isMicEnabled = true,
            isMuted = false,
        )
        assertTrue(VoicePolicy.shouldAlwaysOnMic(s))
    }

    @Test
    fun `no face and wake-in-face stays on the hot word`() {
        val s = CompanionState(
            userInView = false,
            requireWakeWordInFace = true,
            isMicEnabled = true,
        )
        assertFalse(VoicePolicy.shouldAlwaysOnMic(s))
    }

    @Test
    fun `a voice call keeps the mic even when chat covers the face`() {
        val s = CompanionState(
            callMode = true,
            isChatExpanded = true,
            isMicEnabled = true,
            requireWakeWordInFace = true,
        )
        assertTrue(VoicePolicy.shouldAlwaysOnMic(s))
        assertTrue(VoicePolicy.shouldEnterFollowUp(s))
    }

    @Test
    fun `privacy mic-off or mute never opens the mic`() {
        assertFalse(
            VoicePolicy.shouldAlwaysOnMic(
                CompanionState(userInView = true, isMicEnabled = false),
            ),
        )
        assertFalse(
            VoicePolicy.shouldAlwaysOnMic(
                CompanionState(userInView = true, isMuted = true),
            ),
        )
        assertTrue(
            VoicePolicy.shouldAlwaysOnMic(
                CompanionState(userInView = true, isMuted = true, isNoting = true),
            ),
        )
    }

    @Test
    fun `a wedged platform recognizer still keeps face always-on`() {
        val s = CompanionState(userInView = true, isMicEnabled = true)
        assertTrue(VoicePolicy.shouldAlwaysOnMic(s))
        assertTrue(VoicePolicy.shouldAlwaysOnMic(s, recognizerHealthy = false))
    }

    @Test
    fun `busy too-many and never-ready are contention`() {
        assertEquals(2, VoicePolicy.ALWAYS_ON_WEDGE_STRIKES)
        assertTrue(VoicePolicy.isContentionError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertTrue(VoicePolicy.isContentionError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS))
        assertTrue(VoicePolicy.isContentionError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertFalse(VoicePolicy.isContentionError(SpeechRecognizer.ERROR_NO_MATCH))
    }

    @Test
    fun `close faces count as in focus`() {
        assertTrue(VoicePolicy.userInFocus(hasFace = true, scale = 1.1f))
        assertFalse(VoicePolicy.userInFocus(hasFace = true, scale = 0.4f))
        assertFalse(VoicePolicy.userInFocus(hasFace = false, scale = 2f))
    }

    @Test
    fun `speech after a camera-off nap reopens just-talk`() {
        // Cover-screen CAM OFF + 60s quiet drops to wake. Talking (not
        // "hey meow") must still bring always-on back, or the face is deaf.
        assertTrue(
            VoicePolicy.shouldResumeAlwaysOnFromSpeech(
                CompanionState(
                    userInView = false,
                    requireWakeWordInFace = false,
                    isMicEnabled = true,
                    isMuted = false,
                ),
            ),
        )
        assertFalse(
            VoicePolicy.shouldResumeAlwaysOnFromSpeech(
                CompanionState(
                    userInView = false,
                    requireWakeWordInFace = true,
                    isMicEnabled = true,
                ),
            ),
        )
    }

    @Test
    fun `a listen timing out while Meow answers does not cut the reply off`() {
        assertTrue(VoicePolicy.isStaleListenError(com.example.meowmba.ui.companion.InteractionState.SPEAKING))
        assertTrue(VoicePolicy.isStaleListenError(com.example.meowmba.ui.companion.InteractionState.THINKING))
        assertFalse(VoicePolicy.isStaleListenError(com.example.meowmba.ui.companion.InteractionState.LISTENING))
        assertFalse(VoicePolicy.isStaleListenError(com.example.meowmba.ui.companion.InteractionState.IDLE))
    }
}
