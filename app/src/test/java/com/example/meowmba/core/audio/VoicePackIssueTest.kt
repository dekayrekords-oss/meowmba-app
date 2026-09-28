package com.example.meowmba.core.audio

import com.example.meowmba.ui.companion.CompanionState
import com.example.meowmba.ui.companion.showVoicePackNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Found recording the Android demo (2026-09-27): a first launch without internet left raw socket errors on home. */
class VoicePackIssueTest {
    @Test fun networkFailuresReadAsOfflineNotRawExceptions() {
        assertEquals(VoicePackIssue.OFFLINE, VoicePackIssue.label(java.net.UnknownHostException("Unable to resolve host \"us.aws.cdn.hf.co\"")))
        assertEquals(VoicePackIssue.OFFLINE, VoicePackIssue.label(java.net.SocketException("Software caused connection abort")))
        assertEquals(VoicePackIssue.OFFLINE, VoicePackIssue.label(java.io.IOException("wrapped", java.net.SocketTimeoutException("timeout"))))
        assertEquals(VoicePackIssue.FAILED, VoicePackIssue.label(IllegalStateException("bad archive")))
        assertNull(VoicePackIssue.label(null))
    }

    @Test fun cardShowsOnlyWhileDownloadingOrReporting() {
        // After a failure used to read "Getting voice models…" forever.
        assertFalse(CompanionState(voicePackReady = false, voicePackLabel = null, voicePackProgress = null).showVoicePackNotice())
        assertTrue(CompanionState(voicePackReady = false, voicePackProgress = .2f).showVoicePackNotice())
        assertTrue(CompanionState(voicePackReady = false, voicePackLabel = VoicePackIssue.OFFLINE).showVoicePackNotice())
    }
}

class SpeechMarkdownTest {
    @Test fun markdownMarkersAreNotSpoken() {
        assertEquals("Design review at 2:30 — finish the slides.", SpeechText.clean("**Design review at 2:30** — finish the slides."))
        assertEquals("Today\nRun git pull.", SpeechText.clean("## Today\nRun `git pull`."))
    }
}
