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

        private val candidates = recognitionCandidates(context)

        /** Index into [candidates]; advances when a service errors out repeatedly. */
        private var candidateIndex = 0

        /** Consecutive errors that mean this service cannot serve us at all. */
        private var serviceErrors = 0

        private var recognizer: SpeechRecognizer? = null

        private fun currentComponent(): ComponentName? = candidates.getOrNull(candidateIndex)

        private fun buildRecognizer(): SpeechRecognizer? {
            if (!isAvailable) return null
            val component = currentComponent()
            return runCatching {
                if (component != null) {
                    SpeechRecognizer.createSpeechRecognizer(context, component)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            }.getOrNull()?.also { attachListener(it) }
        }

        /**
         * Moves to the next candidate service and rebuilds. Returns false when there
         * is nothing left to try.
         */
        private fun failOver(): Boolean {
            if (candidateIndex + 1 >= candidates.size) {
                Log.w(TAG, "no recognizer candidates left after ${currentComponent()}")
                return false
            }
            val from = currentComponent()
            candidateIndex++
            serviceErrors = 0
            runCatching { recognizer?.destroy() }
            recognizer = buildRecognizer()
            Log.w(TAG, "recognizer failover: $from -> ${currentComponent()}")
            return recognizer != null
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
                    _isListening.value = true
                    Log.i(TAG, "session ready ($componentName)")
                }
                override fun onBeginningOfSpeech() {
                    Log.i(TAG, "speech started")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isListening.value = false
                    Log.i(TAG, "speech ended")
                }
                override fun onError(error: Int) {
                    _isListening.value = false
                    Log.w(TAG, "session error code=$error ($componentName)")

                    // Fail over only when the SERVICE cannot serve us. NO_MATCH is not
                    // such a signal even when speech was detected: ambient noise
                    // legitimately produces speech-started followed by NO_MATCH, and
                    // treating that as "deaf" made a working recognizer hand off to a
                    // broken one.
                    if (error in FAILOVER_ERRORS) {
                        if (++serviceErrors >= ERRORS_BEFORE_FAILOVER && failOver()) {
                            Log.w(TAG, "switched recognizer after $ERRORS_BEFORE_FAILOVER service errors")
                        }
                    }
                    onError?.invoke(error)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    Log.i(TAG, "session result: ${matches?.firstOrNull()?.take(60) ?: "(empty)"}")
                    if (!matches.isNullOrEmpty()) {
                        serviceErrors = 0   // this service works; keep it
                        onResult?.invoke(matches[0])
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        _partialResults.value = matches[0]
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        private val componentName: String
            get() = currentComponent()?.flattenToShortString() ?: "default"

        override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            val r = recognizer ?: run {
                onError(SpeechRecognizer.ERROR_CLIENT)
                return
            }
            this.onResult = onResult
            this.onError = onError
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Keep recognition on the device whenever possible (privacy-first)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            _partialResults.value = ""
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
        }
    }

    // ----------------------------------------------------------------------

    internal companion object {
        private const val TAG = "MeowmbaVoice"

        /** Service-level errors tolerated before switching recognition service. */
        private const val ERRORS_BEFORE_FAILOVER = 3

        /**
         * Errors that mean the bound SERVICE is unusable, so another candidate is
         * worth trying. Deliberately excludes NO_MATCH and SPEECH_TIMEOUT: those are
         * normal for a wake loop in a quiet or noisy room and say nothing about the
         * service.
         */
        private val FAILOVER_ERRORS = setOf(
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_SERVER,
        )

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
        fun recognitionCandidates(context: Context): List<ComponentName> {
            val services = runCatching {
                context.packageManager.queryIntentServices(
                    Intent(RecognitionService.SERVICE_INTERFACE), 0
                )
            }.getOrDefault(emptyList())

            val installed = services
                .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
                .filter { it.packageName !in DENYLISTED_PACKAGES }

            val configured = runCatching {
                Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
            }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { ComponentName.unflattenFromString(it) }
                ?.takeIf { it.packageName !in DENYLISTED_PACKAGES }

            val ordered = LinkedHashSet<ComponentName>()
            installed.firstOrNull { it.packageName == AIAI_PACKAGE }?.let(ordered::add)
            installed.firstOrNull { it.packageName == GOOGLE_RECOGNITION_PACKAGE }
                ?.let(ordered::add)
            configured?.let(ordered::add)
            ordered.addAll(installed)
            return ordered.toList()
        }
    }
}
