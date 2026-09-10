package com.example.meowmba.core.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Local Whisper (base.en, int8) batch transcriber.
 *
 * The streaming zipformer in [SherpaAsrEngine] supplies provisional text;
 * Whisper reconsiders the complete captured utterance. It is a batch model,
 * not a live stream, and can also make mistakes. Recognition accuracy needs
 * a real reference recording rather than an inference from a model's name.
 * It runs entirely on-device: no network, API key, or off-device audio.
 *
 * No new native dependency: the shipped libsherpa-onnx-jni.so already
 * contains Whisper and the full OfflineRecognizer JNI API (verified —
 * offline-whisper-model.cc plus Java_..._OfflineRecognizer_* symbols are
 * present in the .so), so this is model weights + Kotlin bindings only.
 *
 * Trade-off, stated plainly: Whisper cannot stream. It transcribes a finished
 * utterance, so the live partial text still comes from the zipformer and the
 * final text is replaced by Whisper's. That is the same hybrid the
 * cloud Gemini backend uses (on-device endpointing, better model for the
 * final text), just without the network.
 */
class WhisperAsrEngine(private val context: Context) {

    @Volatile
    private var recognizer: OfflineRecognizer? = null
    private val decodeMutex = Mutex()

    fun isAvailable(): Boolean = VoiceModelStore(context).whisperReady()

    /**
     * Transcribe a complete 16 kHz mono PCM16 buffer.
     *
     * Loading ~160MB of weights is slow, so the recognizer is built once and
     * reused; the heavy work runs on [Dispatchers.Default] — never the main
     * thread (a multi-second block there is what froze the whole app).
     */
    suspend fun transcribe(pcm: ShortArray): String? = decodeMutex.withLock {
        coroutineContext.ensureActive()
        if (pcm.isEmpty()) return@withLock null
        if (!isAvailable()) return@withLock null
        if (pcm.all { it == 0.toShort() }) return@withLock ""

        val rec = recognizer ?: withContext(Dispatchers.IO) {
            runCatching { buildRecognizer() }.getOrElse {
                Log.w(TAG, "whisper init failed", it)
                null
            }?.also { recognizer = it }
        } ?: return@withLock null

        withContext(Dispatchers.Default) {
            coroutineContext.ensureActive()
            runCatching {
                val started = System.nanoTime()
                val stream = rec.createStream()
                try {
                    val frame = FloatArray(pcm.size)
                    for (i in pcm.indices) frame[i] = pcm[i] / PCM16_FULL_SCALE
                    stream.acceptWaveform(frame, SAMPLE_RATE)
                    rec.decode(stream)
                    // Legacy Zipformer word substitutions must not rewrite an
                    // independently decoded final. Presentation casing only.
                    SpokenText.normalize(rec.getResult(stream).text).also { text ->
                        Log.i(TAG, "Whisper final audioMs=${pcm.size.toLong() * 1000L / SAMPLE_RATE} " +
                            "decodeMs=${(System.nanoTime() - started) / 1_000_000L} characters=${text.length}")
                    }
                } finally {
                    runCatching { stream.release() }
                }
            }.getOrElse {
                if (it is CancellationException) throw it
                Log.w(TAG, "whisper transcription failed", it)
                null
            }
        }
    }

    private fun buildRecognizer(): OfflineRecognizer {
        val dir = VoiceModelStore(context).whisperDir.absolutePath
        Log.i(TAG, "loading local Whisper base.en int8, cpu, threads=4")
        return OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "$dir/${VoiceModelPack.WHISPER_ENCODER}",
                        decoder = "$dir/${VoiceModelPack.WHISPER_DECODER}",
                        language = "en",
                        task = "transcribe",
                    ),
                    tokens = "$dir/${VoiceModelPack.WHISPER_TOKENS}",
                    // Whisper is heavier than the streaming zipformer; give it
                    // real threads so a multi-second utterance decodes in a
                    // couple of seconds instead of ten.
                    numThreads = 4,
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            ),
        )
    }

    fun release() {
        runCatching { recognizer?.release() }
        recognizer = null
    }

    companion object {
        private const val TAG = "WhisperAsr"
        private const val SAMPLE_RATE = 16_000
        private const val PCM16_FULL_SCALE = 32_768.0f
    }
}
