package com.example.meowmba.core.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Provider-neutral speech abstraction layer.
 *
 * The companion never talks to Android's SpeechRecognizer / TextToSpeech
 * directly. Swapping in Whisper, sherpa-onnx, Vosk, Piper, or a cloud speech
 * API means providing another implementation of these interfaces — the
 * ViewModel and avatar never change.
 */

/** Speech-to-text provider. */
interface SpeechEngine {
    /** True while the recognizer is actively capturing audio. */
    val isListening: StateFlow<Boolean>

    /** Latest partial (in-progress) transcript, empty when none. */
    val partialResults: StateFlow<String>

    /**
     * False when the device has no usable recognition service at all
     * (e.g. no Google app / voice typing service) — sessions would only
     * ever fail. Defaults to true so test fakes need no changes.
     */
    val isAvailable: Boolean get() = true

    /**
     * Begin one recognition session.
     * [onResult] delivers the final transcript, [onError] the platform error code.
     */
    fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit)

    /**
     * Face is in focus: use a longer silence window so mid-sentence pauses
     * are not cut off. Default no-op for fakes.
     */
    fun setFocusedListening(on: Boolean) {}

    /**
     * Swap callbacks on an already-running recognizer without stopping it.
     * Useful when barge-in / interruption transitions to follow-up — avoids
     * restarting the recognizer and losing audio on devices (Samsung) where
     * stop+start can stall.
     */
    fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit)

    /** Stop the current session early (the result callback may still fire). */
    fun stopListening()

    /** Release recognizer resources. */
    fun destroy()
}

/** Text-to-speech provider. */
interface SpeechSynthesizer {
    var onSpeechStarted: (() -> Unit)?
    var onSpeechFinished: (() -> Unit)?

    /**
     * Fired at word boundaries while speaking (API 26+; may never fire on
     * older devices or engines without range callbacks). Used to synchronize
     * the avatar's mouth with actual speech rhythm.
     */
    var onWordBoundary: ((start: Int, end: Int) -> Unit)?

    /** Speak [text], replacing anything currently playing. */
    fun speak(text: String)

    /**
     * Append [text] after whatever is already queued, without interrupting it.
     *
     * Used to speak a streamed reply sentence by sentence while the model is still
     * generating. Default delegates to [speak] so simple fakes need no changes — but
     * that default CUTS OFF prior speech, so real implementations should override.
     */
    fun speakMore(text: String) = speak(text)

    /** Stop playback immediately (user interruption). */
    fun stop()

    /** Release engine resources. */
    fun shutdown()
}
