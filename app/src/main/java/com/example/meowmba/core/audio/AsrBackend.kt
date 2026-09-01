package com.example.meowmba.core.audio

/**
 * Which speech-to-text backend the user picked on the Hardware → Voice
 * Recognition page.
 *
 * Deliberately pure (no Context, no android.*): the selection matrix is a
 * unit test, not a device session — same reasoning as [VoiceHealth].
 *
 * Until this existed the engine was chosen entirely by [VoiceHealth]'s
 * health heuristics, which is why "recognition is terrible" had no user-facing
 * fix: the local sherpa model mishears (its own KDoc says so) and there was no
 * way to say "use the accurate one".
 */
enum class AsrBackend(
    /** Stable id for [com.example.meowmba.core.security.SecureStorageManager]. */
    val id: String,
    val displayName: String,
    val subtitle: String,
    /** True when audio leaves the device. Surfaced in the UI, never hidden. */
    val isCloud: Boolean,
) {
    /**
     * Whisper base.en on-device for the FINAL transcript, with the streaming
     * zipformer still driving live partials + endpointing. Fully offline: no
     * network, no API key, nothing leaves the phone.
     *
     * This exists because the streaming zipformer measurably mishears — the
     * "recognition is terrible" reports were it, not a settings problem.
     * Whisper is a batch model (no live partials of its own), hence the
     * hybrid: zipformer for the live text, Whisper for what actually gets
     * answered. Needs the Whisper model downloaded.
     */
    WHISPER_LOCAL(
        id = "whisper_local",
        displayName = "Whisper (on-device)",
        subtitle = "Local Whisper for the final text — most accurate offline, no key, no network",
        isCloud = false,
    ),

    /**
     * Ray-Ban Meta glasses mic over Bluetooth SCO, transcribed on-device by
     * sherpa. The glasses ARE a Bluetooth headset to Android (MWDAT 0.8.0 has
     * no public mic API — verified against the AAR), so capture rides the
     * standard SCO link; falls through to the phone mic path when no glasses
     * are connected. Fully offline.
     */
    META_GLASSES_MIC(
        id = "meta_glasses_mic",
        displayName = "Meta glasses mic",
        subtitle = "Hear through the Ray-Ban glasses when they're connected — on-device, offline",
        isCloud = false,
    );

    companion object {
        /** SecureStorageManager preference key. */
        const val PREF_KEY = "asr_backend"

        /** OpenRouter provider slot for the ASR model name (mirrors "openrouter_vision"). */
        const val OPENROUTER_ASR_PROVIDER = "openrouter_asr"

        /**
         * Verified present in OpenRouter's live catalog with `audio` among its
         * input modalities (checked 2026-08-22). Overridable per-install via
         * `saveModelName(OPENROUTER_ASR_PROVIDER, …)` so a newer Gemini can be
         * selected without an app update.
         */
        const val DEFAULT_GEMINI_MODEL = "google/gemini-3.6-flash"

        /**
         * Whisper is the default.
         *
         * The streaming zipformer alone measurably mishears — that was the
         * "recognition is terrible" report — and a health-driven AUTO pick
         * spent most of its time wedged onto that weaker engine. Whisper is
         * the accurate one, so it is what you get unless you deliberately
         * choose the glasses mic.
         *
         * Note this also migrates anyone on a removed backend: [fromId] fails
         * to match ids like "auto"/"platform" and falls through to here.
         */
        val DEFAULT = WHISPER_LOCAL

        fun fromId(id: String?): AsrBackend =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * What each backend can actually do *right now* on this device.
 *
 * House rule is honesty over polish: the picker badges a backend UNAVAILABLE
 * with the reason rather than letting the user select something that will
 * silently fall back to a different engine.
 */
data class AsrBackendStatus(
    val backend: AsrBackend,
    val available: Boolean,
    /** Why it is unavailable — empty when [available]. */
    val reason: String = "",
) {
    companion object {
        /**
         * Pure availability matrix. Callers supply the three facts; this stays
         * testable without a device.
         */
        fun evaluate(
            backend: AsrBackend,
            platformAvailable: Boolean,
            localModelReady: Boolean,
            cloudKeyConfigured: Boolean,
            glassesConnected: Boolean = false,
            whisperReady: Boolean = false,
        ): AsrBackendStatus = when (backend) {
            // Whisper needs BOTH: the streaming pack for live partials +
            // endpointing, and the Whisper weights for the final text.
            // Reporting only one would send the user to Settings for nothing.
            AsrBackend.WHISPER_LOCAL -> when {
                !localModelReady && !whisperReady ->
                    AsrBackendStatus(backend, false, "Needs the streaming ASR model and Whisper")
                !localModelReady ->
                    AsrBackendStatus(backend, false, "Needs the on-device streaming ASR model")
                !whisperReady ->
                    AsrBackendStatus(backend, false, "Needs the Whisper model — download it on this page")
                else -> AsrBackendStatus(backend, true)
            }

            AsrBackend.META_GLASSES_MIC -> when {
                !localModelReady ->
                    AsrBackendStatus(backend, false, "Needs the on-device model for transcription")
                !glassesConnected ->
                    AsrBackendStatus(backend, false, "No glasses connected — connect them on the Hardware page")
                else -> AsrBackendStatus(backend, true)
            }
        }

        fun evaluateAll(
            platformAvailable: Boolean,
            localModelReady: Boolean,
            cloudKeyConfigured: Boolean,
            glassesConnected: Boolean = false,
            whisperReady: Boolean = false,
        ): List<AsrBackendStatus> = AsrBackend.entries.map {
            evaluate(
                it,
                platformAvailable,
                localModelReady,
                cloudKeyConfigured,
                glassesConnected,
                whisperReady,
            )
        }
    }
}
