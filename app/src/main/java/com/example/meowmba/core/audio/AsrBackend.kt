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
     * Health-driven: platform recognizer while it answers, local sherpa once
     * it wedges. The behaviour that shipped before the picker existed.
     */
    AUTO(
        id = "auto",
        displayName = "Automatic",
        subtitle = "Phone recognizer, falling back to on-device when it wedges",
        isCloud = false,
    ),

    /** Android's own recognizer (AiAi / Google). Accurate until it wedges BUSY. */
    PLATFORM(
        id = "platform",
        displayName = "Phone recognizer",
        subtitle = "Android / Google voice typing — accurate, but wedges under load",
        isCloud = false,
    ),

    /** sherpa-onnx streaming zipformer. Fully offline, no network at all. */
    LOCAL_SHERPA(
        id = "local_sherpa",
        displayName = "On-device (sherpa)",
        subtitle = "Fully offline streaming zipformer — private, lower accuracy",
        isCloud = false,
    ),

    /**
     * Local sherpa for live partials + endpointing, then the finished
     * utterance is transcribed by Gemini for the final text. Most accurate,
     * needs network and an OpenRouter key.
     */
    GEMINI_CLOUD(
        id = "gemini_cloud",
        displayName = "Gemini (cloud)",
        subtitle = "On-device preview, then Gemini transcribes — most accurate, sends audio",
        isCloud = true,
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

        val DEFAULT = AUTO

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
        ): AsrBackendStatus = when (backend) {
            AsrBackend.AUTO ->
                if (platformAvailable || localModelReady) {
                    AsrBackendStatus(backend, true)
                } else {
                    AsrBackendStatus(backend, false, "No recognizer available")
                }

            AsrBackend.PLATFORM ->
                if (platformAvailable) {
                    AsrBackendStatus(backend, true)
                } else {
                    AsrBackendStatus(backend, false, "No voice typing service on this device")
                }

            AsrBackend.LOCAL_SHERPA ->
                if (localModelReady) {
                    AsrBackendStatus(backend, true)
                } else {
                    AsrBackendStatus(backend, false, "Model not downloaded yet")
                }

            // Gemini rides on sherpa for endpointing, so it needs BOTH the
            // local model and a key. Saying only "needs a key" would be a lie
            // that sends the user to Settings for nothing.
            AsrBackend.GEMINI_CLOUD -> when {
                !cloudKeyConfigured && !localModelReady ->
                    AsrBackendStatus(backend, false, "Needs the on-device model and an OpenRouter key")
                !cloudKeyConfigured ->
                    AsrBackendStatus(backend, false, "Add an OpenRouter API key in Settings")
                !localModelReady ->
                    AsrBackendStatus(backend, false, "Needs the on-device model for endpointing")
                else -> AsrBackendStatus(backend, true)
            }
        }

        fun evaluateAll(
            platformAvailable: Boolean,
            localModelReady: Boolean,
            cloudKeyConfigured: Boolean,
        ): List<AsrBackendStatus> = AsrBackend.entries.map {
            evaluate(it, platformAvailable, localModelReady, cloudKeyConfigured)
        }
    }
}
