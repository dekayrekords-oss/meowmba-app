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

    /**
     * Utterances handed to the engine but not finished yet.
     *
     * Streamed replies are spoken sentence by sentence, so the engine holds several
     * queued utterances at once. [onSpeechFinished] must fire only when the LAST one
     * drains — firing per sentence would tell the companion it had stopped talking
     * mid-answer, which resumes the wake loop and re-arms barge-in against its own
     * remaining speech.
     */
    private var pendingUtterances = 0

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Only the first utterance of a reply is a transition into speaking.
                if (pendingUtterances <= 1) onSpeechStarted?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                finishOne()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finishOne()
            }

            private fun finishOne() {
                synchronized(this@TextToSpeechManager) {
                    pendingUtterances = (pendingUtterances - 1).coerceAtLeast(0)
                    if (pendingUtterances > 0) return
                }
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

    override fun speak(text: String) = enqueue(text, flush = true)

    /**
     * Appends [text] to what is already being said instead of cutting it off.
     *
     * This is what makes a streamed reply sound like a live call: the first sentence
     * starts as soon as the model has produced it, and later sentences are appended
     * while generation is still running, rather than the user waiting in silence for
     * the whole answer before hearing a word.
     */
    override fun speakMore(text: String) = enqueue(text, flush = false)

    private fun enqueue(text: String, flush: Boolean) {
        if (!isInitialized || text.isBlank()) return
        synchronized(this) {
            if (flush) pendingUtterances = 0
            pendingUtterances++
        }
        val id = "memba_utterance_${utteranceSeq++}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, params, id)
    }

    private var utteranceSeq = 0L

    override fun stop() {
        synchronized(this) { pendingUtterances = 0 }
        tts?.stop()
    }

    override fun shutdown() {
        tts?.shutdown()
    }
}
