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
     * "yomeow" spoken as one word leads, since that is the phrase in use; the spaced and
     * casual variants follow so the companion answers however it is addressed.
     *
     * Bare "yo" / "oi" / "dude" are deliberately absent. The old transcript matcher could
     * afford single syllables because it scanned finished text; a spotter running
     * continuously on raw audio would false-fire on them constantly.
     */
    private val BASE_PHRASES = listOf(
        // Said as one word, said as two, and every carrier the user actually uses.
        "yomeow", "yo meow", "hey meow", "ok meow", "okay meow",
        // Homophones of the same sound. "MEOW" is rare in the model's training data and
        // decodes unreliably; registering the spellings it is likely to emit instead is
        // what makes a fluid "yomeow" land without the deliberate "yo — meow" pause.
        // See KwsKeywords.EXACT.
        "yomao", "yomeo", "yo mao", "yo meo", "yo mow", "yo mew", "yo miao",
        "hey mao", "hey meo", "hey mew", "ok mao", "ok mew", "okay mao",
        // Run-together and drawn-out spellings of the hey/ok carriers. Measured: the
        // plain "▁HE Y ▁ME OW" path does not fire on this model, these are the paths
        // that do.
        "heymeow", "haymeow", "hay meow", "hi meow", "hey meaow", "hey meeow",
        "hey miao", "hey me ow", "okmeow", "okey meow", "ok me ow",
        // H-less decodes of "hey meow" — the H is barely voiced in casual speech, so
        // the spotter must also listen for what the model actually emits. See KwsKeywords.
        "ey meow", "eh meow", "eymeow",
        // 2026-08-05 wave ("only yo meow works"): more decodes of the hey/ok carriers.
        "hey miaw", "hey miow", "hey miou", "hey meao", "hey meou", "hey meu",
        "hey mia", "hey meuw", "hey maow", "hey maw", "he meow", "ay meow",
        "yo miaw", "yo miow", "yo meao", "ok miaw",
        // Older phrases, kept.
        "hey kitty", "yo memba", "hey memba", "oi meow",
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
