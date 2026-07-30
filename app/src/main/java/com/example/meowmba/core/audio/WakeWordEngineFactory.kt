package com.example.meowmba.core.audio

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * Chooses the wake-word implementation.
 *
 * Prefers the sherpa-onnx keyword spotter, which is what a wake word should be: one
 * continuous AudioRecord stream through a 5 MB on-device model. Falls back to the old
 * SpeechRecognizer restart loop when the model assets are absent — a build without them
 * still works, just with the session churn, deaf gaps and media-focus grabs that
 * [SherpaKwsEngine]'s KDoc documents.
 *
 * Deliberately free of android.util.Log: this runs during ViewModel construction, and in
 * JVM unit tests Log is not mocked, so a single log call here fails every test that
 * builds a ViewModel.
 */
object WakeWordEngineFactory {

    /**
     * Kill switch for the VAD + speaker-verification stages.
     *
     * Kept because it earned its place: adding those stages made the mic deliver digital
     * silence a few seconds after start, and flipping this to false is how the cause was
     * isolated to the VAD (wrong window size) rather than guessed at. If wake detection
     * ever regresses again, set false and re-measure before theorising.
     */
    private const val GATES_ENABLED = true

    /**
     * Wake phrases offered regardless of the companion's name.
     *
     * Mirrors the casual variants WakePhraseMatcher accepted, because those are what
     * people actually say. Bare "yo"/"oi"/"dude" are deliberately NOT here — one syllable
     * would false-fire constantly, whereas the matcher could afford them because it
     * worked on a full transcript.
     */
    private val BASE_PHRASES = listOf(
        "yo meow", "hey meow", "hey kitty",
        "yo memba", "hey memba", "oi meow",
    )

    data class Result(
        val engine: WakeWordEngine,
        /** True when the real keyword spotter is in use. */
        val isKws: Boolean,
        /** Set when a fatal-error callback should be attached. */
        val kws: SherpaKwsEngine? = null,
        /** Present when speaker verification is available (model bundled). */
        val verifier: SpeakerVerifier? = null,
    )

    fun create(
        context: Context?,
        scope: CoroutineScope,
        companionName: String,
        matcher: WakePhraseMatcher,
        speechEngine: SpeechEngine?,
    ): Result? {
        if (context != null) buildKws(context, scope, companionName)?.let { (kws, verifier) ->
            return Result(engine = kws, isKws = true, kws = kws, verifier = verifier)
        }
        if (speechEngine == null) return null
        return Result(
            engine = RecognizerWakeWordEngine(speechEngine, matcher, scope),
            isKws = false,
        )
    }

    private fun buildKws(
        context: Context,
        scope: CoroutineScope,
        companionName: String,
    ): Pair<SherpaKwsEngine, SpeakerVerifier?>? {
        // All model files must be present. A partial bundle is worse than none: the
        // native loader aborts the process instead of failing gracefully.
        if (!SherpaKwsEngine.assetsPresent(context)) return null

        val vocab = readVocab(context) ?: return null

        val phrases = BASE_PHRASES + companionName.trim()
            .takeIf { it.isNotBlank() }
            ?.let { listOf("hey $it") }
            .orEmpty()

        val keywords = KwsKeywords.build(phrases, vocab)
        if (keywords.isBlank()) {
            return null
        }
        // Both gates are optional and fail open: a missing model just means the stage is
        // skipped, never that the wake word stops working.
        val verifier = SpeakerVerifier(context, VoiceProfileStore(context))
            .takeIf { it.isModelAvailable() }
            ?.also { it.reload() }

        val engine = SherpaKwsEngine(
            context = context,
            scope = scope,
            keywords = keywords,
            verifier = if (GATES_ENABLED) verifier else null,
            speechGate = if (GATES_ENABLED) VadGate.create(context) else null,
        )
        return engine to verifier
    }

    /** Reads tokens.txt from assets; null means the model is not bundled. */
    private fun readVocab(context: Context): Set<String>? = try {
        context.assets.open("${SherpaKwsEngine.DEFAULT_MODEL_DIR}/tokens.txt")
            .bufferedReader()
            .useLines { KwsKeywords.parseVocab(it) }
            .takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        // No assets (or a test double with no AssetManager): fall back quietly.
        null
    }
}
