package com.example.meowmba.ui.companion

import android.speech.SpeechRecognizer

/**
 * Speech-error classification and follow-up policy.
 *
 * Pure functions extracted from [CompanionViewModel]'s companion so the
 * recognition-error matrix can be changed without opening the ViewModel.
 * Companion methods still delegate here for existing tests.
 */
object VoicePolicy {

    private val TRANSIENT_SPEECH_ERRORS = setOf(
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    )

    /** Codes meaning "the recognition service hiccuped", not "broken setup". */
    fun isTransientSpeechError(code: Int): Boolean = code in TRANSIENT_SPEECH_ERRORS

    /** Codes meaning "heard nothing yet" (silence), not an error. */
    fun isSilenceError(code: Int): Boolean =
        code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            code == SpeechRecognizer.ERROR_NO_MATCH

    /**
     * Errors that must NOT end an active follow-up. Silence (6/7) plus the two
     * session-collision codes, ERROR_CLIENT (5) and ERROR_RECOGNIZER_BUSY (8):
     * those mean another recognizer session (the barge-in watch or a wake re-arm)
     * raced the capture, and they resolve by re-opening rather than resetting.
     */
    fun isFollowUpKeepAliveError(code: Int): Boolean =
        isSilenceError(code) ||
            code == SpeechRecognizer.ERROR_CLIENT ||
            code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

    /**
     * Phase E3 state machine: after the companion finishes speaking, do we
     * stay hot in FOLLOW_UP mode? Conversation mode always does; barge-in
     * does it whenever the wake word is armed. Mic off / muted never.
     */
    fun shouldEnterFollowUp(s: CompanionState): Boolean =
        s.isMicEnabled && !s.isMuted &&
            (s.conversationMode || (s.bargeInEnabled && s.wakeWordEnabled))

    /**
     * Retry gap after the recognizer refuses us, doubling per consecutive
     * refusal: 2.5s, 5s, 10s, 20s, 40s, then a 60s ceiling.
     *
     * The ceiling matters as much as the growth. A wedged AiAi can stay
     * wedged for minutes, and a retry that keeps arriving is part of why —
     * but we must never stop retrying entirely, or the mic would need an
     * app restart to come back.
     */
    fun contentionBackoffMs(streak: Int): Long {
        val step = (streak - 1).coerceIn(0, 4)
        return (2_500L shl step).coerceAtMost(60_000L)
    }
}
