package com.example.meowmba.core.audio

/** PCM captured for one completed user turn. */
data class CapturedSpeechAudio(
    val pcm16: ShortArray,
    val sampleRate: Int,
) {
    val isUsable: Boolean get() = pcm16.isNotEmpty() && sampleRate > 0
}

/** Optional capability implemented by recognizers that retain the microphone PCM. */
interface SpeechAudioCapture {
    fun snapshotCapturedAudio(): CapturedSpeechAudio?
}

data class NativeSpeechRequest(
    /** Original microphone audio; this, not [fallbackTranscript], is the model input. */
    val audio: CapturedSpeechAudio,
    /** Persona, memories, device facts, and prior turns; excludes the current transcript. */
    val instructions: String,
    /** Local transcript used only for diagnostics and the cascade fallback. */
    val fallbackTranscript: String,
)

data class NativeSpeechReply(
    /** Complete WAV file returned by the audio-native model. */
    val wav: ByteArray,
    /** Model-provided transcript of its generated audio. */
    val transcript: String,
)

sealed interface NativeSpeechResponse {
    data class Success(val reply: NativeSpeechReply) : NativeSpeechResponse
    data class Failure(val reason: String) : NativeSpeechResponse
}

/**
 * Audio-in/audio-out conversational model.
 *
 * This is intentionally not a [SpeechEngine] or [SpeechSynthesizer]: those two
 * interfaces encode a text boundary. Keeping this seam separate makes it impossible
 * to accidentally call the audio model "TTS" while still allowing the cascade to be
 * retained as an explicit fallback.
 */
interface NativeSpeechEngine {
    val isAvailable: Boolean
    suspend fun respond(request: NativeSpeechRequest): NativeSpeechResponse
    fun cancel()
    fun close()
}

/** Playback boundary kept injectable so ViewModel behavior remains pure-JVM testable. */
interface NativeSpeechPlayer {
    fun play(
        wav: ByteArray,
        onStarted: () -> Unit,
        onEnergy: (Float) -> Unit,
        onFinished: () -> Unit,
        onError: (String) -> Unit,
    )

    fun stop()
    fun release()
}

