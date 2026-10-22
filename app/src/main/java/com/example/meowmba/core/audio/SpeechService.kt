package com.example.meowmba.core.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
 * 4. Every consumer funnels through ONE process-wide [SessionEngine] (see
 *    [sharedEngine]). AiAi serves a single session for the whole package, so
 *    separate recognizer connections were not separate slots — they were busy
 *    refusals that made the mic button, always-on listening and the wake word
 *    die at once. Callback isolation between consumers (mic button, always-on,
 *    wake standby, barge-in, the background WakeWordService) is handled by
 *    [VoiceSessionGate] + newest-start-wins, and every [SessionEngine.startListening]
 *    tears down the previous session first so back-to-back sessions never
 *    collide with the recognition service's lingering teardown.
 */
class SpeechService(context: Context) : SpeechEngine, SpeechAudioCapture {

    override val isAvailable: Boolean =
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ||
            SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * ONE recognizer connection for the whole process.
     *
     * Every consumer — the interactive (mic button) engine, the always-on
     * conversation loop, the wake-word standby loop, and the background
     * WakeWordService — funnels through this single engine. AiAi serves ONE
     * session for the whole package, so a second SpeechRecognizer connection
     * is not a second slot: it is a busy refusal. When two connections raced
     * each other, the platform answered with a steady ERROR_RECOGNIZER_BUSY
     * (8) / ERROR_CLIENT (5) storm and every voice mode died at once — the
     * "always-on mic and wake word are dead" bug. One connection, one session,
     * and the app-level slot guard below is the only arbiter there can be.
     *
     * Callback isolation between consumers is handled by [VoiceSessionGate]
     * (state-and-timing), which is why sharing is safe: the old reason for a
     * dedicated wake-loop connection — late callbacks bleeding into a new
     * session — is exactly what the gate exists to solve.
     */
    private val interactive = sharedEngine(context)

    override val isListening get() = interactive.isListening
    override val partialResults get() = interactive.partialResults

    override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) =
        interactive.startListening(onResult, onError)

    override fun setFocusedListening(on: Boolean) {
        interactive.setFocusedListening(on)
    }

    /** Last ~2s of recognizer PCM when the service delivers buffers. */
    fun lastHeardPcm(): ShortArray = interactive.lastHeardPcm()

    override fun snapshotCapturedAudio(): CapturedSpeechAudio? =
        lastHeardPcm().takeIf { it.isNotEmpty() }?.let {
            CapturedSpeechAudio(it, CAPTURE_SAMPLE_RATE)
        }

    override fun stopListening() = interactive.stopListening()

    override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) =
        interactive.swapCallbacks(onResult, onError)

    /**
     * No-op. The engine is shared process-wide; destroying it here would tear
     * the connection out from under every other consumer (including the
     * background service). The engine rebuilds its recognizer on demand, so
     * "destroying" a facade just means nobody is listening.
     */
    override fun destroy() = Unit

    /**
     * The wake-word loop shares the process-wide engine. Historically this
     * built a SECOND connection so a dying loop session's late error could not
     * land in a mic-tap session — a concern [VoiceSessionGate] now handles, and
     * the second connection was what made the two loops refuse each other.
     * Returns the shared engine so callers' shapes stay unchanged.
     */
    fun createSessionEngine(): SpeechEngine = interactive

    /**
     * Tears down and rebuilds the recognizer connection. Used when a session
     * has gone silently wrong (e.g. the always-on watchdog declared a session
     * dead): the next startListening binds a fresh connection instead of
     * re-opening a zombie one.
     */
    fun rebindRecognizer() = interactive.rebindRecognizer()

    // ----------------------------------------------------------------------

    private class SessionEngine(private val ctx: Context) : SpeechEngine {

        override val isAvailable: Boolean =
            SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) ||
                SpeechRecognizer.isRecognitionAvailable(ctx)

        private var candidateIndex = 0
        private var component: ComponentName? = null
        private var allowPlatformDefault = false
        // Previous (working) path: bind AiAi / an explicit RecognitionService.
        // createOnDeviceSpeechRecognizer + Google Voice Search first is what
        // painted the Google listening UI over the whole face.
        private var bindOnDevice = false

        init {
            val candidates = recognitionCandidates(ctx)
            component = candidates.firstOrNull()
            allowPlatformDefault = candidates.isNotEmpty() && component == null
            Log.i(
                TAG,
                "engine init onDevice=$bindOnDevice component=${component?.flattenToShortString()}",
            )
        }

        private var recognizer: SpeechRecognizer? = null

        /**
         * Consecutive starts that never reported onReadyForSpeech. A never-ready
         * connection is a wedged one (see [onNeverReady]); this counter drives the
         * rebuild-then-failover escalation instead of retrying on the same wedged
         * connection forever (which is what held every voice mode dead for the
         * life of the process).
         */
        private var consecutiveNeverReady = 0

        /**
         * Consecutive accepted contention refusals (BUSY / CLIENT /
         * SERVER_DISCONNECTED). The connection may be holding a session the
         * app no longer tracks (a zombie); a few in a row means it is time to
         * tear the recognizer down so the next start binds a clean one.
         */
        private var consecutiveContention = 0

        /** When this engine last stopped/cancelled its recognizer (settle math). */
        private var lastInterruptAtMs = 0L

        /** Monotonic per-start token; invalidates superseded delayed starts. */
        private var startEpoch = 0L

        /**
         * Decides which callbacks belong to the live session.
         *
         * One recognizer now serves many sequential sessions, so a callback
         * carries no evidence of which session produced it — and re-attaching a
         * listener does not restore that, because a late callback from the old
         * session is still delivered to the new listener. [VoiceSessionGate]
         * therefore decides from state and timing, which is the only evidence
         * that exists. See its docs for why the previous epoch-capture approach
         * silently killed voice from the second session onward.
         */
        private val gate = VoiceSessionGate()

        /** Watchdog for a start that never reports ready. */
        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        /**
         * Give up the recognition slot for another engine. Silent: the loser
         * gets no onError, because it did not fail — it was pre-empted, and
         * reporting an error here would drive its retry loop straight back into
         * a fight with the engine that just took over.
         *
         * Cancels but KEEPS the connection: this engine will listen again
         * (always-on re-arms constantly), and creating a fresh SpeechRecognizer
         * per session is what piles sessions onto AiAi's small per-app capacity
         * until it refuses everyone.
         */
        fun yieldSession() {
            // Settle time is only owed when a session the service was actively
            // processing is being torn down. Defensive stops against an idle
            // engine (they happen constantly — every stopAlwaysOnListening,
            // mic-mode switch, wake re-arm) must NOT arm the 500ms settle, or
            // the next real start inherits a dead half-second of deafness.
            val hadLiveSession = _isListening.value
            runCatching { recognizer?.cancel() }
            startEpoch++
            if (hadLiveSession) lastInterruptAtMs = System.currentTimeMillis()
            // Silence this engine BEFORE the cancel artifact can arrive: the
            // gate is what makes "the loser gets no onError" true now that the
            // recognizer is no longer destroyed out from under its callbacks.
            gate.onYield()
            InputSoundLevel.clear("system_rms")
            _isListening.value = false
            _partialResults.value = ""
        }

        /** Release the shared slot if this engine still holds it. */
        fun releaseSessionSlot() {
            if (activeSession.compareAndSet(this, null)) InputSoundLevel.clear("system_rms")
        }

         private fun buildRecognizer(): SpeechRecognizer? {
             // Prefer the official on-device API. Binding Google TTS's
             // RecognitionService (the Fold's voice_recognition_service) is
             // ERROR_CLIENT / NO_MATCH; Google Voice Search is ERROR_TOO_MANY
             // REQUESTS. createOnDeviceSpeechRecognizer is a different path.
             if (bindOnDevice) {
                 val onDevice = runCatching {
                     SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                 }.getOrNull()
                 if (onDevice != null) {
                     liveRecognizers.incrementAndGet()
                     Log.i(TAG, "recognizer bound, live=$liveRecognizers component=on-device")
                     attachListener(onDevice)
                     return onDevice
                 }
                 Log.w(TAG, "on-device recognizer unavailable — falling through")
                 bindOnDevice = false
             }
             // Never call createSpeechRecognizer(ctx) with no component when
             // ranking produced an empty list — that binds Settings.Secure
             // voice_recognition_service, which is Google TTS on this Fold
             // and fails every session with ERROR_CLIENT.
             if (component == null && !allowPlatformDefault) return null
             return runCatching {
                 if (component != null) {
                     SpeechRecognizer.createSpeechRecognizer(ctx, component)
                 } else {
                     SpeechRecognizer.createSpeechRecognizer(ctx)
                 }
             }.getOrNull()?.also {
                 liveRecognizers.incrementAndGet()
                 Log.i(TAG, "recognizer bound, live=$liveRecognizers component=$componentName")
                 attachListener(it)
             }
         }

        private val _isListening = MutableStateFlow(false)
        override val isListening = _isListening.asStateFlow()

        private val _partialResults = MutableStateFlow("")
        override val partialResults = _partialResults.asStateFlow()

        private var onResult: ((String) -> Unit)? = null
        private var onError: ((Int) -> Unit)? = null

        private val heardPcmLock = Any()
        private val heardPcm = ShortArray(HEARD_PCM_SAMPLES)
        private var heardPcmAt = 0
        private var heardPcmFull = false

        fun lastHeardPcm(): ShortArray = synchronized(heardPcmLock) {
            if (!heardPcmFull && heardPcmAt == 0) return ShortArray(0)
            if (!heardPcmFull) return heardPcm.copyOf(heardPcmAt)
            if (heardPcmAt == 0) return heardPcm.copyOf()
            return ShortArray(heardPcm.size) { i ->
                heardPcm[(heardPcmAt + i) % heardPcm.size]
            }
        }

        private fun noteHeardPcm(buffer: ByteArray?) {
            if (buffer == null || buffer.size < 2) return
            synchronized(heardPcmLock) {
                var i = 0
                while (i + 1 < buffer.size) {
                    val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xff)).toShort()
                    heardPcm[heardPcmAt] = sample
                    heardPcmAt++
                    if (heardPcmAt >= heardPcm.size) {
                        heardPcmAt = 0
                        heardPcmFull = true
                    }
                    i += 2
                }
            }
        }

        private fun attachListener(r: SpeechRecognizer) {
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    // Logged BEFORE the gate: a callback dropped by the gate is
                    // invisible otherwise, and "did the service ever call back
                    // at all" is the question we cannot answer without this.
                    Log.i(TAG, "<- onReadyForSpeech")
                    if (!gate.acceptReady()) {
                        Log.w(TAG, "   ready DROPPED by gate (session already closed)")
                        // A ready arriving after the gate closed means the service
                        // opened a session this engine no longer tracks — a zombie
                        // that will hold the shared slot until the connection dies.
                        // Tear the connection down so the next start binds fresh.
                        rebindRecognizer()
                        return
                    }
                    _isListening.value = true
                    Log.i(TAG, "session ready ($componentName)")
                    // A session that went live proves the connection is healthy.
                    consecutiveNeverReady = 0
                    consecutiveContention = 0
                }
                override fun onBeginningOfSpeech() {
                    Log.i(TAG, "<- onBeginningOfSpeech")
                    if (!gate.acceptSpeechEdge()) return
                    Log.i(TAG, "speech started")
                }
                override fun onRmsChanged(rmsdB: Float) {
                    if (_isListening.value && activeSession.get() === this@SessionEngine) {
                        InputSoundLevel.publishSystemRms(rmsdB)
                    }
                }
                override fun onBufferReceived(buffer: ByteArray?) {
                    noteHeardPcm(buffer)
                }
                override fun onEndOfSpeech() {
                    Log.i(TAG, "<- onEndOfSpeech")
                    if (!gate.acceptSpeechEdge()) return
                    _isListening.value = false
                    InputSoundLevel.clear("system_rms")
                    Log.i(TAG, "speech ended")
                }
                override fun onError(error: Int) {
                    Log.i(TAG, "<- onError($error)")
                    // A pre-empted session must not corrupt the live one: AiAi
                    // delivers a result and then a late ERROR_SERVER_DISCONNECTED
                    // when the session is replaced mid-delivery. If that landed,
                    // a follow-up capture would end on it and the second half of
                    // the sentence ("... on spotify") would be cut off. The gate
                    // drops those, while still letting a genuine refusal
                    // (RECOGNIZER_BUSY) through so the engine can back off.
                    if (!gate.acceptError(error, System.currentTimeMillis())) {
                        Log.i(TAG, "stale error code=$error ignored")
                        return
                    }
                    _isListening.value = false
                    releaseSessionSlot()
                    Log.w(TAG, "session error code=$error ($componentName)")

                    // A burst of accepted refusals means the connection is likely
                    // holding a session nobody tracks (a zombie), or the service
                    // itself has wedged (measured on this device: AiAi refuses
                    // every start with BUSY until IT is restarted — which the app
                    // cannot do, so fail over). Rebuild first, then move on.
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                        error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS
                    ) {
                        consecutiveContention++
                        if (consecutiveContention >= CONTENTION_FAILOVER_THRESHOLD) {
                            if (advanceCandidate()) {
                                Log.w(TAG, "contention x$consecutiveContention — failed over to $component")
                            } else {
                                Log.e(TAG, "every recognition service refused the session ($componentName)")
                            }
                            consecutiveContention = 0
                            rebindRecognizer()
                        } else if (consecutiveContention == CONTENTION_REBIND_THRESHOLD) {
                            Log.w(TAG, "contention x$consecutiveContention — rebinding a fresh recognizer")
                            rebindRecognizer()
                        }
                    } else {
                        consecutiveContention = 0
                    }

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
                    Log.i(TAG, "<- onResults")
                    if (!gate.acceptResults()) return
                    releaseSessionSlot()
                    consecutiveNeverReady = 0
                    consecutiveContention = 0
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val official = SpokenText.bestTranscript(matches, scores)
                    // AiAi on this device often ends a session that HEARD speech
                    // with an empty result. The last partial is the only copy of
                    // the words — promote it so the caller does not have to wait
                    // for a hang watchdog to recover them.
                    val text = SpokenText.preferFinal(official, _partialResults.value)
                    Log.i(TAG, "session result: ${text.take(60).ifEmpty { "(empty)" }}")
                    // Deliver even an empty transcript: consumers use the blank to
                    // learn the session ended on silence (quiet-open sessions end
                    // this way constantly) and must re-arm. Without it the
                    // always-on loop hears "session over" from nobody and its
                    // state (latch, interaction state) sticks forever.
                    onResult?.invoke(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    Log.i(TAG, "<- onPartialResults")
                    if (!gate.acceptPartial()) return
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val scores = partialResults?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        val best = SpokenText.bestTranscript(matches, scores).ifBlank { matches[0] }
                        Log.i(TAG, "   partial: \"${best.take(80)}\"")
                        _partialResults.value = best
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        private val componentName: String
            get() = when {
                bindOnDevice -> "on-device"
                component != null -> component!!.flattenToShortString()
                else -> "platform default"
            }

        @Volatile
        private var focusedListening = false

        override fun setFocusedListening(on: Boolean) {
            focusedListening = on
        }

        override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            // Only ONE engine may hold a session at a time, process-wide.
            //
            // Separate SessionEngine instances (see createSessionEngine) keep the
            // wake loop's callbacks away from the mic button's — but they do NOT
            // give each engine its own slot in the recognition service. AiAi
            // serves a single session for the whole app, so when the always-on
            // capture and the wake spotter both opened one, each refused the
            // other: measured on device as bursts of ERROR_SERVER_DISCONNECTED
            // (11), ERROR_RECOGNIZER_BUSY (8) and ERROR_CLIENT (5) arriving
            // within ten milliseconds of each other, over and over, with no
            // session ever succeeding. The mic button, always-on face listening
            // and the wake word all appeared dead at once, which is the giveaway
            // that they were killing each other rather than each being broken.
            //
            // The newest request wins: it is the one that reflects what the user
            // just did. The previous holder is torn down first so the service
            // sees a clean handover instead of two live binds.
            (activeSession.getAndSet(this) as? SessionEngine)
                ?.takeIf { it !== this }?.yieldSession()

            this.onResult = onResult
            this.onError = onError
            _isListening.value = false
            _partialResults.value = ""

            // The actual start may be delayed so the recognizer can finish
            // tearing down the session we are taking over: stop/cancel followed
            // by an immediate startListening lands ERROR_CLIENT (5) on AiAi and
            // the new session's ready is "dropped by gate" — which is exactly
            // what made the mic button look dead while always-on or the wake
            // standby held the mic. Waiting out the settle window keeps the
            // handover clean. A start that supersedes this one (startEpoch bump
            // from a newer request, stop, yield or destroy) invalidates the
            // delayed open via the epoch token.
            val epoch = ++startEpoch
            val settle = lastInterruptAtMs + HANDOFF_SETTLE_MS - System.currentTimeMillis()
            if (settle > 0) {
                Log.i(TAG, "handoff: delaying start ${settle}ms for the service to settle")
            }
            mainHandler.postDelayed({ doStartListening(epoch) }, maxOf(0L, settle))
        }

        /** Opens the session; runs immediately or after the handoff settle delay. */
        private fun doStartListening(epoch: Long) {
            if (startEpoch != epoch) {
                Log.i(TAG, "start superseded during settle delay — not starting")
                return
            }
            if (activeSession.get() !== this) {
                Log.i(TAG, "start superseded by another engine during settle delay")
                return
            }
            gate.onStart(System.currentTimeMillis())
            // Remember whether this connection has ever been used: a recognizer
            // built just now must NOT be cancelled (see below).
            val reused = recognizer != null
            val r = recognizer ?: buildRecognizer()?.also { recognizer = it }
            if (r == null) {
                gate.onDestroy()
                onError?.invoke(SpeechRecognizer.ERROR_CLIENT)
                return
            }
            // Settle any in-flight session on the SAME connection. Reopening a
            // fresh connection per session is what stacks sessions on AiAi's
            // tiny per-app capacity — measured as "capacity is full" after just
            // a few always-on re-arms. One recognizer per engine, forever.
            //
            // ONLY on a connection that has actually been used. Cancelling a
            // recognizer built moments ago tears the binding down before it has
            // ever listened — the framework says so outright:
            //   "Connection to speech recognition service lost, but no
            //    #startListening has been invoked yet."
            // followed by AiAiSpeechRecognitionService#onCancel and #onDestroy.
            // The startListening below then runs on a dead binding and NOTHING
            // comes back: no ready, no result, no error. That silence was the
            // whole bug — mic and wake word appeared dead with a bound
            // recognizer and not one callback ever arriving.
            if (reused) runCatching { r.cancel() }
            lastInterruptAtMs = 0L
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    java.util.Locale.getDefault().toLanguageTag(),
                )

                // From prog's voice-quality work: modern recognizers can use device
                // context (contacts, installed apps, prior vocabulary) and punctuation.
                // Unsupported services ignore these; they are hints, not commands.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_BIASING_DEVICE_CONTEXT, true)
                    putExtra(
                        RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                        RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY,
                    )
                    putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
                    putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, false)
                    putStringArrayListExtra(
                        RecognizerIntent.EXTRA_BIASING_STRINGS,
                        arrayListOf(
                            "Memba", "Meowmba", "Meow", "Ollama", "ChatGPT", "Grok",
                            "Spotify", "YouTube", "Gmail",
                        ),
                    )
                }

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
                    if (focusedListening) FOCUS_COMPLETE_SILENCE_MS else COMPLETE_SILENCE_MS,
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    if (focusedListening) FOCUS_POSSIBLY_COMPLETE_SILENCE_MS else POSSIBLY_COMPLETE_SILENCE_MS,
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    if (focusedListening) FOCUS_MINIMUM_SESSION_MS else MINIMUM_SESSION_MS,
                )
                // Keep recognition on the device when the engine can do it.
                // Google Voice Search is a cloud backend — PREFER_OFFLINE there
                // is a no-model stall, not privacy.
                val preferOffline = PREFER_OFFLINE &&
                    component?.packageName != GOOGLE_RECOGNITION_PACKAGE
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            }
            r.startListening(intent)

            // The gate drops cancel artifacts during startup, so a start that
            // never reports ready would otherwise wait forever. One delayed
            // check turns that silence into a normal error the engine retries
            // on. readyTimedOut() self-cancels once the session goes live, so a
            // late-firing check on a healthy session is a no-op. A never-ready
            // connection is a wedged one, so onNeverReady() escalates (rebind,
            // then fail over to the next recognizer) instead of retrying the
            // same broken connection forever.
            mainHandler.postDelayed({
                if (gate.readyTimedOut(System.currentTimeMillis())) {
                    Log.w(TAG, "session never became ready ($componentName)")
                    _isListening.value = false
                    releaseSessionSlot()
                    onNeverReady()
                }
            }, READY_TIMEOUT_MS)
        }

        /**
         * A start that never reported ready is the signature of a wedged
         * connection — the recognizer accepts the request and then goes silent,
         * leaving every voice mode dead at once for the life of the process.
         * Retrying on the same connection just re-wedges it, so escalate:
         * first rebind a fresh recognizer, and if that still does not help,
         * fail over to the next recognition service. The caller on the other
         * side of [onError] still decides what repeated failure means (its
         * backoff / fatal-stop logic).
         */
        private fun onNeverReady() {
            consecutiveNeverReady++
            if (consecutiveNeverReady >= NEVER_READY_REBIND_THRESHOLD) {
                Log.w(TAG, "never-ready x$consecutiveNeverReady — rebinding a fresh recognizer")
                rebindRecognizer()
            }
            if (consecutiveNeverReady >= NEVER_READY_FAILOVER_THRESHOLD) {
                if (advanceCandidate()) {
                    Log.i(TAG, "never-ready x$consecutiveNeverReady — failed over to $component")
                    consecutiveNeverReady = 0
                } else {
                    Log.e(TAG, "every recognition service failed to become ready ($componentName)")
                }
            }
            onError?.invoke(SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
        }

        /** Destroys the current recognizer so the next start binds a fresh connection. */
        internal fun rebindRecognizer() {
            if (recognizer != null) {
                runCatching { recognizer?.destroy() }
                liveRecognizers.decrementAndGet()
                recognizer = null
            }
        }

        /**
         * Recovery is Sherpa in the ViewModel, not another RecognitionService.
         * Walking onto Google Voice Search is what painted the fullscreen
         * listening UI over the face.
         */
        private fun advanceCandidate(): Boolean = false

        override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            this.onResult = onResult
            this.onError = onError
        }

        override fun stopListening() {
            // Stop, don't destroy: the connection is kept so the next
            // [startListening] is a cheap reopen on a known-good slot. Destroying
            // here would force a fresh connection (and a fresh slot) on the next
            // start, which is what overloads AiAi when sessions churn.
            //
            // Settle is armed ONLY when a live session actually existed — see
            // [yieldSession]. Stopping an idle engine is a no-op on the service,
            // so charging the settle window for it just taxes the next start.
            val hadLiveSession = _isListening.value
            recognizer?.stopListening()
            startEpoch++
            if (hadLiveSession) lastInterruptAtMs = System.currentTimeMillis()
            _isListening.value = false
            releaseSessionSlot()
        }

        override fun destroy() {
            gate.onDestroy()
            mainHandler.removeCallbacksAndMessages(null)
            startEpoch++
            lastInterruptAtMs = System.currentTimeMillis()
            recognizer?.destroy()
            if (recognizer != null) liveRecognizers.decrementAndGet()
            recognizer = null
            _isListening.value = false
            releaseSessionSlot()
        }
    }

    // ----------------------------------------------------------------------

    internal companion object {
        /**
         * The single recognizer connection shared by every SpeechService facade
         * in the process. AiAi serves one session per package; a second
         * connection only ever gets busy refusals. See the field docs on
         * [SpeechService.interactive].
         */
        @Volatile
        private var shared: SessionEngine? = null

        private fun sharedEngine(context: Context): SessionEngine =
            shared ?: synchronized(this) {
                shared ?: SessionEngine(context.applicationContext).also { shared = it }
            }

        /**
         * The engine currently holding the recognition service's single session
         * slot, across every SpeechService and SessionEngine in the process.
         * AiAi serves one session for the whole app; without this, concurrent
         * engines refuse each other forever.
         */
        private val activeSession =
            java.util.concurrent.atomic.AtomicReference<Any?>(null)

        /** Live SpeechRecognizer instances, process-wide (diagnostic). */
        private val liveRecognizers =
            java.util.concurrent.atomic.AtomicInteger(0)

        private const val TAG = "MeowmbaVoice"

        /** Rolling capture for speaker ID — ~2s at 16 kHz mono 16-bit. */
        private const val HEARD_PCM_SAMPLES = 16_000 * 2
        private const val CAPTURE_SAMPLE_RATE = 16_000

        /**
         * Capture-window hints. ~2.5s of silence before a sentence is considered
         * finished, and sessions run at least 15s so a slow speaker is not cut off.
         */
        // Int, NOT Long. These go into the recognizer Intent, and the framework
        // reads them with getInt(): a Long value silently misses and the
        // DEFAULT (0) is used instead. The device says so out loud —
        //   W/Bundle: Key ...SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS
        //   expected Integer but value was a java.lang.Long. The default value
        //   0 was returned.
        // Zero complete-silence and zero minimum length tell the recognizer to
        // end the session immediately, so it starts and dies before anyone can
        // speak. That is what "the mic and wake word do nothing" looked like.
        private const val COMPLETE_SILENCE_MS = 2_500
        private const val POSSIBLY_COMPLETE_SILENCE_MS = 2_000
        private const val MINIMUM_SESSION_MS = 15_000
        private const val FOCUS_COMPLETE_SILENCE_MS = 4_000
        private const val FOCUS_POSSIBLY_COMPLETE_SILENCE_MS = 3_000
        private const val FOCUS_MINIMUM_SESSION_MS = 20_000

        /**
         * How long a start may go without onReadyForSpeech before the engine is
         * told it failed. Must be >= VoiceSessionGate's own readyTimeoutMs; the
         * gate is the authority, this is just when we ask it.
         */
        private const val READY_TIMEOUT_MS = 5_500L

        /**
         * Privacy-first: keep recognition on the device.
         *
         * Tested 2026-08-11 as a suspect for the never-becomes-ready stall and
         * CLEARED — the stall reproduces identically with this false, so the
         * offline model is not the cause. Left true, which is the intended
         * behaviour; flipping it sends audio off the device.
         */
        private const val PREFER_OFFLINE = true

        /** Android System Intelligence — the real modern on-device recognizer. */
        private const val AIAI_PACKAGE = "com.google.android.as"

        /** Google app's recognizer. */
        private const val GOOGLE_RECOGNITION_PACKAGE = "com.google.android.googlequicksearchbox"

        /**
         * How long to wait after a stop/cancel/yield before reopening a session.
         * AiAi serves ONE session for the whole app; a start that lands while the
         * previous session is still tearing down is refused with ERROR_CLIENT (5),
         * and the new session's onReadyForSpeech is dropped by the gate. Measured
         * on device: the mic button looked dead whenever always-on or the wake
         * standby held the mic, purely because its start raced the teardown.
         */
        private const val HANDOFF_SETTLE_MS = 500L

        /**
         * Never-ready starts before we rebuild a fresh recognizer connection.
         * A connection that accepted startListening and then went silent is
         * wedged; retrying on it re-wedges. Rebind first (cheap), fail over
         * after [NEVER_READY_FAILOVER_THRESHOLD].
         */
        private const val NEVER_READY_REBIND_THRESHOLD = 2

        /**
         * Never-ready starts before failing over to the next recognition
         * service. Two rebuilds of a dead connection is enough; the wedged
         * engine is clearly not going to recover, so move on.
         */
        private const val NEVER_READY_FAILOVER_THRESHOLD = 4

        /**
         * Accepted BUSY/CLIENT/SERVER_DISCONNECTED refusals before the
         * recognizer connection is rebuilt. A few in a row means the connection
         * is holding a session nobody tracks (a zombie) that would refuse every
         * later start — the permanent "always-on mic and wake word are dead"
         * wedge. Rebuild clears it.
         */
        private const val CONTENTION_REBIND_THRESHOLD = 3

        /**
         * Accepted BUSY/CLIENT/SERVER_DISCONNECTED refusals before failing over
         * to the next recognition service. Once a service refuses this many
         * times even after a fresh rebuild, it is wedged at the service level
         * (measured on this device: AiAi refuses every start with BUSY until
         * the Google app itself is restarted — which the app cannot do). The
         * only recovery left is a different recognizer.
         */
        private const val CONTENTION_FAILOVER_THRESHOLD = 6

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
         * Recognition services to try, best first, ending in a possible
         * platform-default slot (`null`). Used once to pick the initial service
         * and again by [SessionEngine.onNeverReady] to fail over, so a device
         * whose preferred service turns out to be deaf can move to the next one
         * instead of looping forever on a wedged connection.
         *
         * Order: Android System Intelligence (AiAi), then the Google app if it
         * actually exposes a RecognitionService, then the system default if it
         * is not denylisted. Never pin Google Voice Search first — binding
         * GoogleRecognitionService (or launching ACTION_RECOGNIZE_SPEECH)
         * paints the Google listening UI over the whole screen.
         * Never append platform-default (`null`) when that default is Google TTS —
         * createSpeechRecognizer(ctx) binds it and every session is ERROR_CLIENT.
         */
        fun recognitionCandidates(context: Context): List<ComponentName?> {
            val installed = runCatching {
                context.packageManager.queryIntentServices(
                    Intent(RecognitionService.SERVICE_INTERFACE),
                    0,
                )
            }.getOrDefault(emptyList())
                .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
                .filter { it.packageName !in DENYLISTED_PACKAGES }

            val systemDefault = runCatching {
                Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
            }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { ComponentName.unflattenFromString(it) }

            val ranked = rankRecognitionCandidates(
                installed = installed,
                googleVoiceUsable = false,
                systemDefault = systemDefault,
            )
            Log.i(
                TAG,
                "recognition candidates: ${ranked.joinToString { it?.flattenToShortString() ?: "platform-default" }} " +
                    "(installed=${installed.joinToString { it.flattenToShortString() }}; " +
                    "sysDefault=${systemDefault?.flattenToShortString()})",
            )
            return ranked
        }

        /**
         * Pure ranking so the Fold case (TTS as system default) can be unit-tested.
         * [googleVoiceUsable] is kept on the signature so old tests still compile;
         * Voice Search is never added to the list (fullscreen overlay).
         */
        @Suppress("UNUSED_PARAMETER")
        fun rankRecognitionCandidates(
            installed: List<ComponentName>,
            googleVoiceUsable: Boolean,
            systemDefault: ComponentName?,
        ): List<ComponentName?> {
            val candidates = ArrayList<ComponentName?>()

            installed.firstOrNull { it.packageName == AIAI_PACKAGE }
                ?.let { candidates += it }
            // Google Voice Search is never a candidate. Binding it draws the
            // fullscreen Google listening UI over the face.

            systemDefault
                ?.takeIf { it.packageName !in DENYLISTED_PACKAGES }
                ?.takeIf { it.packageName != GOOGLE_RECOGNITION_PACKAGE }
                ?.takeIf { it !in candidates }
                ?.let { candidates += it }

            if (candidates.isEmpty()) {
                val defaultIsSafe = systemDefault == null ||
                    systemDefault.packageName !in DENYLISTED_PACKAGES
                if (defaultIsSafe) candidates += null
            }
            return candidates
        }

        /** The initial, best-first choice of recognition service. */
        fun recognitionService(context: Context): ComponentName? =
            recognitionCandidates(context).firstOrNull()
    }
}
