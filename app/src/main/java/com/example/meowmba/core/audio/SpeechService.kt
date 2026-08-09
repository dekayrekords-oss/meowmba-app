package com.example.meowmba.core.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SpeechEngine] backed by Android's on-device SpeechRecognizer.
 * Prefers offline recognition when the device supports it.
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
 *    Candidates are ordered by [recognitionCandidates] and that package is
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

        private val component: ComponentName? = recognitionService(context)

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

                    // No automatic service switching. Rotating on error walked off the
                    // correct recognizer (AiAi) after a few transient CLIENT errors,
                    // down through Home Assistant and Claude, to "no candidates left"
                    // and a false "keeps failing on this device". Picking the right
                    // service ONCE is what actually fixed recognition; the rotation
                    // only ever made it worse. The engine above decides what an error
                    // means.
                    onError?.invoke(error)
                }
                override fun onResults(results: Bundle?) {
                    if (recognizer !== r) return
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    Log.i(TAG, "session result: ${matches?.firstOrNull()?.take(60) ?: "(empty)"}")
                    if (!matches.isNullOrEmpty()) {
                        onResult?.invoke(matches[0])
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
            this.onResult = onResult
            this.onError = onError
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

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
                // Keep recognition on the device whenever possible (privacy-first)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
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
         * Order: Android System Intelligence, then the Google app, then whatever the
         * system is configured to use, then anything else. The system setting is
         * demoted rather than trusted first — it is exactly what pointed at the TTS
         * service here.
         */
        fun recognitionService(context: Context): ComponentName? {
            val installed = runCatching {
                context.packageManager.queryIntentServices(
                    Intent(RecognitionService.SERVICE_INTERFACE), 0
                )
            }.getOrDefault(emptyList())
                .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
                .filter { it.packageName !in DENYLISTED_PACKAGES }

            // Prefer a general-purpose ASR provider.
            installed.firstOrNull { it.packageName == AIAI_PACKAGE }?.let { return it }
            installed.firstOrNull { it.packageName == GOOGLE_RECOGNITION_PACKAGE }
                ?.let { return it }

            // Then the system's own choice, if it is not the denylisted TTS one.
            runCatching {
                Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
            }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { ComponentName.unflattenFromString(it) }
                ?.takeIf { it.packageName !in DENYLISTED_PACKAGES }
                ?.let { return it }

            // Deliberately NOT falling through to arbitrary third-party assist services.
            // Devices here expose Home Assistant's and Claude's RecognitionServices;
            // both are assistant integrations, not general dictation back-ends, and both
            // error immediately (5/9) when driven as one. Returning null lets the
            // platform pick instead of us binding something known-wrong.
            return null
        }
    }
}
