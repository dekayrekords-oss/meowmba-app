package com.example.meowmba.core.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SpeechEngine] backed by Android's on-device SpeechRecognizer.
 * Prefers offline recognition when the device supports it.
 *
 * Devices without a recognition service (no Google app / voice typing
 * provider) are detected up front via [isRecognitionAvailable] instead of
 * failing every session with a silent ERROR_CLIENT.
 *
 * The recognizer binds to an EXPLICIT service component (Google's service
 * when present, else the first installed): the platform default binding
 * picks the system's default voice-input service, which on some devices
 * (Samsung's intellivoiceservice) fails every session with ERROR_CLIENT
 * even though recognition is technically "available".
 */
class SpeechService(context: Context) : SpeechEngine {
    override val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private val speechRecognizer: SpeechRecognizer? = run {
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
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _isListening.value = false
            }
            override fun onError(error: Int) {
                _isListening.value = false
                onError?.invoke(error)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
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

    override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        val recognizer = speechRecognizer ?: run {
            // No recognition service on this device — report honestly.
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
        recognizer.startListening(intent)
    }

    override fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
    }

    override fun destroy() {
        speechRecognizer?.destroy()
    }

    private companion object {
        /** Google's on-device recognition service — the reliable one on most phones. */
        private const val GOOGLE_RECOGNITION_PACKAGE = "com.google.android.googlequicksearchbox"

        /** Pick the recognition service to bind: Google's when installed, else any installed one. */
        fun resolveRecognitionService(context: Context): ComponentName? {
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
