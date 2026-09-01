package com.example.meowmba.core.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local Whisper (base.en, int8) batch transcriber.
 *
 * Why this exists: the streaming zipformer in [SherpaAsrEngine] trades
 * accuracy for latency and measurably mishears — its own KDoc says so, and
 * the on-device transcripts it produced were largely unusable ("Oh low",
 * "Mused dick", "Probably better up"). Whisper is a *batch* model: no live
 * partials, but materially more accurate, and it runs entirely on-device —
 * no network, no API key, nothing leaves the phone.
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

    fun isAvailable(): Boolean = VoiceModelStore(context).whisperReady()

    /**
     * Transcribe a complete 16 kHz mono PCM16 buffer.
     *
     * Loading ~160MB of weights is slow, so the recognizer is built once and
     * reused; the heavy work runs on [Dispatchers.Default] — never the main
     * thread (a multi-second block there is what froze the whole app).
     */
    suspend fun transcribe(pcm: ShortArray): String? {
        if (pcm.isEmpty()) return null
        if (!isAvailable()) return null

        val rec = recognizer ?: withContext(Dispatchers.IO) {
            runCatching { buildRecognizer() }.getOrElse {
                Log.w(TAG, "whisper init failed", it)
                null
            }
        } ?: return null
        recognizer = rec

        return withContext(Dispatchers.Default) {
            runCatching {
                val stream = rec.createStream()
                try {
                    val frame = FloatArray(pcm.size)
                    for (i in pcm.indices) frame[i] = pcm[i] / PCM16_FULL_SCALE
                    stream.acceptWaveform(frame, SAMPLE_RATE)
                    rec.decode(stream)
                    SpokenText.polish(rec.getResult(stream).text)
                } finally {
                    runCatching { stream.release() }
                }
            }.getOrElse {
                Log.w(TAG, "whisper transcription failed", it)
                null
            }
        }
    }

    private fun buildRecognizer(): OfflineRecognizer {
        val dir = VoiceModelStore(context).whisperDir.absolutePath
        Log.i(TAG, "loading whisper from $dir")
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
