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
 * Three hard-won device truths baked in here:
 *
 * 1. Devices without a recognition service are detected up front via
 *    [isRecognitionAvailable] instead of failing every session silently.
 * 2. The recognizer binds to an EXPLICIT component: the system's configured
 *    voice-recognition service first (proven to work on this device), then
 *    Google's package, then anything installed. The platform default binding
 *    picks a service that fails every session with ERROR_CLIENT on some
 *    devices (Samsung).
 * 3. The mic button, the wake-word loop, and barge-in each overlap sessions
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
        private val recognizer: SpeechRecognizer? = run {
            if (!isAvailable) return@run null
            val component = resolveRecognitionService(context)
            runCatching {
                if (component != null) {
                    SpeechRecognizer.createSpeechRecognizer(context, component)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            }.getOrNull()
        }

        private val _isListening = MutableStateFlow(false)
        override val isListening = _isListening.asStateFlow()

        private val _partialResults = MutableStateFlow("")
        override val partialResults = _partialResults.asStateFlow()

        private var onResult: ((String) -> Unit)? = null
        private var onError: ((Int) -> Unit)? = null

        init {
            recognizer?.setRecognitionListener(object : RecognitionListener {
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
                    onError?.invoke(error)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    Log.i(TAG, "session result: ${matches?.firstOrNull()?.take(60) ?: "(empty)"}")
                    if (!matches.isNullOrEmpty()) {
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
            get() = resolveRecognitionService(context)?.flattenToShortString() ?: "default"

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

    private companion object {
        private const val TAG = "MeowmbaVoice"
        /** Google's on-device recognition package — fallback preference. */
        private const val GOOGLE_RECOGNITION_PACKAGE = "com.google.android.googlequicksearchbox"

        /**
         * Pick the recognition service to bind:
         *  1. the service the SYSTEM is configured to use (proven on-device)
         *  2. Google's package when installed
         *  3. anything installed
         */
        fun resolveRecognitionService(context: Context): ComponentName? {
            runCatching {
                Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { flat ->
                ComponentName.unflattenFromString(flat)?.let { return it }
            }
            val services = context.packageManager.queryIntentServices(
                Intent(RecognitionService.SERVICE_INTERFACE), 0
            )
            val chosen = services.firstOrNull {
                it.serviceInfo.packageName == GOOGLE_RECOGNITION_PACKAGE
            } ?: services.firstOrNull() ?: return null
            return ComponentName(chosen.serviceInfo.packageName, chosen.serviceInfo.name)
        }
    }
}
