package com.example.meowmba.core.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Wake-word source abstraction. A dedicated always-on engine (Porcupine,
 * sherpa-onnx KWS, etc.) can replace the recognizer-loop implementation
 * without touching the ViewModel.
 */
interface WakeWordEngine {
    val isRunning: kotlinx.coroutines.flow.StateFlow<Boolean>

    /** Fired when the wake phrase is heard; carries any command spoken after it. */
    var onWake: ((command: String?) -> Unit)?

    fun start()
    fun stop()
}

/**
 * Wake-word detection built on top of the active [SpeechEngine]: a
 * restart-on-complete recognition loop whose transcripts are scanned by a
 * [WakePhraseMatcher].
 *
 * This runs locally (the engine prefers offline recognition) and only while
 * the app is in the foreground — a justified-foreground-service design for
 * true background listening is a later phase. Android recognizers have a
 * session time limit, so each completed/error session is restarted after a
 * short backoff; [stop] cancels the loop cleanly.
 */
class RecognizerWakeWordEngine(
    private val speechEngine: SpeechEngine,
    private var matcher: WakePhraseMatcher,
    private val scope: CoroutineScope
) : WakeWordEngine {

    private val _isRunning = MutableStateFlow(false)
    override val isRunning = _isRunning.asStateFlow()

    override var onWake: ((command: String?) -> Unit)? = null

    /**
     * Fired when the recognizer keeps failing ([MAX_CONSECUTIVE_ERRORS] in a
     * row) — the engine stops instead of hot-looping on a broken device, and
     * the companion surfaces the reason honestly.
     */
    var onFatalError: (() -> Unit)? = null

    @Volatile
    private var running = false

    @Volatile
    private var consecutiveErrors = 0

    /** Collector for this session's partial transcripts. */
    private var partialJob: Job? = null

    /**
     * True once this session has woken the companion. Partials keep growing, so
     * without this a single "hey meow" would fire on every subsequent update.
     */
    @Volatile
    private var firedThisSession = false

    /** Update the phrase set (e.g. after the companion is renamed). */
    fun updateMatcher(newMatcher: WakePhraseMatcher) {
        matcher = newMatcher
    }

    override fun start() {
        if (running) return
        running = true
        consecutiveErrors = 0
        _isRunning.value = true
        listen()
    }

    override fun stop() {
        running = false
        _isRunning.value = false
        partialJob?.cancel()
        partialJob = null
        speechEngine.stopListening()
    }

    private fun listen() {
        if (!running) return
        firedThisSession = false

        // Watch PARTIAL transcripts as well as the final one. This is the difference
        // between usually hearing the wake phrase and often missing it: a session that
        // ends in ERROR_NO_MATCH delivers no final result at all, so anything said
        // during it — including "hey meow" — was silently discarded, even though the
        // partials had already contained it. Observed on-device: speech detected for
        // 7s, then NO_MATCH and nothing handed to the matcher.
        partialJob?.cancel()
        partialJob = scope.launch {
            speechEngine.partialResults.collect { partial ->
                if (!running || firedThisSession || partial.isBlank()) return@collect
                val match = matcher.match(partial) ?: return@collect
                // One wake per session: partials keep growing and would re-fire.
                firedThisSession = true
                consecutiveErrors = 0
                onWake?.invoke(match.command)
            }
        }

        speechEngine.startListening(
            onResult = { text ->
                if (!running) return@startListening
                consecutiveErrors = 0  // a completed session proves the recognizer works
                // Already woken from a partial in this session — the final transcript
                // is the same utterance, so do not wake twice.
                if (firedThisSession) return@startListening
                val match = matcher.match(text)
                if (match != null) {
                    firedThisSession = true
                    // Hand off to the companion; it resumes the engine when idle.
                    onWake?.invoke(match.command)
                } else {
                    restart(RESTART_DELAY_MS)
                }
            },
            onError = { code ->
                if (!running) return@startListening
                // Woken from a partial: the companion owns the mic now, so this
                // session's terminal error is expected and must not restart the loop.
                if (firedThisSession) return@startListening
                when {
                    isContention(code) -> {
                        // Something ELSE holds the recognizer — most often the
                        // foreground WakeWordService and this in-app loop overlapping
                        // during the mic handoff on resume/pause. That is transient and
                        // says nothing about the device, so it must not count toward
                        // "keeps failing"; counting it is what produced the false
                        // "wake phrase stopped" report while the recognizer was fine.
                        consecutiveErrors = 0
                        restart(CONTENTION_BACKOFF_MS)
                    }
                    !isInfrastructureError(code) -> {
                        // Quiet room: no-speech timeouts are the loop's NORMAL
                        // heartbeat, not a failing recognizer. Counting them made
                        // the engine declare the device broken after ~5 idle
                        // sessions ("wake phrase stopped…" bug).
                        consecutiveErrors = 0
                        restart(RESTART_DELAY_MS)
                    }
                    ++consecutiveErrors >= MAX_CONSECUTIVE_ERRORS -> {
                        stop()
                        onFatalError?.invoke()
                    }
                    else -> restart(ERROR_BACKOFF_MS)
                }
            }
        )
    }

    private fun restart(delayMs: Long) {
        scope.launch {
            delay(delayMs)
            if (running) listen()
        }
    }

    companion object {
        /**
         * Gap between sessions. The recognizer is DEAF for this plus its own startup
         * (~280ms measured), and a wake phrase landing in that window is simply lost —
         * so keep it short. 250ms here made the deaf window over half a second.
         */
        private const val RESTART_DELAY_MS = 100L
        private const val ERROR_BACKOFF_MS = 700L

        /**
         * Longer than the normal backoff: whoever else holds the recognizer needs time
         * to let go, and hammering it just keeps both sides busy.
         */
        private const val CONTENTION_BACKOFF_MS = 1_500L
        private const val MAX_CONSECUTIVE_ERRORS = 5

        /**
         * The mic is temporarily unavailable to us — not a broken recognizer. Retry
         * with a longer backoff; never escalate to "this device is broken".
         *
         * - RECOGNIZER_BUSY: another client owns it, usually the foreground
         *   WakeWordService and the in-app loop overlapping during the mic handoff.
         * - INSUFFICIENT_PERMISSIONS: appops grants this app mic access in FOREGROUND
         *   mode only (verified: "Uid mode: RECORD_AUDIO: foreground" with the runtime
         *   permission granted). Background moments therefore deny the mic and clear
         *   again on their own. Escalating turned that into a permanent false
         *   "the speech recognizer keeps failing on this device".
         *
         * ERROR_CLIENT is deliberately NOT here. [SpeechService.startListening] reports
         * CLIENT when the recognizer could not be created at all — a genuinely unusable
         * device — so treating it as transient would retry forever instead of stopping.
         * The CLIENT errors seen in the logs came from the removed failover binding
         * assistant services that reject dictation, not from a healthy recognizer.
         */
        internal fun isContention(code: Int): Boolean = when (code) {
            android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> true
            else -> false
        }

        /**
         * Errors that mean the recognizer itself is broken (service missing,
         * busy, permission, audio device) — NOT "nothing heard" (6/7), which
         * is routine for a wake loop in a quiet room.
         */
        internal fun isInfrastructureError(code: Int): Boolean = when (code) {
            android.speech.SpeechRecognizer.ERROR_NO_MATCH,
            android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> false
            else -> true
        }
    }
}
