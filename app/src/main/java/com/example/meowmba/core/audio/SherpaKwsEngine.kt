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
import kotlinx.coroutines.withTimeoutOrNull

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
    /**
     * Optional "is that you?" gate. When enrolled, a wake is only reported if the
     * pre-roll audio matches an enrolled voice. Fails open — see [SpeakerVerifier].
     */
    private val verifier: SpeakerVerifier? = null,
    /**
     * Optional speech gate. When present, the keyword model only runs on audio that a
     * VAD thinks contains speech, which is the two-stage VAD -> KWS design real
     * assistants use. Costs battery to skip, not accuracy — see the lead-in note below.
     */
    private val speechGate: VadGate? = null,
) : WakeWordEngine {

    private val _isRunning = MutableStateFlow(false)
    override val isRunning = _isRunning.asStateFlow()

    override var onWake: ((command: String?) -> Unit)? = null

    /** Raised when the model or microphone could not be brought up at all. */
    var onFatalError: ((String) -> Unit)? = null

    /** Fired when a wake phrase was heard but the voice did not match an enrolled one. */
    var onWakeRejected: (() -> Unit)? = null

    /**
     * Fired when the microphone became unavailable — almost always because the app went to
     * the background, since mic access here is foreground-only.
     *
     * Distinct from [onFatalError] on purpose: this is temporary and expected, so it must
     * NOT disable the wake phrase. Treating it as fatal meant every screen-off left the
     * user with "Wake phrase stopped" and a switch they had to flip back on.
     */
    var onMicUnavailable: (() -> Unit)? = null

    /**
     * Diagnostic: fired when loud audio ran for ~2s without ANY keyword firing. The
     * payload is the pre-roll snapshot — what the mic heard while the wake word
     * "didn't work" — so it can be transcribed offline and the miss root-caused.
     */
    var onLoudNoWake: ((pcm: ShortArray) -> Unit)? = null

    /**
     * The audio immediately preceding and including the wake phrase, oldest-first, 16 kHz
     * mono PCM16. Read this on wake to recover a command spoken in the same breath.
     */
    @Volatile
    var lastPreRoll: ShortArray = ShortArray(0)
        private set

    /**
     * Drops the buffered pre-roll. Callers that have already consumed it
     * (transcribed into a command) must clear it — otherwise a spotter re-fire
     * on trailing audio hands the SAME buffer to the wake handler again and
     * the same command commits twice.
     */
    fun clearPreRoll() {
        lastPreRoll = ShortArray(0)
    }

    private var captureJob: Job? = null
    private var spotter: KeywordSpotter? = null
    private var dumpSeq = 0

    /** True while the engine intends to be capturing. */
    @Volatile
    private var running = false

    fun updateKeywords(tokenised: String) {
        keywords = tokenised
    }

    // Synchronized for the same reason as SherpaAsrEngine.start: the check and
    // the launch must be one step, or two racing callers each start a loop.
    @Synchronized
    override fun start() {
        // [stop] clears [running] immediately but the previous captureLoop may
        // still hold AudioRecord until the next 100ms read + finally. Starting
        // a second loop on top of that is two engines, SpeechRecognizer BUSY,
        // and "start() ignored" forever after. Treat a live job as still running.
        if (running || captureJob?.isActive == true) {
            Log.i(TAG, "start() ignored: already running")
            return
        }
        running = true
        _isRunning.value = true
        captureJob = scope.launch(Dispatchers.Default) { captureLoop() }
    }

    override fun stop() {
        running = false
        _isRunning.value = false
        captureJob?.cancel()
        // Do not null [captureJob] here. [start] must still see the live job
        // until [stopAndAwait] (or the loop's finally) finishes releasing the mic.
    }

    override suspend fun stopAndAwait(timeoutMs: Long) {
        stop()
        val job = captureJob ?: return
        withTimeoutOrNull(timeoutMs) { job.join() }
        if (!job.isActive) captureJob = null
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
            yieldMic()
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
            // Another app very likely holds it; that resolves on its own.
            yieldMic()
            return
        }

        val preRoll = AudioPreRoll.forSeconds(PRE_ROLL_SECONDS, SAMPLE_RATE)
        var stream: OnlineStream? = null

        // Set when a wake is accepted. The handoff deliberately happens AFTER the finally
        // block below, never from inside the loop — see the note at the invoke site.
        var wakeAccepted = false

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
            var silentFrames = 0
            // Missed-wake diagnostic: set on the first loud frame, see the check below.
            var loudAtFrame = -1L

            while (running && scope.isActive && !wakeAccepted) {
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

                // Missed-wake diagnostic: ~2s of continuous loud audio with no keyword
                // hit means the user almost certainly spoke and the spotter did not
                // fire. Hand the buffered audio up for offline transcription. Fires at
                // most once per loud event; a hit below also re-arms it.
                if (peak > LOUD_PEAK_THRESHOLD) {
                    if (loudAtFrame < 0) loudAtFrame = frames
                    if (frames - loudAtFrame >= MISS_DUMP_DELAY_FRAMES) {
                        Log.i(TAG, "loud speech without wake; dumping pre-roll for diagnosis")
                        val dump = preRoll.snapshot()
                        try {
                            val dir = context.getExternalFilesDir(null)
                            if (dir != null) {
                                val wav = java.io.File(dir, "kws_preroll_${dumpSeq++}.wav")
                                java.io.DataOutputStream(java.io.FileOutputStream(wav)).use { d ->
                                    val bytes = ByteArray(44 + dump.size * 2)
                                    fun w(p: Int, v: Int) {
                                        bytes[p] = (v and 0xff).toByte(); bytes[p + 1] = ((v shr 8) and 0xff).toByte()
                                    }
                                    bytes[0] = 'R'.code.toByte(); bytes[1] = 'I'.code.toByte(); bytes[2] = 'F'.code.toByte(); bytes[3] = 'F'.code.toByte()
                                    w(4, bytes.size - 8); bytes[8] = 'W'.code.toByte(); bytes[9] = 'A'.code.toByte(); bytes[10] = 'V'.code.toByte(); bytes[11] = 'E'.code.toByte()
                                    bytes[12] = 'f'.code.toByte(); bytes[13] = 'm'.code.toByte(); bytes[14] = 't'.code.toByte(); bytes[15] = ' '.code.toByte()
                                    w(16, 16); w(20, 1); w(22, 1); w(24, 16000); w(28, 32000); w(32, 2); w(34, 16)
                                    bytes[36] = 'd'.code.toByte(); bytes[37] = 'a'.code.toByte(); bytes[38] = 't'.code.toByte(); bytes[39] = 'a'.code.toByte()
                                    w(40, dump.size * 2)
                                    for ((i, v) in dump.withIndex()) {
                                        bytes[44 + i * 2] = (v.toInt() and 0xff).toByte(); bytes[45 + i * 2] = ((v.toInt() shr 8) and 0xff).toByte()
                                    }
                                    d.write(bytes)
                                }
                                Log.i(TAG, "pre-roll dumped to ${wav.absolutePath} (${dump.size} samples)")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "pre-roll dump failed", e)
                        }
                        onLoudNoWake?.invoke(dump)
                        loudAtFrame = -1L
                    }
                } else {
                    loudAtFrame = -1L
                }

                // Digital silence is not quiet — it means we no longer have the mic.
                // Android grants this app mic access foreground-only, so once the app is
                // backgrounded without a microphone foreground service, AudioRecord keeps
                // succeeding and returns nothing but zeros. Measured 440s of peak=0.000
                // while another app held the foreground, with every log line claiming to
                // be listening. Give the mic up so a rightful owner can take it.
                if (peak == 0) {
                    if (++silentFrames >= SILENT_FRAMES_BEFORE_YIELD) {
                        Log.w(TAG, "no audio for ${silentFrames / 10}s — mic lost, yielding")
                        yieldMic()
                        return
                    }
                } else {
                    silentFrames = 0
                }
                if (++frames % HEALTH_LOG_FRAMES == 0L) {
                    Log.d(
                        TAG,
                        "listening: ${frames / 10}s, peak=${"%.3f".format(peakSinceLog / PCM16_FULL_SCALE)}"
                    )
                    peakSinceLog = 0
                }

                val frame = FloatArray(read)
                for (i in 0 until read) frame[i] = pcm[i] / PCM16_FULL_SCALE

                // VAD gate. Skipping silence is the whole battery win, but a naive gate
                // clips the START of the phrase: the VAD needs a moment of speech before
                // it flips, by which time "yo" is already gone. So on each rising edge the
                // buffered lead-in is replayed into the spotter first, and nothing is lost.
                if (speechGate != null) {
                    when (speechGate.classify(frame)) {
                        VadGate.Decision.SILENT -> continue
                        VadGate.Decision.SPEECH_STARTED ->
                            speechGate.leadIn()?.let { s.acceptWaveform(it, SAMPLE_RATE) }
                        VadGate.Decision.SPEAKING -> Unit
                    }
                }

                s.acceptWaveform(frame, SAMPLE_RATE)

                // The result is checked INSIDE the decode loop, as in sherpa's demo: the
                // keyword is reported by the decode step that detects it, and reset()
                // must follow immediately.
                while (kws.isReady(s)) {
                    kws.decode(s)
                    val hit = kws.getResult(s).keyword
                    if (hit.isNotBlank()) {
                        Log.i(TAG, "wake phrase detected: $hit")
                        kws.reset(s)
                        loudAtFrame = -1L
                        val captured = preRoll.snapshot()
                        lastPreRoll = captured
                        preRoll.clear()

                        // "Is that you?" — checked on the pre-roll, which contains the
                        // phrase itself. Fails open when nothing is enrolled.
                        val v = verifier
                        if (v != null && v.isActive && !v.verifyPcm(captured)) {
                            withContext(Dispatchers.Main) { onWakeRejected?.invoke() }
                        } else {
                            // Leave the loop so the mic is actually freed before anyone
                            // tries to use it.
                            wakeAccepted = true
                            running = false
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (running) Log.w(TAG, "Wake capture loop failed", e)
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
            runCatching { stream?.release() }
            // `running` MUST be cleared here, not on the success path. It is the
            // guard `start()` checks, so any throw out of the capture loop (mic
            // stolen, AudioRecord dead after a route change) used to leave it
            // latched true with the mic already released: every later start()
            // logged "already running" and returned, and the wake word never
            // came back for the life of the process. The finally block is the
            // only exit every path takes.
            running = false
            _isRunning.value = false
            captureJob = null
        }

        if (wakeAccepted) {
            // Two things that both broke the command turn if ignored:
            //
            // 1. This engine holds AudioRecord continuously. Invoking onWake from inside
            //    the loop meant the SpeechRecognizer for the command started while the mic
            //    was STILL open here, so it received nothing — "not picking up voice after
            //    the wake word". The old recognizer-loop design hid this, because stopping
            //    it freed the mic implicitly.
            // 2. The loop runs on Dispatchers.Default, and SpeechRecognizer must be driven
            //    from the main thread.
            //
            // So: exit the loop, let the finally above release the mic, THEN hand off on
            // the main thread.
            withContext(Dispatchers.Main) { onWake?.invoke(null) }
        }
    }

    private fun buildSpotter(): KeywordSpotter {
        val store = VoiceModelStore(context)
        val fromDisk = store.kwsReady()
        val dir = if (fromDisk) store.kwsDir.absolutePath else modelDir
        return KeywordSpotter(
            assetManager = if (fromDisk) null else context.assets,
            config = KeywordSpotterConfig(
                featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$dir/$ENCODER",
                        decoder = "$dir/$DECODER",
                        joiner = "$dir/$JOINER",
                    ),
                    tokens = "$dir/tokens.txt",
                    modelType = "zipformer2",
                    numThreads = 1,
                ),
                keywordsFile = "$dir/$KEYWORDS_FILE",
                keywordsScore = KEYWORDS_SCORE,
                keywordsThreshold = KEYWORDS_THRESHOLD,
            ),
        )
    }

    /** Steps down quietly, without telling the user anything is broken. */
    private fun yieldMic() {
        running = false
        _isRunning.value = false
        onMicUnavailable?.invoke()
    }

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
        fun assetsPresent(context: Context, modelDir: String = DEFAULT_MODEL_DIR): Boolean {
            if (VoiceModelStore(context).kwsReady()) return true
            return listOf(ENCODER, DECODER, JOINER, TOKENS, KEYWORDS_FILE).all { name ->
                try {
                    context.assets.open("$modelDir/$name").close()
                    true
                } catch (e: Exception) {
                    false
                }
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

        /**
         * Frames of pure digital silence tolerated before concluding the mic is gone.
         * 15s: long enough that a genuinely dead-quiet room with an aggressive noise gate
         * does not trip it, short enough that a lost mic does not go unnoticed for
         * minutes.
         */
        private const val SILENT_FRAMES_BEFORE_YIELD = 150

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

        /** Peak (0..32768) above which a frame counts as loud speech/noise. */
        private const val LOUD_PEAK_THRESHOLD = 4_096

        /** ~0.6s of continuous loud audio without a hit triggers the missed-wake dump. */
        private const val MISS_DUMP_DELAY_FRAMES = 6L
    }
}
