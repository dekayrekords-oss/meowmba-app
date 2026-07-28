package com.example.meowmba.core.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

/**
 * [SpeechSynthesizer] backed by Android's Text-to-Speech engine.
 */
class TextToSpeechManager(context: Context) : SpeechSynthesizer {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    override var onSpeechStarted: (() -> Unit)? = null
    override var onSpeechFinished: (() -> Unit)? = null
    override var onWordBoundary: ((start: Int, end: Int) -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
                setupListener()
            }
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeechStarted?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                onSpeechFinished?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeechFinished?.invoke()
            }

            // API 26+: word-boundary events drive the avatar's mouth rhythm.
            // On older devices this simply never fires and the renderer
            // falls back to its base talk cycle.
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                onWordBoundary?.invoke(start, end)
            }
        })
    }

    override fun speak(text: String) {
        if (isInitialized) {
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "memba_utterance")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "memba_utterance")
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.shutdown()
    }
}
