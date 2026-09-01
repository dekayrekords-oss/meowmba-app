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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
) : SpeechEngine {

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
    private var captureJob: Job? = null

    @Volatile
    private var running = false

    @Volatile
    var lastHeardPcm: ShortArray = ShortArray(0)
        private set

    /**
     * Optional local-Whisper refiner. When set, a finished utterance is also
     * transcribed by Whisper and the FINAL text is replaced with Whisper's —
     * the zipformer still drives the live partials and endpointing.
     *
     * This is the whole point of the Whisper backend: the streaming zipformer
     * is fast but measurably mishears, and Whisper is a batch model that
     * cannot stream. Hybrid = live feedback stays instant, the answered text
     * is the accurate one. Null (the default) keeps today's behaviour exactly.
     */
    @Volatile
    var whisperRefiner: WhisperAsrEngine? = null

    override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
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
        stopListening()
        runCatching { recognizer?.release() }
        recognizer = null
    }

    private fun start() {
        if (running || captureJob?.isActive == true) return
        running = true
        _isListening.value = true
        captureJob = scope.launch(Dispatchers.Default) { captureLoop() }
    }

    fun stop() {
        running = false
        _isListening.value = false
        captureJob?.cancel()
    }

    suspend fun stopAndAwait(timeoutMs: Long = 800L) {
        stop()
        val job = captureJob ?: return
        withTimeoutOrNull(timeoutMs) { job.join() }
        if (!job.isActive) captureJob = null
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
            }
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
                    Log.i(TAG, "Bluetooth SCO on '${scoDevice.productName}' for glasses mic")
                }.onFailure { Log.w(TAG, "SCO start failed", it) }
            } else {
                Log.w(TAG, "bluetoothSco requested but no SCO device connected — using built-in path")
            }
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            running = false
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
            running = false
            _isListening.value = false
            return
        }

        var stream: OnlineStream? = null
        val uttered = ArrayList<Short>(SAMPLE_RATE * 8)
        try {
            effectiveRecord.startRecording()
            val s = rec.createStream()
            if (s.ptr == 0L) {
                Log.w(TAG, "createStream failed")
                return
            }
            stream = s
            Log.i(TAG, "always-on ASR listening")
            val pcm = ShortArray(CHUNK_SAMPLES)

            while (running && scope.isActive) {
                val read = effectiveRecord.read(pcm, 0, pcm.size)
                if (read <= 0) continue
                for (i in 0 until read) uttered.add(pcm[i])
                if (uttered.size > SAMPLE_RATE * 20) {
                    uttered.subList(0, uttered.size - SAMPLE_RATE * 12).clear()
                }

                // Sherpa wants 16kHz. An 8kHz SCO link is linearly upsampled
                // 2x — cheap and adequate for recognition.
                val frame: FloatArray
                if (scoRate8k) {
                    frame = FloatArray(read * 2)
                    for (i in 0 until read) {
                        val v = pcm[i] / PCM16_FULL_SCALE
                        frame[2 * i] = v
                        frame[2 * i + 1] = v
                    }
                } else {
                    frame = FloatArray(read)
                    for (i in 0 until read) frame[i] = pcm[i] / PCM16_FULL_SCALE
                }
                s.acceptWaveform(frame, SAMPLE_RATE)

                while (rec.isReady(s)) rec.decode(s)
                val text = SpokenText.polish(rec.getResult(s).text)
                if (text.isNotEmpty() && text != _partialResults.value) {
                    _partialResults.value = text
                }

                if (rec.isEndpoint(s)) {
                    rec.reset(s)
                    if (text.isNotBlank()) {
                        val pcm = uttered.toShortArray()
                        lastHeardPcm = pcm
                        uttered.clear()
                        Log.i(TAG, "always-on ASR: \"$text\"")
                        val deliver = onResult
                        val refiner = whisperRefiner
                        if (refiner != null) {
                            // Refine off the capture thread, then deliver on
                            // main. Launch, don't withContext: processUserSpeech
                            // cancels in-flight Ktor jobs, and those cancel
                            // handlers can throw (OkHttp Unbalanced
                            // enter/exit). That must not tear down this capture
                            // loop or crash the main thread as a
                            // CompletionHandlerException.
                            scope.launch(Dispatchers.Default) {
                                val refined = runCatching { refiner.transcribe(pcm) }
                                    .onFailure { Log.w(TAG, "whisper refine failed", it) }
                                    .getOrNull()
                                    ?.takeIf { SpokenText.isUsable(it) }
                                if (refined != null && refined != text) {
                                    Log.i(TAG, "whisper refined: \"$text\" -> \"$refined\"")
                                }
                                withContext(Dispatchers.Main.immediate) {
                                    runCatching { deliver?.invoke(refined ?: text) }
                                }
                            }
                        } else {
                            // Launch, don't withContext: processUserSpeech cancels
                            // in-flight Ktor jobs, and those cancel handlers can
                            // throw (OkHttp Unbalanced enter/exit). That must not
                            // tear down this capture loop or crash the main thread
                            // as a CompletionHandlerException.
                            scope.launch(Dispatchers.Main.immediate) {
                                runCatching { deliver?.invoke(text) }
                            }
                        }
                    } else {
                        uttered.clear()
                    }
                    _partialResults.value = ""
                }
            }
        } catch (e: Exception) {
            if (running) Log.w(TAG, "ASR capture loop failed", e)
        } finally {
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
            running = false
            _isListening.value = false
            captureJob = null
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
        if (pcm.isEmpty()) return null
        if (running || captureJob?.isActive == true) return null
        if (!VoiceModelStore(context).asrReady()) return null
        val rec = recognizer ?: withContext(Dispatchers.IO) {
            runCatching { buildRecognizer() }.getOrElse {
                Log.w(TAG, "pre-roll ASR init failed", it)
                null
            }
        } ?: return null
        recognizer = rec
        return withContext(Dispatchers.Default) {
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
                Log.w(TAG, "pre-roll transcription failed", it)
                null
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
                    // 1.6s still clears a natural breath; the ViewModel's
                    // partial-stability commit (900ms + looksFinished) usually
                    // wins the race anyway.
                    rule2 = EndpointRule(true, 1.6f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 30.0f),
                ),
                enableEndpoint = true,
                decodingMethod = "modified_beam_search",
                maxActivePaths = 8,
            ),
        )
    }

    companion object {
        private const val TAG = "SherpaAsr"
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 10
        private const val PCM16_FULL_SCALE = 32_768.0f
    }
}
