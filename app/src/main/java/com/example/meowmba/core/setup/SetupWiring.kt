package com.example.meowmba.core.setup

import android.content.Context
import com.example.meowmba.core.security.SecureStorageManager

/**
 * Single, beginner-oriented layer through which the "Connect your AI"
 * onboarding wizard reads and writes the app's provider configuration.
 *
 * Everything here is plain persistence over [SecureStorageManager] — no calls
 * into the (much larger, internal-only) routing/engine code — so the wizard UI
 * and any test can drive it without tripping over internal machinery. Where a
 * genuinely local action is needed (downloading an on-device model) the wizard
 * goes through [localModelDownloader], an injected hook that the app wires to
 * its real downloader at startup.
 *
 * Users choose, per domain, one of a small set of human paths:
 *   - LOCAL      — runs on this phone (may need a model download from Hugging Face)
 *   - FREE_CLOUD — works with a free API key
 *   - PAID_CLOUD — needs a cloud account + API key
 *
 * Honesty rule: a domain is "ready" only when its chosen path is actually
 * satisfied (key present, or local selected). We never claim a provider is set
 * up when it isn't.
 */
object SetupWiring {

    // ── shared storage ─────────────────────────────────────────────────────
    // Some reads need a Context (for the master key) and some are pure helpers.

    private const val PREF_ONBOARDING_DONE = "onboarding_done"
    private const val PREF_BRAIN_PROVIDER = "brain_provider"
    private const val PREF_TTS_PATH = "tts_path"
    private const val PREF_VOICE_PATH = "voice_path"

    // ── paths ──────────────────────────────────────────────────────────────

    enum class Path { LOCAL, FREE_CLOUD, PAID_CLOUD }

    /** One selectable cloud brain provider presented in the wizard. */
    data class BrainProvider(
        val id: String,
        val name: String,
        val path: Path,
        val getKeyUrl: String?,
        val defaultModel: String,
        val blurb: String,
    )

    /**
     * The brains the wizard offers. LOCAL is shown once, followed by the
     * free/paid cloud providers. Order matters for beginners.
     */
    val BRAIN_PROVIDERS: List<BrainProvider> = listOf(
        BrainProvider(
            id = "local",
            name = "Run a conversation model on this phone",
            path = Path.LOCAL,
            getKeyUrl = null,
            defaultModel = "local",
            blurb = "On-phone action routing needs a separate model download. " +
                "Voice models download separately; connected services can still require internet access.",
        ),
        BrainProvider(
            id = "bigpickle",
            name = "BigPickle (OpenCode Zen)",
            path = Path.FREE_CLOUD,
            getKeyUrl = "https://opencode.ai/zen",
            defaultModel = "big-pickle",
            blurb = "Cloud conversation using an OpenCode Zen key. " +
                "Check the provider's current availability and pricing before connecting.",
        ),
        BrainProvider(
            id = "openrouter",
            name = "OpenRouter",
            path = Path.PAID_CLOUD,
            getKeyUrl = "https://openrouter.ai/settings/keys",
            defaultModel = "nvidia/nemotron-3-super-120b-a12b:free",
            blurb = "One key reaches hundreds of models from many companies. Some are free, " +
                "others cost a little per use. Pay-as-you-go.",
        ),
        BrainProvider(
            id = "openai",
            name = "OpenAI (GPT)",
            path = Path.PAID_CLOUD,
            getKeyUrl = "https://platform.openai.com/api-keys",
            defaultModel = "gpt-4o-mini",
            blurb = "The well-known GPT models. Needs an OpenAI account and a key; " +
                "you pay per use.",
        ),
    )

    // ── local model-download hook (wired by the app) ───────────────────────

    enum class LocalModelKind { BRAIN, ASR, TTS }

    /** Method Signature: (context, kind) -> void. Wired to the real downloader. */
    var localModelDownloader: ((Context, LocalModelKind) -> Unit)? = null

    fun requestLocalModel(context: Context, kind: LocalModelKind) {
        localModelDownloader?.invoke(context, kind)
    }

    // ── brain ──────────────────────────────────────────────────────────────

    fun brainProviders(): List<BrainProvider> = BRAIN_PROVIDERS

    fun brainProvider(id: String?): BrainProvider? = BRAIN_PROVIDERS.firstOrNull { it.id == id }

    /**
     * Configure the brain. For cloud providers this saves the key + model under
     * the provider's own storage key (matching what Settings already uses) and
     * records "local" or the provider id. For LOCAL it just records the choice.
     */
    fun setBrain(context: Context, providerId: String, apiKey: String?, modelName: String?) {
        val s = storage(context)
        val p = brainProvider(providerId) ?: return
        if (p.path != Path.LOCAL) {
            val key = apiKey?.trim().orEmpty()
            if (key.isNotEmpty()) s.saveApiKey(providerId, key)
            s.saveModelName(providerId, modelName?.trim()?.takeIf { it.isNotBlank() } ?: p.defaultModel)
        }
        s.savePreference(PREF_BRAIN_PROVIDER, providerId)
        // The rest of the app reads the "selected model" for the brain lane.
        // The provider picker stores its user-facing router label. The chosen
        // service itself is selected inside OmniRoute via default_brain.
        s.savePreference("selected_model", "Omniroute (Local router)")
        s.savePreference("default_brain", providerId)
    }

    /** True once a brain path is actually usable. */
    fun isBrainReady(context: Context): Boolean {
        val s = storage(context)
        val id = s.getPreference(PREF_BRAIN_PROVIDER) ?: return false
        val p = brainProvider(id) ?: return false
        return when (p.path) {
            Path.LOCAL -> s.getEndpoint("local")?.let { path ->
                java.io.File(path).let { it.isFile && it.length() > 0 && com.example.meowmba.core.agent.LocalChatModel.supports(it) } &&
                    (s.getPreference("planner_verified_local") == path ||
                        s.getPreference("chat_verified_local") == localStamp(java.io.File(path)))
            } == true
            else -> !s.getApiKey(id).isNullOrBlank() && PlannerReadiness.cloudStamp(s.getModelName(id), s.getApiKey(id)).let { stamp ->
                s.getPreference("planner_verified_$id") == stamp || s.getPreference("chat_verified_$id") == stamp
            }
        }
    }

    fun localStamp(file: java.io.File): String = "${file.absolutePath}:${file.length()}:${file.lastModified()}"

    // ── TTS (speech out) ───────────────────────────────────────────────────

    fun setTts(context: Context, path: Path, apiKey: String?) {
        val s = storage(context)
        s.savePreference(PREF_TTS_PATH, path.name)
        if (path != Path.LOCAL && !apiKey.isNullOrBlank()) s.saveApiKey("tts", apiKey)
    }

    fun ttsPath(context: Context): Path {
        val v = storage(context).getPreference(PREF_TTS_PATH) ?: return Path.LOCAL
        return Path.values().firstOrNull { it.name == v } ?: Path.LOCAL
    }

    fun isTtsConfigured(context: Context): Boolean {
        val p = ttsPath(context)
        return p == Path.LOCAL || !storage(context).getApiKey("tts").isNullOrBlank()
    }

    // ── voice in (ASR) ─────────────────────────────────────────────────────

    fun setVoice(context: Context, path: Path, apiKey: String?) {
        val s = storage(context)
        s.savePreference(PREF_VOICE_PATH, path.name)
        if (path == Path.PAID_CLOUD && !apiKey.isNullOrBlank()) s.saveApiKey("gemini", apiKey)
        // Map the chosen path to the real ASR backend the pipeline reads.
        val backend = when (path) {
            Path.LOCAL -> "whisper_local"
            Path.PAID_CLOUD -> "gemini_cloud"
            else -> "whisper_local"
        }
        s.savePreference("asr_backend", backend)
    }

    fun voicePath(context: Context): Path {
        val v = storage(context).getPreference(PREF_VOICE_PATH) ?: return Path.LOCAL
        return Path.values().firstOrNull { it.name == v } ?: Path.LOCAL
    }

    fun isVoiceConfigured(context: Context): Boolean {
        val p = voicePath(context)
        return p == Path.LOCAL || !storage(context).getApiKey("gemini").isNullOrBlank()
    }

    // ── onboarding state ───────────────────────────────────────────────────

    fun markOnboardingDone(context: Context) {
        storage(context).saveBooleanPreference(PREF_ONBOARDING_DONE, true)
    }

    fun isOnboardingDone(context: Context): Boolean =
        storage(context).getBooleanPreference(PREF_ONBOARDING_DONE, false)

    fun needsOnboarding(context: Context): Boolean =
        !isOnboardingDone(context)

    // ── storage helper ─────────────────────────────────────────────────────

    private fun storage(context: Context): SecureStorageManager = SecureStorageManager(context)
}
