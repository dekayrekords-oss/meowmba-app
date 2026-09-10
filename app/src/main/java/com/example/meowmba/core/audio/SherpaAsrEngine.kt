package com.example.meowmba.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Always-on speech-to-text on a dedicated [AudioRecord] + sherpa streaming
 * zipformer. Android's SpeechRecognizer (AiAi / Google) is BUSY or
 * rate-limited on this Fold, so face-mode "just talk" has to live here —
 * the same hardware path as the wake-word spotter, never both at once.
 */
class SherpaAsrEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    /** Mic source. Meta glasses: BLUETOOTH_SCO via [bluetoothSco]. */
    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    /**
     * True = capture from a Bluetooth SCO headset (Ray-Ban Meta glasses show
     * up as one). Starts the SCO link and prefers the BT device explicitly —
     * without setCommunicationDevice the phone keeps using its own mic even
     * with SCO on. Sherpa needs 16kHz; an 8kHz SCO link is upsampled 2x.
     */
    private val bluetoothSco: Boolean = false,
) : SpeechEngine, SpeechAudioCapture {
    override val deliversFinalResults = true
    override val continuousCapture = true
    override val finalizationPending get() = finalizer?.hasPending == true
    /** A cloud refiner needs the original audio even when local words are poor. */
    @Volatile var forwardEndpointAudio = false

    /** True when a Bluetooth SCO audio device is connected right now. */
    fun bluetoothHeadsetConnected(): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return false
        return am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
    }

    private var scoStarted = false

    override val isAvailable: Boolean
        get() = VoiceModelStore(context).asrReady()

    private val _isListening = MutableStateFlow(false)
    override val isListening = _isListening.asStateFlow()

    private val _partialResults = MutableStateFlow("")
    override val partialResults = _partialResults.asStateFlow()

    private var onResult: ((String) -> Unit)? = null
    private var onError: ((Int) -> Unit)? = null

    private var recognizer: OnlineRecognizer? = null
    @Volatile private var captureJob: Job? = null
    private val captureMutex = Mutex()
    @Volatile private var destroyed = false
    @Volatile private var finalizer: SpeechFinalizer? = null

    @Volatile
    private var running = false
    @Volatile private var finishRequested = false

    override fun finishInput(): Boolean {
        if (!running) return false
        if (finalizationPending) return true
        finishRequested = true
        return true
    }

    @Volatile
    /** Audio of the last delivered final, never a newer endpoint waiting in the queue. */
    var lastHeardPcm: ShortArray = ShortArray(0)
        private set

    override fun snapshotCapturedAudio(): CapturedSpeechAudio? =
        lastHeardPcm.takeIf { it.isNotEmpty() }?.let {
            CapturedSpeechAudio(it.copyOf(), SAMPLE_RATE)
        }

    /**
     * Optional local-Whisper refiner. When set, a finished utterance is also
     * transcribed by Whisper and the FINAL text is replaced with Whisper's —
     * the zipformer still drives the live partials and endpointing.
     *
     * This is the whole point of the Whisper backend: the streaming zipformer
     * is fast but measurably mishears, and Whisper is a batch model that
     * cannot stream. Hybrid = the live preview remains provisional; the
     * finished local pass supplies the answer text, not guaranteed ground truth.
     * Null keeps streaming-only finalization.
     */
    @Volatile
    var whisperRefiner: WhisperAsrEngine? = null

    override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        if (destroyed) {
            onError(android.speech.SpeechRecognizer.ERROR_CLIENT)
            return
        }
        this.onResult = onResult
        this.onError = onError
        _partialResults.value = ""
        if (!isAvailable) {
            onError(android.speech.SpeechRecognizer.ERROR_CLIENT)
            return
        }
        start()
    }

    override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) {
        this.onResult = onResult
        this.onError = onError
    }

    override fun stopListening() {
        onResult = null
        onError = null
        _isListening.value = false
        _partialResults.value = ""
        stop()
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        stopListening()
        // Native decode/read cannot be interrupted mid-call. Dispose after it
        // exits, off main, even if the owning ViewModel scope was cancelled.
        CoroutineScope(Dispatchers.Default).launch {
            captureMutex.withLock {
                runCatching { recognizer?.release() }
                recognizer = null
            }
        }
    }

    private fun start() {
        if (running || captureJob?.isActive == true) return
        running = true
        finishRequested = false
        _isListening.value = true
        // A cancelled AudioRecord.read/native decode may still be returning.
        // Wait for its finally/release before starting another stream. Checking
        // the PARENT scope's isActive allowed the old loop to resume when a
        // new session set the shared running flag back to true.
        val job = scope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            try {
                captureMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    captureLoop()
                }
            } finally {
                if (captureJob === currentCoroutineContext()[Job]) {
                    running = false
                    _isListening.value = false
                    captureJob = null
                }
            }
        }
        captureJob = job
        job.start()
    }

    fun stop() {
        running = false
        _isListening.value = false
        finalizer?.close()
        captureJob?.cancel()
    }

    suspend fun stopAndAwait(timeoutMs: Long = 800L) {
        stop()
        val job = captureJob ?: return
        withTimeoutOrNull(timeoutMs) { job.join() }
        if (job.isCompleted && captureJob === job) captureJob = null
    }

    private suspend fun captureLoop() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            running = false
            _isListening.value = false
            withContext(Dispatchers.Main) {
                onError?.invoke(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            }
            return
        }

        val rec = recognizer ?: withContext(Dispatchers.IO) {
            runCatching { buildRecognizer() }.getOrElse {
                Log.w(TAG, "ASR init failed", it)
                null
            }?.also { recognizer = it }
        }
        if (rec == null) {
            running = false
            _isListening.value = false
            withContext(Dispatchers.Main) {
                onError?.invoke(android.speech.SpeechRecognizer.ERROR_CLIENT)
            }
            return
        }
        recognizer = rec

        if (bluetoothSco) {
            // Route the glasses' mic before opening AudioRecord. minSdk 31 so
            // setCommunicationDevice is always available (BLUETOOTH_CONNECT is
            // declared in the manifest).
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val scoDevice = am?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                ?.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            if (am != null && scoDevice != null) {
                runCatching {
                    am.startBluetoothSco()
                    am.isBluetoothScoOn = true
                    am.setCommunicationDevice(scoDevice)
                    scoStarted = true
                    Log.i(TAG, "Bluetooth SCO routing requested for glasses mic")
                }.onFailure { Log.w(TAG, "SCO start failed", it) }
            } else {
                Log.w(TAG, "bluetoothSco requested but no SCO device connected — using built-in path")
            }
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            withContext(Dispatchers.Main.immediate) { onError?.invoke(android.speech.SpeechRecognizer.ERROR_AUDIO) }
            return
        }
        val record = try {
            AudioRecord(
                if (scoStarted) MediaRecorder.AudioSource.VOICE_COMMUNICATION else audioSource,
                SAMPLE_RATE, CHANNEL, ENCODING,
                maxOf(minBuf * 4, SAMPLE_RATE),
            )
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord construction failed", e)
            null
        }
        // 8kHz SCO links (older HFP) can't do 16k — retry at 8k and upsample.
        var scoRate8k = false
        val effectiveRecord = if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
            record
        } else {
            runCatching { record?.release() }
            if (scoStarted) {
                scoRate8k = true
                try {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        8000, CHANNEL, ENCODING,
                        maxOf(minBuf * 4, SAMPLE_RATE),
                    ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                } catch (e: Exception) {
                    Log.w(TAG, "8k SCO AudioRecord failed", e)
                    null
                }
            } else null
        }
        if (effectiveRecord == null) {
            withContext(Dispatchers.Main.immediate) { onError?.invoke(android.speech.SpeechRecognizer.ERROR_AUDIO) }
            return
        }

        var stream: OnlineStream? = null
        // The endpoint can run 30s. A 20s ring silently dropped the first ten
        // seconds of a long utterance before the final Whisper pass saw it.
        val uttered = PcmRingBuffer(AsrCaptureBounds.PCM_CAPACITY)
        lastHeardPcm = ShortArray(0)
        val sessionFinalizer = SpeechFinalizer(
            CoroutineScope(scope.coroutineContext + Dispatchers.Default),
            refine = { audio -> whisperRefiner?.transcribe(audio) },
            deliver = { text, audio ->
                withContext(Dispatchers.Main.immediate) {
                    if (running) {
                        // Publish this final's audio immediately before its
                        // callback. Capture must not replace it at enqueue time
                        // while an earlier utterance is still being refined.
                        lastHeardPcm = audio
                        Log.i(TAG, "ASR final delivered characters=${text.length}")
                        runCatching { onResult?.invoke(text) }
                    }
                }
            },
            discard = {
                withContext(Dispatchers.Main.immediate) {
                    if (running) { lastHeardPcm = ShortArray(0); onResult?.invoke("") }
                }
            },
        )
        finalizer = sessionFinalizer
        try {
            effectiveRecord.startRecording()
            check(effectiveRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }
            Log.i(TAG, "ASR capture rate=${effectiveRecord.sampleRate} channels=${effectiveRecord.channelCount} " +
                "source=${effectiveRecord.audioSource} routeType=${effectiveRecord.routedDevice?.type ?: -1} " +
                "bufferFrames=${effectiveRecord.bufferSizeInFrames} whisper=${whisperRefiner != null}")
            val s = rec.createStream()
            if (s.ptr == 0L) {
                Log.w(TAG, "createStream failed")
                withContext(Dispatchers.Main.immediate) { onError?.invoke(android.speech.SpeechRecognizer.ERROR_CLIENT) }
                return
            }
            stream = s
            Log.i(TAG, "always-on ASR listening")
            val pcm = ShortArray(CHUNK_SAMPLES)
            val fullFrame = FloatArray(CHUNK_SAMPLES * if (scoRate8k) 2 else 1)
            val signalGate = DigitalSilenceGate()
            val preview = StreamingTranscriptPreview()
            var decodeNanos = 0L
            var hadHypothesis = false

            while (running && currentCoroutineContext().isActive) {
                val read = effectiveRecord.read(pcm, 0, pcm.size)
                currentCoroutineContext().ensureActive()
                if (read < 0) {
                    Log.w(TAG, "ASR AudioRecord read failed code=$read")
                    withContext(Dispatchers.Main.immediate) {
                        if (running) onError?.invoke(android.speech.SpeechRecognizer.ERROR_AUDIO)
                    }
                    break
                }
                if (read == 0) continue
                if (!signalGate.shouldDecode(pcm, read) && !finishRequested) continue
                // Store at the rate advertised to Whisper and audio consumers.
                // Previously 8k SCO was incorrectly sent to Whisper as 16k.
                uttered.append(pcm, read, repeatEach = if (scoRate8k) 2 else 1)

                // Sherpa wants 16kHz. Repeat samples from an 8kHz SCO link 2x
                // to preserve timebase; this does not add missing bandwidth.
                val frame: FloatArray
                if (scoRate8k) {
                    frame = if (read == pcm.size) fullFrame else FloatArray(read * 2)
                    for (i in 0 until read) {
                        val v = pcm[i] / PCM16_FULL_SCALE
                        frame[2 * i] = v
                        frame[2 * i + 1] = v
                    }
                } else {
                    frame = if (read == pcm.size) fullFrame else FloatArray(read)
                    for (i in 0 until read) frame[i] = pcm[i] / PCM16_FULL_SCALE
                }
                s.acceptWaveform(frame, SAMPLE_RATE)

                val forcedEndpoint = finishRequested
                if (forcedEndpoint) {
                    finishRequested = false
                    // Flush decoder look-ahead without permanently closing the
                    // shared continuous stream's feature extractor.
                    s.acceptWaveform(FloatArray(SAMPLE_RATE / 2), SAMPLE_RATE)
                }

                val decodeStarted = System.nanoTime()
                while (rec.isReady(s)) rec.decode(s)
                currentCoroutineContext().ensureActive()
                decodeNanos += System.nanoTime() - decodeStarted
                val text = SpokenText.polish(rec.getResult(s).text)
                if (text.isNotBlank()) hadHypothesis = true
                val stablePreview = preview.update(text, System.nanoTime() / 1_000_000L)
                if (stablePreview != _partialResults.value) {
                    _partialResults.value = stablePreview
                }

                if (forcedEndpoint || rec.isEndpoint(s)) {
                    rec.reset(s)
                    signalGate.reset()
                    preview.reset()
                    if (text.isNotBlank() || forcedEndpoint || (forwardEndpointAudio && hadHypothesis)) {
                        val pcm = uttered.snapshot()
                        uttered.clear()
                        Log.i(TAG, "ASR endpoint ${AsrPcmSummary.from(pcm, SAMPLE_RATE)} " +
                            "decodeMs=${decodeNanos / 1_000_000L} characters=${text.length}")
                        if (forwardEndpointAudio || (forcedEndpoint && text.isBlank() && whisperRefiner == null)) {
                            withContext(Dispatchers.Main.immediate) {
                                if (running) { lastHeardPcm = pcm; onResult?.invoke(text) }
                            }
                        } else if (!sessionFinalizer.offer(text, pcm) && running) {
                            Log.w(TAG, "ASR refinement queue full")
                            withContext(Dispatchers.Main.immediate) {
                                onError?.invoke(android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
                            }
                        }
                    } else {
                        uttered.clear()
                        withContext(Dispatchers.Main.immediate) {
                            if (running && !finalizationPending) { lastHeardPcm = ShortArray(0); onResult?.invoke("") }
                        }
                    }
                    decodeNanos = 0L
                    hadHypothesis = false
                    _partialResults.value = ""
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (running) {
                Log.w(TAG, "ASR capture loop failed", e)
                withContext(Dispatchers.Main.immediate) {
                    onError?.invoke(android.speech.SpeechRecognizer.ERROR_AUDIO)
                }
            }
        } finally {
            sessionFinalizer.close()
            if (finalizer === sessionFinalizer) finalizer = null
            runCatching { effectiveRecord.stop() }
            runCatching { effectiveRecord.release() }
            if (scoStarted) {
                scoStarted = false
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                runCatching {
                    am?.clearCommunicationDevice()
                    am?.stopBluetoothSco()
                    am?.isBluetoothScoOn = false
                }
            }
            runCatching { stream?.release() }
        }
    }

    /**
     * Transcribes a finished PCM buffer with the cached streaming recognizer.
     *
     * This is how a one-breath command survives the wake handoff: the keyword
     * spotter cannot produce text, so everything said around the wake phrase
     * lives only in its pre-roll. Decoding that buffer here recovers the words
     * instead of losing the first syllables to recognizer startup.
     *
     * Returns null when the ASR models are not installed or decoding failed.
     * MUST NOT be called while this engine's capture loop is running — the
     * caller stops it first (wake standby and always-on never hold the mic at
     * once, and the spotter has already released AudioRecord by wake time).
     */
    suspend fun transcribePcm(pcm: ShortArray): String? {
        // Never wait behind an active open-mic session for a pre-roll request.
        if (destroyed || pcm.isEmpty() || running) return null
        return captureMutex.withLock {
            if (destroyed || pcm.isEmpty()) return@withLock null
            if (running || captureJob?.isCompleted == false) return@withLock null
            if (!VoiceModelStore(context).asrReady()) return@withLock null
            val rec = recognizer ?: withContext(Dispatchers.IO) {
                runCatching { buildRecognizer() }.getOrElse {
                    Log.w(TAG, "pre-roll ASR init failed", it)
                    null
                }?.also { recognizer = it }
            } ?: return@withLock null
            recognizer = rec
            withContext(Dispatchers.Default) {
                runCatching {
                    val s = rec.createStream()
                    if (s.ptr == 0L) {
                        Log.w(TAG, "pre-roll createStream failed")
                        return@runCatching null
                    }
                    try {
                        val frame = FloatArray(pcm.size)
                        for (i in pcm.indices) frame[i] = pcm[i] / PCM16_FULL_SCALE
                        s.acceptWaveform(frame, SAMPLE_RATE)
                        // Flush the tail: without inputFinished the last chunk can
                        // sit undecoded and the transcript loses trailing words.
                        s.inputFinished()
                        while (rec.isReady(s)) rec.decode(s)
                        SpokenText.polish(rec.getResult(s).text)
                    } finally {
                        runCatching { s.release() }
                    }
                }.getOrElse {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "pre-roll transcription failed", it)
                    null
                }
            }
        }
    }

    private fun buildRecognizer(): OnlineRecognizer {
        val store = VoiceModelStore(context)
        val pack = store.resolvedAsr()
            ?: error("ASR pack not ready")
        if (!pack.encoder.contains("int8")) {
            error("refusing non-int8 ASR encoder ${pack.encoder}")
        }
        val dir = File(store.root, pack.dir).absolutePath
        Log.i(TAG, "loading ASR pack ${pack.dir} (${pack.modelType})")
        return OnlineRecognizer(
            assetManager = null,
            config = OnlineRecognizerConfig(
                featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$dir/${pack.encoder}",
                        decoder = "$dir/${pack.decoder}",
                        joiner = "$dir/${pack.joiner}",
                    ),
                    tokens = "$dir/${pack.tokens}",
                    modelType = pack.modelType,
                    numThreads = 3,
                ),
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 3.2f, 0.0f),
                    // Trailing silence before a turn ends. 2.4s made every
                    // always-on reply feel ~2.5s late — the endpoint fired long
                    // after the last word and the model only started then.
                    // Keep a natural breath; local final results, never a
                    // provisional preview, own the completed utterance.
                    rule2 = EndpointRule(true, 1.6f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, AsrCaptureBounds.MAX_ENDPOINT_SECONDS.toFloat()),
                ),
                enableEndpoint = true,
                decodingMethod = "modified_beam_search",
                maxActivePaths = 8,
            ),
        )
    }

    companion object {
        private const val TAG = "SherpaAsr"
        private const val SAMPLE_RATE = AsrCaptureBounds.SAMPLE_RATE
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 10
        private const val PCM16_FULL_SCALE = 32_768.0f
    }
}
