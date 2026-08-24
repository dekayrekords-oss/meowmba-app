package com.example.meowmba.core.audio

import android.speech.SpeechRecognizer

/**
 * Pure rules for who owns the microphone.
 *
 * Kept free of Android runtime so the wedge / always-on / wake handoff can be
 * unit-tested without a device. The Fold's SpeechRecognizer (AiAi BUSY,
 * Google TOO_MANY_REQUESTS) is why [recognizerHealthy] exists: after a few
 * contention refusals the open-mic loop must give the hardware to the
 * on-device keyword spotter instead of retrying forever and blocking the
 * mic button.
 */
object VoicePolicy {

    /** Consecutive BUSY/CLIENT/10/11 refusals before always-on yields to wake. */
    const val ALWAYS_ON_WEDGE_STRIKES = 2

    fun shouldAlwaysOnMic(
        isMicEnabled: Boolean,
        isMuted: Boolean,
        handedOffToBackground: Boolean,
        faceModeActive: Boolean,
        requireWakeWordInFace: Boolean,
        recognizerHealthy: Boolean,
    ): Boolean = isMicEnabled &&
        !isMuted &&
        !handedOffToBackground &&
        faceModeActive &&
        !requireWakeWordInFace &&
        recognizerHealthy

    /**
     * Face-mode just-talk: any speech is a command, not only "hey meow".
     * Same gate as [shouldAlwaysOnMic] — the always-on STT loop wedges AiAi,
     * so the live path is VAD on the keyword spotter handing off to one STT turn.
     */
    fun shouldCaptureBareSpeech(
        isMicEnabled: Boolean,
        isMuted: Boolean,
        handedOffToBackground: Boolean,
        faceModeActive: Boolean,
        requireWakeWordInFace: Boolean,
        recognizerHealthy: Boolean,
    ): Boolean = shouldAlwaysOnMic(
        isMicEnabled = isMicEnabled,
        isMuted = isMuted,
        handedOffToBackground = handedOffToBackground,
        faceModeActive = faceModeActive,
        requireWakeWordInFace = requireWakeWordInFace,
        recognizerHealthy = recognizerHealthy,
    )

    /**
     * Delayed wake-spotter re-arm. A 900ms retry from a pause must not
     * start AudioRecord on top of a live command capture (that is the
     * "mic button is deaf" race: KWS holds the hardware, SpeechRecognizer
     * gets BUSY, nothing hears the user).
     */
    fun shouldStartWakeSpotter(
        isMicEnabled: Boolean,
        wakeWordEnabled: Boolean,
        handedOffToBackground: Boolean,
        interactionIdle: Boolean,
        captureInFlight: Boolean,
    ): Boolean = isMicEnabled &&
        wakeWordEnabled &&
        !handedOffToBackground &&
        interactionIdle &&
        !captureInFlight

    /**
     * The recognition service refused us or never became ready. Retrying the
     * same open-mic loop just re-wedges AiAi / Google and keeps AudioRecord
     * away from the keyword spotter and the mic button.
     */
    fun isContentionError(code: Int): Boolean = when (code) {
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_CLIENT,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> true
        else -> false
    }
}
