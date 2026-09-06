package com.example.meowmba.core.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

/**
 * [SpeechSynthesizer] backed by Android's Text-to-Speech engine.
 */
class TextToSpeechManager(private val context: Context) : SpeechSynthesizer {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val neural by lazy { LocalNeuralSpeech(context.applicationContext,
        started = { onSpeechStarted?.invoke() }, finished = { onSpeechFinished?.invoke() },
        failed = { LocalVoiceRuntime.lastError.value = it }) }
    private fun useNeural() = LocalVoices.enabled(context) && LocalVoicePack(context).ready()

    override var onSpeechStarted: (() -> Unit)? = null
    override var onSpeechFinished: (() -> Unit)? = null
    override var onWordBoundary: ((start: Int, end: Int) -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Never send speech to a network-backed Android voice.
                val offline = tts?.voices?.filter { !it.isNetworkConnectionRequired && it.locale.language == "en" }
                    ?.sortedByDescending { it.quality }?.firstOrNull()
                if (offline != null) tts?.voice = offline
                isInitialized = offline != null
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
    /** Phase G1: mood modulates voice slightly (1.0 = neutral). */
    fun setPitch(pitch: Float) { tts?.setPitch(pitch.coerceIn(0.5f, 2.0f)) }

    fun setSpeechRate(rate: Float) { tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f)) }

    private var pendingUtterances = 0
    private val activeIds = mutableSetOf<String>()
    private var speakingStarted = false

    /**
     * True while any utterance — or a queued tail of streamed sentences — is still
     * being spoken. The always-on mic gates on this: opening a capture session in
     * a gap between streamed sentences makes the recognizer hear HER OWN VOICE and
     * feed it back as a user turn (measured on device as self-conversation loops).
     *
     * Staleness escape: some engines silently drop an utterance without ever
     * calling onDone/onError, which would leave [pendingUtterances] stuck above
     * zero and gate the mic shut forever. After 20s with zero progress the
     * queue is declared finished regardless.
     */
    private var oldestPendingAtMs = 0L

    fun isSpeaking(): Boolean = synchronized(this) {
        if (neural.isSpeaking()) return true
        if (pendingUtterances <= 0) return false
        tts?.isSpeaking == true || System.currentTimeMillis() - oldestPendingAtMs < 20_000L
    }

    /**
     * UtteranceProgressListener callbacks arrive on a TTS binder thread, but the
     * ViewModel uses them to start/stop a SpeechRecognizer, which Android only
     * allows on the application's main thread (off-thread calls log
     * “SpeechRecognizer should be used only from the application's main thread”
     * and then collide with the follow-up session, surfacing as error 5/7 and a
     * dead follow-up). Deliver every listener callback on the main thread.
     */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private fun onMain(block: () -> Unit) { mainHandler.post(block) }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                synchronized(this@TextToSpeechManager) {
                    if (utteranceId !in activeIds || speakingStarted) return
                    speakingStarted = true
                }
                onMain { synchronized(this@TextToSpeechManager) {
                    if (utteranceId in activeIds) onSpeechStarted?.invoke()
                } }
            }

            override fun onDone(utteranceId: String?) {
                finishOne(utteranceId)
            }

            override fun onError(utteranceId: String?) {
                finishOne(utteranceId)
            }

            // API 30+: engines may report failures through this overload ONLY —
            // missing it leaked pendingUtterances and gated the mic shut.
            override fun onError(utteranceId: String?, status: Int) {
                finishOne(utteranceId)
            }

            private fun finishOne(id: String?) {
                synchronized(this@TextToSpeechManager) {
                    if (!activeIds.remove(id)) return
                    pendingUtterances = activeIds.size
                    if (pendingUtterances > 0) return
                    speakingStarted = false
                }
                onMain { synchronized(this@TextToSpeechManager) {
                    if (pendingUtterances == 0) onSpeechFinished?.invoke()
                } }
            }

            // API 26+: word-boundary events drive the avatar's mouth rhythm.
            // On older devices this simply never fires and the renderer
            // falls back to its base talk cycle.
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                synchronized(this@TextToSpeechManager) { if (utteranceId !in activeIds) return }
                onMain { onWordBoundary?.invoke(start, end) }
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

    fun previewLocalVoice(id: String) {
        stop()
        val voice = LocalVoices.find(id)
        neural.enqueue("Hi, I'm ${voice.name}. What would you like to do next?", true, voice)
    }

    private fun enqueue(text: String, flush: Boolean) {
        if (useNeural()) {
            if (flush) { synchronized(this) { pendingUtterances = 0; activeIds.clear(); speakingStarted = false }; tts?.stop() }
            LocalVoiceRuntime.lastError.value = null
            neural.enqueue(text, flush)
            return
        }
        if (!isInitialized || text.isBlank()) return
        val id = "memba_utterance_${utteranceSeq++}"
        synchronized(this) {
            if (flush || pendingUtterances == 0) oldestPendingAtMs = System.currentTimeMillis()
            if (flush) { activeIds.clear(); speakingStarted = false }
            activeIds.add(id)
            pendingUtterances = activeIds.size
        }
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (tts?.speak(text, mode, params, id) == TextToSpeech.ERROR) {
            synchronized(this) { activeIds.remove(id); pendingUtterances = activeIds.size }
            if (pendingUtterances == 0) onMain { onSpeechFinished?.invoke() }
        }
    }

    private var utteranceSeq = 0L

    override fun stop() {
        synchronized(this) { pendingUtterances = 0; activeIds.clear(); speakingStarted = false }
        neural.stop()
        tts?.stop()
    }

    override fun shutdown() {
        neural.shutdown()
        tts?.shutdown()
    }
}
