package com.example.meowmba.core.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
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
 */
class SpeechService(context: Context) : SpeechEngine {
    override val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private val speechRecognizer: SpeechRecognizer? =
        if (isAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null

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
}
