package com.example.meowmba

import android.content.ComponentName
import com.example.meowmba.core.audio.SpeechService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecognitionCandidatesTest {

    private val googleVoice = ComponentName(
        "com.google.android.googlequicksearchbox",
        "com.google.android.voicesearch.serviceapi.GoogleRecognitionService",
    )
    private val aiai = ComponentName(
        "com.google.android.as",
        "com.google.android.apps.miphone.aiai.common.recognition.service.AiAiSpeechRecognitionService",
    )
    private val tts = ComponentName(
        "com.google.android.tts",
        "com.google.android.tts.service.GoogleTTSRecognitionService",
    )
    private val other = ComponentName(
        "com.example.other",
        "com.example.other.RecognitionService",
    )

    @Test
    fun `Fold case pins AiAi first and never falls back to TTS`() {
        val ranked = SpeechService.rankRecognitionCandidates(
            installed = listOf(aiai, googleVoice),
            googleVoiceUsable = true,
            systemDefault = tts,
        )
        assertEquals(aiai, ranked.first())
        assertTrue(ranked.none { it?.packageName == "com.google.android.tts" })
        assertTrue(ranked.none { it?.packageName == "com.google.android.googlequicksearchbox" })
        assertTrue(null !in ranked)
    }

    @Test
    fun `Voice Search is never a candidate even when the pin is usable`() {
        val ranked = SpeechService.rankRecognitionCandidates(
            installed = listOf(aiai),
            googleVoiceUsable = true,
            systemDefault = tts,
        )
        assertEquals(listOf(aiai), ranked)
    }

    @Test
    fun `only TTS installed produces an empty list, not platform default`() {
        val ranked = SpeechService.rankRecognitionCandidates(
            installed = emptyList(),
            googleVoiceUsable = false,
            systemDefault = tts,
        )
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `AiAi is used when Voice Search is missing`() {
        val ranked = SpeechService.rankRecognitionCandidates(
            installed = listOf(aiai),
            googleVoiceUsable = false,
            systemDefault = tts,
        )
        assertEquals(listOf(aiai), ranked)
    }

    @Test
    fun `unknown system default is a last-resort platform slot`() {
        val ranked = SpeechService.rankRecognitionCandidates(
            installed = emptyList(),
            googleVoiceUsable = false,
            systemDefault = null,
        )
        assertEquals(1, ranked.size)
        assertNull(ranked.single())
    }

    @Test
    fun `a non-TTS system default is kept after Google and AiAi`() {
        val ranked = SpeechService.rankRecognitionCandidates(
            installed = listOf(aiai),
            googleVoiceUsable = true,
            systemDefault = other,
        )
        assertEquals(listOf(aiai, other), ranked)
    }
}
