package com.example.meowmba.core.audio

/**
 * How a conversational voice turn is answered.
 *
 * Recognition is deliberately configured separately through [AsrBackend]. Even in
 * [NATIVE_AUDIO], the local recognizer still owns wake-word hand-off, endpointing,
 * deterministic commands, captions, and the auditable text fallback. The selected
 * audio-native model receives the original microphone PCM for understanding and
 * returns speech audio directly, so ordinary conversation does not pass through a
 * cloud ASR -> text LLM -> Android TTS cascade.
 */
enum class SpeechPipelineMode(
    val id: String,
    val displayName: String,
    val subtitle: String,
    /** True when a completed utterance is sent off-device. */
    val isCloud: Boolean,
) {
    CASCADE(
        id = "cascade",
        displayName = "ASR + LLM + TTS",
        subtitle = "Current modular pipeline; works with local, LAN, and cloud brains",
        isCloud = false,
    ),
    NATIVE_AUDIO(
        id = "native_audio",
        displayName = "End-to-end speech",
        subtitle = "Audio-native understanding and voice generation with the OpenAI audio model",
        isCloud = true,
    );

    companion object {
        const val PREF_KEY = "speech_pipeline_mode"
        val DEFAULT = CASCADE

        fun fromId(id: String?): SpeechPipelineMode =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Honest, testable availability state for a speech pipeline choice. */
data class SpeechPipelineStatus(
    val mode: SpeechPipelineMode,
    val available: Boolean,
    val reason: String = "",
) {
    companion object {
        fun evaluateAll(nativeAudioAvailable: Boolean): List<SpeechPipelineStatus> =
            SpeechPipelineMode.entries.map { mode ->
                when (mode) {
                    SpeechPipelineMode.CASCADE -> SpeechPipelineStatus(mode, available = true)
                    SpeechPipelineMode.NATIVE_AUDIO -> if (nativeAudioAvailable) {
                        SpeechPipelineStatus(mode, available = true)
                    } else {
                        SpeechPipelineStatus(
                            mode,
                            available = false,
                            reason = "Add an OpenAI API key in Settings",
                        )
                    }
                }
            }
    }
}

