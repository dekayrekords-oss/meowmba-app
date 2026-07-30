package com.example.meowmba.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
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

/**
 * Wake-word detection with a real on-device keyword spotter (sherpa-onnx), replacing the
 * restart-on-complete SpeechRecognizer loop.
 *
 * Why this exists — every one of these was a measured bug in the recognizer-loop design:
 *
 *  - **No session churn.** A single [AudioRecord] runs continuously instead of thousands
 *    of short recognition sessions, so there is no ERROR_RECOGNIZER_BUSY contention
 *    (measured 30 in 45s at worst) and no ~380ms deaf gap between sessions.
 *  - **Nothing is discarded.** A recognizer session ending in ERROR_NO_MATCH delivered no
 *    transcript at all, throwing away whatever was said in it. Audio here is scanned
 *    frame by frame; there is no session to fail.
 *  - **Media keeps playing.** AudioRecord does not request audio focus. The old path let
 *    the recognition service take AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE with USAGE_MEDIA on
 *    every session, which paused the user's music indefinitely.
 *
 * The spotter reports only that a keyword fired — it returns no transcript. So words
 * spoken after the wake phrase live only in the audio, which is what [AudioPreRoll] is
 * for: the caller can replay them rather than clipping a one-breath command.
 *
 * Keywords are supplied as tokenised text at stream creation, so renaming the companion
 * needs no retraining and no new model.
 */
class SherpaKwsEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    /**
     * Tokenised keyword lines, one per phrase, in the model's token vocabulary.
     * See [KwsKeywords].
     */
    private var keywords: String,
    /** Assets-relative directory holding encoder/decoder/joiner/tokens. */
    private val modelDir: String = DEFAULT_MODEL_DIR,
) : WakeWordEngine {

    private val _isRunning = MutableStateFlow(false)
    override val isRunning = _isRunning.asStateFlow()

    override var onWake: ((command: String?) -> Unit)? = null

    /** Raised when the model or microphone could not be brought up at all. */
    var onFatalError: ((String) -> Unit)? = null

    /**
     * The audio immediately preceding and including the wake phrase, oldest-first, 16 kHz
     * mono PCM16. Read this on wake to recover a command spoken in the same breath.
     */
    @Volatile
    var lastPreRoll: ShortArray = ShortArray(0)
        private set

    private var captureJob: Job? = null
    private var spotter: KeywordSpotter? = null

    /** True while the engine intends to be capturing. */
    @Volatile
    private var running = false

    fun updateKeywords(tokenised: String) {
        keywords = tokenised
    }

    override fun start() {
        if (running) return
        running = true
        _isRunning.value = true
        captureJob = scope.launch(Dispatchers.Default) { captureLoop() }
    }

    override fun stop() {
        running = false
        _isRunning.value = false
        captureJob?.cancel()
        captureJob = null
    }

    /** Frees the native model. The engine cannot be restarted afterwards. */
    fun release() {
        stop()
        runCatching { spotter?.release() }
        spotter = null
    }

    private suspend fun captureLoop() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            fail("Microphone permission is not granted, so I can't listen for the wake phrase.")
            return
        }

        val kws = spotter ?: withContext(Dispatchers.IO) {
            runCatching { buildSpotter() }.getOrElse {
                Log.w(TAG, "Keyword spotter init failed", it)
                null
            }
        }
        if (kws == null) {
            fail("The wake-word model could not be loaded.")
            return
        }
        spotter = kws

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            fail("This device reported no usable audio input buffer.")
            return
        }
        // Generous buffer: a stall in the model must not drop frames on the floor.
        val bufBytes = maxOf(minBuf * 4, SAMPLE_RATE)  // >= ~0.5s of PCM16

        val record = try {
            AudioRecord(
                // VOICE_RECOGNITION applies far less aggressive processing than
                // MIC/VOICE_COMMUNICATION, which is what the spotter's features expect.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, CHANNEL, ENCODING, bufBytes,
            )
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord construction failed", e)
            null
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record?.release() }
            fail("The microphone could not be opened for wake-word listening.")
            return
        }

        val preRoll = AudioPreRoll.forSeconds(PRE_ROLL_SECONDS, SAMPLE_RATE)
        var stream: OnlineStream? = null

        try {
            record.startRecording()

            // Per-stream keywords are '/'-separated, NOT newline-separated — that is what
            // sherpa's own Android demo does, and passing newlines makes createStream
            // reject the whole string. It signals that by returning a NULL handle rather
            // than throwing, so the loop then runs forever against a dead stream: audio
            // flowing, mic hot, and not one detection. Check ptr, and fall back to the
            // keywords bundled in the model directory.
            val inlineKeywords = keywords.trim().replace("\n", "/")
            var s = kws.createStream(inlineKeywords)
            if (s.ptr == 0L) {
                Log.w(TAG, "createStream rejected keywords [$inlineKeywords]; using bundled keywords.txt")
                runCatching { s.release() }
                s = kws.createStream("")
            }
            if (s.ptr == 0L) {
                fail("The wake phrases could not be loaded into the spotter.")
                return
            }
            stream = s
            Log.i(TAG, "keyword spotter listening; keywords=[$inlineKeywords]")
            val pcm = ShortArray(CHUNK_SAMPLES)
            val floats = FloatArray(CHUNK_SAMPLES)
            // Periodic health line. Without it there is no way to tell "listening but
            // hearing silence" from "listening and the model just is not matching" —
            // both look identical from outside.
            var frames = 0L
            var peakSinceLog = 0

            while (running && scope.isActive) {
                val read = record.read(pcm, 0, pcm.size)
                if (read <= 0) {
                    // A transient underrun; keep going rather than tearing the mic down.
                    continue
                }
                preRoll.append(pcm, read)

                var peak = 0
                for (i in 0 until read) {
                    val a = if (pcm[i] < 0) -pcm[i].toInt() else pcm[i].toInt()
                    if (a > peak) peak = a
                }
                if (peak > peakSinceLog) peakSinceLog = peak
                if (++frames % HEALTH_LOG_FRAMES == 0L) {
                    Log.d(
                        TAG,
                        "listening: ${frames / 10}s, peak=${"%.3f".format(peakSinceLog / PCM16_FULL_SCALE)}"
                    )
                    peakSinceLog = 0
                }

                for (i in 0 until read) floats[i] = pcm[i] / PCM16_FULL_SCALE
                s.acceptWaveform(floats.copyOf(read), SAMPLE_RATE)

                // The result is checked INSIDE the decode loop, as in sherpa's demo: the
                // keyword is reported by the decode step that detects it, and reset()
                // must follow immediately.
                while (kws.isReady(s)) {
                    kws.decode(s)
                    val hit = kws.getResult(s).keyword
                    if (hit.isNotBlank()) {
                        Log.i(TAG, "wake phrase detected: $hit")
                        kws.reset(s)
                        lastPreRoll = preRoll.snapshot()
                        preRoll.clear()
                        // The spotter reports no transcript; the caller starts a listening
                        // turn (and may replay lastPreRoll).
                        onWake?.invoke(null)
                    }
                }
            }
        } catch (e: Exception) {
            if (running) Log.w(TAG, "Wake capture loop failed", e)
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
            runCatching { stream?.release() }
        }
    }

    private fun buildSpotter(): KeywordSpotter = KeywordSpotter(
        assetManager = context.assets,
        config = KeywordSpotterConfig(
            featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelDir/$ENCODER",
                    decoder = "$modelDir/$DECODER",
                    joiner = "$modelDir/$JOINER",
                ),
                tokens = "$modelDir/tokens.txt",
                modelType = "zipformer2",
                numThreads = 1,
            ),
            // A real keywords file is REQUIRED. An empty string is not "no file" — the
            // native loader tries to read it and aborts the process
            // ("F sherpa-onnx: Read binary file: Load '' failed"). The bundled file holds
            // the built-in phrases; a custom companion name is added per-stream via
            // createStream(keywords), which layers on top of it.
            keywordsFile = "$modelDir/$KEYWORDS_FILE",
            keywordsScore = KEYWORDS_SCORE,
            keywordsThreshold = KEYWORDS_THRESHOLD,
        ),
    )

    private fun fail(message: String) {
        running = false
        _isRunning.value = false
        onFatalError?.invoke(message)
    }

    companion object {
        private const val TAG = "SherpaKws"

        const val DEFAULT_MODEL_DIR = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
        // int8 quantised variants: ~5MB total instead of ~13.6MB, which matters in an
        // APK. These names MUST match what is actually in assets — see the pre-flight
        // check in [assetsPresent], because a missing model file does not throw, it
        // aborts the whole process from native code.
        const val ENCODER = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        const val DECODER = "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        const val JOINER = "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        const val TOKENS = "tokens.txt"
        const val KEYWORDS_FILE = "keywords.txt"

        /**
         * Every asset the native spotter will try to open.
         *
         * Verified BEFORE constructing [KeywordSpotter] on purpose: sherpa-onnx treats a
         * failed model load as fatal and kills the process
         * ("F sherpa-onnx: Read binary file: ... failed"), so runCatching cannot rescue
         * it and there is no fallback to fall back to. Observed exactly that when the
         * filenames did not match what was bundled.
         */
        fun assetsPresent(context: Context, modelDir: String = DEFAULT_MODEL_DIR): Boolean =
            listOf(ENCODER, DECODER, JOINER, TOKENS, KEYWORDS_FILE).all { name ->
                try {
                    context.assets.open("$modelDir/$name").close()
                    true
                } catch (e: Exception) {
                    false
                }
            }

        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** 100 ms per read: responsive without waking the CPU excessively. */
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 10

        private const val PCM16_FULL_SCALE = 32_768.0f

        /** One health line per 5s of audio (100ms frames). */
        private const val HEALTH_LOG_FRAMES = 50L

        /** Enough for the wake phrase plus the start of a one-breath command. */
        private const val PRE_ROLL_SECONDS = 2.5f

        /**
         * Detection sensitivity. Raising the score or lowering the threshold catches more
         * wake phrases at the cost of more false wakes — the false-accept/false-reject
         * dial. sherpa's defaults are 1.5 / 0.25; nudged toward recall because a missed
         * wake is the complaint being fixed.
         */
        private const val KEYWORDS_SCORE = 2.0f
        private const val KEYWORDS_THRESHOLD = 0.20f
    }
}
