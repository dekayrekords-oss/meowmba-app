package com.example.meowmba.core.audio

import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One spoken *turn*, stitched from however many recognizer sessions it takes.
 *
 * Android's recognizer decides on its own when you have finished talking, and it decides
 * after roughly a second of quiet. Think mid-sentence — "sell my… uh… the Fender" — and it
 * ends the session, hands back half a sentence, and the companion answers the wrong
 * question. The `EXTRA_SPEECH_INPUT_*_SILENCE_LENGTH_MILLIS` hints exist for exactly this
 * and are documented as *not guaranteed*; on this device they are ignored, which is why
 * setting them was not enough.
 *
 * So endpointing is done here instead, over the top of the platform's:
 *
 *  - a session ending is treated as a **pause**, not as the end of the turn: the text is
 *    banked and a new session starts immediately;
 *  - the turn ends only after [silenceToFinishMs] with nothing new heard — measured from
 *    the last partial or result, not from when a session happened to close;
 *  - "nothing heard" errors (NO_MATCH, SPEECH_TIMEOUT) are the *normal* way a pause
 *    reports itself, so they restart rather than fail, as long as the pause is still
 *    short and the turn has not run past [maxUtteranceMs].
 *
 * Real errors (permission, busy, dead recognizer) pass straight through — a broken mic
 * must still look broken.
 */
class UtteranceCapture(
    private val engine: SpeechEngine,
    private val scope: CoroutineScope,
    /** Quiet needed before the turn is considered over. */
    private val silenceToFinishMs: Long = SILENCE_TO_FINISH_MS,
    /** Hard cap, so a stuck recognizer cannot listen forever. */
    private val maxUtteranceMs: Long = MAX_UTTERANCE_MS,
    /** How long to wait for the user to start at all before giving up. */
    private val initialSilenceMs: Long = INITIAL_SILENCE_MS,
    /**
     * Ends the turn immediately when the text so far is unambiguously complete.
     *
     * Waiting out the pause is right for a sentence still being formed and wrong for
     * "stop" — being told to stop and then carrying on listening for another two and a
     * half seconds is the one case where patience reads as not obeying.
     */
    private val finishEarlyWhen: (String) -> Boolean = { false },
    /** Injectable clock; tests drive time instead of sleeping through it. */
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    private val segments = mutableListOf<String>()
    private val _live = MutableStateFlow("")

    /** Everything heard so far this turn, including the in-progress partial. */
    val live: StateFlow<String> = _live.asStateFlow()

    private var onFinal: ((String) -> Unit)? = null
    private var onError: ((Int) -> Unit)? = null

    private var startedAt = 0L
    private var lastVoiceAt = 0L
    private var restarts = 0
    private var finished = false

    private var watchdog: Job? = null
    private var partialJob: Job? = null

    /** True while a turn is being captured. */
    val isCapturing: Boolean get() = !finished && startedAt != 0L

    fun start(onFinal: (String) -> Unit, onError: (Int) -> Unit) {
        this.onFinal = onFinal
        this.onError = onError
        segments.clear()
        _live.value = ""
        startedAt = now()
        lastVoiceAt = startedAt
        restarts = 0
        finished = false

        // Partials are the only evidence that someone is still talking *during* a
        // session. Without them the pause timer would only ever update once a session
        // closed, which is far too coarse to tell a breath from an ending.
        partialJob?.cancel()
        partialJob = scope.launch {
            engine.partialResults.collect { partial ->
                if (finished || partial.isBlank()) return@collect
                lastVoiceAt = now()
                _live.value = (segments + partial).joinToString(" ")
            }
        }

        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive && !finished) {
                delay(TICK_MS)
                val quiet = now() - lastVoiceAt
                val elapsed = now() - startedAt
                when {
                    // Said something, then stopped for long enough: that is the turn.
                    segments.isNotEmpty() && quiet >= silenceToFinishMs -> finish()
                    // Never said anything at all.
                    segments.isEmpty() && quiet >= initialSilenceMs ->
                        fail(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    elapsed >= maxUtteranceMs ->
                        if (segments.isEmpty()) fail(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        else finish()
                }
            }
        }

        listen()
    }

    /** Abandons the turn without delivering anything. */
    fun cancel() {
        if (finished) return
        finished = true
        watchdog?.cancel()
        partialJob?.cancel()
        engine.stopListening()
    }

    private fun listen() {
        engine.startListening(
            onResult = { text -> onSessionResult(text) },
            onError = { code -> onSessionError(code) },
        )
    }

    private fun onSessionResult(text: String) {
        if (finished) return
        val trimmed = text.trim()
        if (trimmed.isNotEmpty() && trimmed != segments.lastOrNull()) {
            segments += trimmed
            lastVoiceAt = now()
            _live.value = segments.joinToString(" ")
        }
        // Checked on completed sessions only. A partial can say "stop" on its way to
        // "stop by the store", and acting on that would cut the user off — which is the
        // whole problem this class exists to fix.
        if (segments.isNotEmpty() && finishEarlyWhen(segments.joinToString(" "))) {
            finish()
            return
        }
        // A result is the recognizer's opinion that the turn ended. It is only a pause
        // until the quiet has actually lasted long enough, so keep the mic open.
        if (now() - lastVoiceAt >= silenceToFinishMs) finish() else restart()
    }

    private fun onSessionError(code: Int) {
        if (finished) return
        val heardNothing = code == SpeechRecognizer.ERROR_NO_MATCH ||
            code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        if (!heardNothing) {
            // A genuine failure. But the user may already have said something this turn
            // before it hit: AiAi delivers a result and then a late
            // ERROR_SERVER_DISCONNECTED (the restart's destroy racing the service's
            // final callbacks), and failing there throws a complete utterance away.
            // Deliver what was heard — never discard real words for a transport hiccup.
            if (segments.isNotEmpty()) {
                finish()
                return
            }
            fail(code)
            return
        }
        val quiet = now() - lastVoiceAt
        when {
            segments.isNotEmpty() && quiet >= silenceToFinishMs -> finish()
            segments.isEmpty() && quiet >= initialSilenceMs -> fail(code)
            else -> restart()
        }
    }

    private fun restart() {
        if (finished) return
        if (++restarts > MAX_RESTARTS) {
            if (segments.isEmpty()) fail(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) else finish()
            return
        }
        // Straight back in: every millisecond here is a millisecond of deafness, and words
        // spoken in the gap are gone for good.
        listen()
    }

    private fun finish() {
        if (finished) return
        finished = true
        watchdog?.cancel()
        partialJob?.cancel()
        engine.stopListening()
        val text = segments.joinToString(" ").trim()
        if (text.isEmpty()) {
            onError?.invoke(SpeechRecognizer.ERROR_NO_MATCH)
        } else {
            onFinal?.invoke(text)
        }
    }

    private fun fail(code: Int) {
        if (finished) return
        finished = true
        watchdog?.cancel()
        partialJob?.cancel()
        engine.stopListening()
        onError?.invoke(code)
    }

    companion object {
        /**
         * Long enough to think mid-sentence, short enough that finishing a sentence does
         * not feel like waiting for a reply. Two and a half seconds is roughly the pause
         * people take before "…um", and well past the ~1s the platform ends on.
         */
        const val SILENCE_TO_FINISH_MS = 2_500L

        /** Nobody speaks one turn for this long; a stuck session must not hold the mic. */
        const val MAX_UTTERANCE_MS = 45_000L

        /** Tapping the mic and saying nothing should give up reasonably quickly. */
        const val INITIAL_SILENCE_MS = 8_000L

        private const val TICK_MS = 200L

        /** Backstop against a recognizer that errors instantly, forever. */
        private const val MAX_RESTARTS = 40
    }
}
