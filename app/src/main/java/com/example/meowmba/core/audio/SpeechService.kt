package com.example.meowmba.core.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * [SpeechEngine] backed by Android's SpeechRecognizer.
 * Uses the best trusted recognition service available and allows that service to
 * choose its highest-quality local or network-enhanced model.
 *
 * Four hard-won device truths baked in here:
 *
 * 1. Devices without a recognition service are detected up front via
 *    [isRecognitionAvailable] instead of failing every session silently.
 * 2. The recognizer binds to an EXPLICIT component, because the platform default
 *    binding fails every session with ERROR_CLIENT on some devices (Samsung).
 * 3. But NOT the system-configured service first. On this device
 *    Settings.Secure.voice_recognition_service points at `com.google.android.tts`,
 *    which is deaf (always ERROR_NO_MATCH) and holds exclusive media audio focus.
 *    Candidates are ordered by [recognitionServices] and that package is
 *    denylisted outright — see the comment there for the measurements.
 * 4. The mic button, the wake-word loop, and barge-in each overlap sessions
 *    on ONE SpeechRecognizer — and a dying session's late error lands in the
 *    NEW session's callbacks (observed on-device: "stopListening called with
 *    no preceding startListening"; mic tap instantly reset = "button does
 *    nothing"). The wake-word loop therefore gets its OWN recognizer via
 *    [createSessionEngine]; each engine owns exactly one session at a time.
 *    And each [SessionEngine.startListening] destroys the previous recognizer
 *    and binds a fresh instance, so back-to-back sessions never collide with
 *    the Google recognition service's lingering teardown ("capacity is full"
 *    refusals that made both the mic button and always-on listening dead).
 */
class SpeechService(private val context: Context) : SpeechEngine {

    override val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private val interactive = SessionEngine()

    override val isListening get() = interactive.isListening
    override val partialResults get() = interactive.partialResults

    override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) =
        interactive.startListening(onResult, onError)

    override fun stopListening() = interactive.stopListening()

    override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) =
        interactive.swapCallbacks(onResult, onError)

    override fun destroy() = interactive.destroy()

    /**
     * A SEPARATE recognizer instance for the wake-word loop, so its
     * restart-on-complete sessions never share callbacks with interactive
     * (mic button) sessions. Caller owns the returned engine's lifecycle.
     */
    fun createSessionEngine(): SpeechEngine = SessionEngine()

    // ----------------------------------------------------------------------

    private inner class SessionEngine : SpeechEngine {

        private val components: List<ComponentName?> = recognitionServices(context)
        private var componentIndex = 0
        private val component: ComponentName? get() = components[componentIndex]
        private var failuresOnComponent = 0
        private var heardSpeech = false

        private var recognizer: SpeechRecognizer? = null

        private fun buildRecognizer(): SpeechRecognizer? {
            if (!isAvailable) return null
            return runCatching {
                if (component != null) {
                    SpeechRecognizer.createSpeechRecognizer(context, component)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            }.getOrNull()?.also { attachListener(it) }
        }

        private val _isListening = MutableStateFlow(false)
        override val isListening = _isListening.asStateFlow()

        private val _partialResults = MutableStateFlow("")
        override val partialResults = _partialResults.asStateFlow()

        private var onResult: ((String) -> Unit)? = null
        private var onError: ((Int) -> Unit)? = null

        init {
            recognizer = buildRecognizer()
        }

        private fun attachListener(r: SpeechRecognizer) {
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (recognizer !== r) return
                    _isListening.value = true
                    Log.i(TAG, "session ready ($componentName)")
                }
                override fun onBeginningOfSpeech() {
                    if (recognizer !== r) return
                    heardSpeech = true
                    Log.i(TAG, "speech started")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (recognizer !== r) return
                    _isListening.value = false
                    Log.i(TAG, "speech ended")
                }
                override fun onError(error: Int) {
                    // A recognizer we destroyed must not corrupt the live session: AiAi
                    // delivers a result and then a late ERROR_SERVER_DISCONNECTED when the
                    // restart destroys it mid-delivery. If that landed, a follow-up
                    // capture would end on it and the second half of the sentence ("...
                    // on spotify") would be cut off. Stale callbacks are dropped outright.
                    if (recognizer !== r) return
                    _isListening.value = false
                    Log.w(TAG, "session error code=$error ($componentName)")
                    noteFailureAndMaybeFailOver(error)
                    onError?.invoke(error)
                }
                override fun onResults(results: Bundle?) {
                    if (recognizer !== r) return
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val selected = TranscriptSelector.select(
                        finals = matches.orEmpty(),
                        confidences = scores,
                        lastPartial = _partialResults.value,
                    )
                    failuresOnComponent = 0
                    Log.i(TAG, "session result: ${selected.take(60).ifEmpty { "(empty)" }}")
                    if (selected.isNotEmpty()) {
                        onResult?.invoke(selected)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (recognizer !== r) return
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        _partialResults.value = matches[0]
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        private val componentName: String
            get() = component?.flattenToShortString() ?: "platform default"

        override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            // One recognizer instance is bound to ONE AiAi session slot, and a
            // finished session's teardown lingers on the service side — reusing the
            // same instance back-to-back lands the next open in "capacity is full"
            // (ERROR_CLIENT / BUSY) and the whole retry loop churns forever. Destroy
            // the previous instance and bind a fresh one so every session starts from
            // a clean slot. This is what makes both always-on face listening and the
            // mic button reliable against the strict Google recognition service.
            recognizer?.destroy()
            recognizer = null
            _isListening.value = false
            _partialResults.value = ""
            val r = buildRecognizer() ?: run {
                onError(SpeechRecognizer.ERROR_CLIENT)
                return
            }
            recognizer = r
            heardSpeech = false
            this.onResult = onResult
            this.onError = onError
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Modern recognizers can use device context (contacts, installed apps,
                    // prior vocabulary) and punctuation to resolve the names and natural
                    // phrasing that assistants hear. Unsupported services may ignore these.
                    putExtra(RecognizerIntent.EXTRA_ENABLE_BIASING_DEVICE_CONTEXT, true)
                    putExtra(
                        RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                        RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY,
                    )
                    putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
                    putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, false)
                    putStringArrayListExtra(
                        RecognizerIntent.EXTRA_BIASING_STRINGS,
                        arrayListOf(
                            "Memba", "Meowmba", "Meow", "Ollama", "ChatGPT", "Grok",
                            "Spotify", "YouTube", "Gmail",
                        ),
                    )
                }

                // Capture window. Android's defaults end a session after a beat of
                // quiet, which cuts people off mid-sentence and makes the companion
                // feel like a walkie-talkie instead of a call. These ask for a much
                // longer tolerance for pauses.
                //
                // Honoured as a HINT only — several recognizers ignore them outright
                // (they are documented as not guaranteed). Setting them costs nothing
                // and helps on the ones that do listen.
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    COMPLETE_SILENCE_MS,
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    POSSIBLY_COMPLETE_SILENCE_MS,
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    MINIMUM_SESSION_MS,
                )
                // Do not set EXTRA_PREFER_OFFLINE here. Android defines it as an
                // offline-only restriction, and forcing the small offline language pack
                // caused severe word loss on the target Fold. The selected recognition
                // service may still run locally; it is simply allowed to use its best
                // available model when network-enhanced recognition exists.
            }
            r.startListening(intent)
        }

        override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            this.onResult = onResult
            this.onError = onError
        }

        override fun stopListening() {
            recognizer?.stopListening()
            _isListening.value = false
        }

        override fun destroy() {
            recognizer?.destroy()
            recognizer = null
            _isListening.value = false
        }

        /**
         * Fail over only between curated ASR services. The removed implementation
         * rotated into arbitrary assistant integrations (Home Assistant/Claude) and
         * made recognition worse; this bounded list can never do that.
         */
        private fun noteFailureAndMaybeFailOver(error: Int) {
            if (componentIndex >= components.lastIndex) return
            val heardButNoTranscript = heardSpeech && error == SpeechRecognizer.ERROR_NO_MATCH
            val infrastructureFailure = error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                error == SpeechRecognizer.ERROR_SERVER ||
                error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
            if (!heardButNoTranscript && !infrastructureFailure) return

            failuresOnComponent++
            val threshold = if (heardButNoTranscript) 3 else 2
            if (failuresOnComponent < threshold) return
            val previous = componentName
            componentIndex++
            failuresOnComponent = 0
            Log.w(TAG, "recognition failover: $previous -> $componentName")
        }
    }

    // ----------------------------------------------------------------------

    internal companion object {
        private const val TAG = "MeowmbaVoice"

        /**
         * Capture-window hints. ~2.5s of silence before a sentence is considered
         * finished, and sessions run at least 15s so a slow speaker is not cut off.
         */
        private const val COMPLETE_SILENCE_MS = 2_500L
        private const val POSSIBLY_COMPLETE_SILENCE_MS = 2_000L
        private const val MINIMUM_SESSION_MS = 15_000L

        /** Android System Intelligence — the real modern on-device recognizer. */
        private const val AIAI_PACKAGE = "com.google.android.as"

        /** Google app's recognizer. */
        private const val GOOGLE_RECOGNITION_PACKAGE = "com.google.android.googlequicksearchbox"

        /**
         * Packages whose RecognitionService must never be bound.
         *
         * `com.google.android.tts` is a text-to-SPEECH package that also publishes a
         * RecognitionService, and on this device it is what
         * Settings.Secure.voice_recognition_service points at. Measured behaviour
         * when bound (Z Fold 7 / Android 16):
         *
         *  - it hears speech (onBeginningOfSpeech fires, 7s of audio) and then
         *    returns ERROR_NO_MATCH every single time — never a transcript, so the
         *    wake phrase can never match; and
         *  - it takes AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE with USAGE_MEDIA for every
         *    session. Back-to-back sessions therefore pause the user's music and
         *    never let it resume, even with the app in the background.
         *
         * Both reported bugs — "wake phrase stopped" and "all my media pauses" —
         * were this one service. Preferring the system-configured service walked
         * straight into it.
         */
        private val DENYLISTED_PACKAGES = setOf("com.google.android.tts")

        /**
         * Recognition services to try, best first. Returns more than one so a
         * device whose preferred service turns out to be deaf can fail over
         * (see [SessionEngine] suspect detection) instead of looping forever.
         *
         * Order: Google app, then Android System Intelligence, then a safe installed
         * system choice. The system setting is demoted rather than trusted first—it is
         * exactly what pointed at the deaf TTS service here. Arbitrary assistants are
         * never included.
         */
        fun recognitionServices(context: Context): List<ComponentName?> {
            val installed = runCatching {
                context.packageManager.queryIntentServices(
                    Intent(RecognitionService.SERVICE_INTERFACE), 0
                )
            }.getOrDefault(emptyList())
                .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
                .filter { it.packageName !in DENYLISTED_PACKAGES }

            val trusted = buildList<ComponentName?> {
                // Quality-first conversation mode: Google can use its current
                // network-enhanced model. AiAi is the trusted local fallback.
                installed.firstOrNull { it.packageName == GOOGLE_RECOGNITION_PACKAGE }
                    ?.let(::add)
                installed.firstOrNull { it.packageName == AIAI_PACKAGE }
                    ?.takeIf { it !in this }
                    ?.let(::add)

                // Then the system's own choice, if it is installed, safe, and not
                // already present. This commonly points at one of the two above.
                runCatching {
                    Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
                }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ComponentName.unflattenFromString(it) }
                    ?.takeIf { it.packageName !in DENYLISTED_PACKAGES && it in installed }
                    ?.takeIf { it !in this }
                    ?.let(::add)
            }

            // Deliberately NOT falling through to arbitrary third-party assist services.
            // Devices here expose Home Assistant's and Claude's RecognitionServices;
            // both are assistant integrations, not general dictation back-ends, and both
            // error immediately (5/9) when driven as one. Returning null lets the
            // platform pick instead of us binding something known-wrong only when no
            // curated/general recognizer was discoverable.
            return trusted.ifEmpty { listOf(null) }
        }
    }
}
