package com.example.meowmba.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** One native model per app process. Never load one copy per voice. */
object LocalVoiceRuntime {
    val lastError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private var model: OfflineTts? = null

    /** True once the Kokoro graph is resident. Diagnostics and tests only. */
    @Synchronized
    fun isLoaded(): Boolean = model != null

    /**
     * Frees the resident Kokoro graph (300 MB+ of ONNX weights) and its native
     * session. Loading is lazy, so the next [generate] simply pays for it again.
     *
     * Deliberately NOT wired to backgrounding, idling or thermal tier. This app
     * is the home screen, so pause/resume happens every time the user opens
     * anything: unloading on each pause and reloading on each resume would
     * re-parse a 300 MB graph over and over, and that load spike is far more
     * heat than leaving the model resident. It is wired only to the explicit
     * "use system voice" switch, where the user has said they are done with it.
     */
    @Synchronized
    fun unload() {
        val tts = model ?: return
        model = null
        runCatching { tts.release() }
    }

    @Synchronized fun generate(context: Context, text: String, voice: LocalVoice,
        speed: Float = 1f, callback: ((FloatArray) -> Int)? = null): GeneratedAudio {
        check(LocalVoicePack(context).ready()) { "Download the local voice pack first" }
        val tts = model ?: run {
            val root = LocalVoicePack(context).directory.path
            OfflineTts(config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(kokoro = OfflineTtsKokoroModelConfig(
                    model = "$root/model.onnx", voices = "$root/voices.bin", tokens = "$root/tokens.txt",
                    dataDir = "$root/espeak-ng-data", lexicon = "$root/lexicon-us-en.txt", lang = "en-us"
                ), numThreads = 4), maxNumSentences = 1, silenceScale = 0.15f
            )).also { model = it }
        }
        return if (callback == null) tts.generate(text, voice.speaker, speed)
            else tts.generateWithCallback(text, voice.speaker, speed, callback)
    }
}

/** Sequential neural synthesis with streamed PCM playback and cancel-safe callbacks. */
class LocalNeuralSpeech(private val context: Context,
    private val started: () -> Unit, private val finished: () -> Unit,
    private val failed: (String) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val epoch = AtomicInteger()
    private val pending = AtomicInteger()
    @Volatile private var track: AudioTrack? = null
    @Volatile private var startedEpoch = -1
    fun isSpeaking() = pending.get() > 0

    fun enqueue(text: String, flush: Boolean, previewVoice: LocalVoice? = null, rate: Float = 1f, onStarted: (() -> Unit)? = null) {
        if (text.isBlank()) return
        if (flush) stop()
        val speed = VoiceExpression(rate = rate).bounded().rate
        val turn = epoch.get()
        val voice = previewVoice ?: LocalVoices.selected(context)
        pending.incrementAndGet()
        worker.execute {
            var player: AudioTrack? = null
            var written = 0L
            var reportedOutput = false
            try {
                if (turn != epoch.get()) return@execute
                LocalVoiceRuntime.generate(context, text, voice, speed = speed) { samples ->
                    if (turn != epoch.get()) return@generate 0
                    if (player == null) {
                        player = AudioTrack.Builder()
                            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                            .setBufferSizeInBytes(maxOf(24000, AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)))
                            .setTransferMode(AudioTrack.MODE_STREAM).build()
                        track = player
                        player!!.play()
                        if (startedEpoch != turn) {
                            startedEpoch = turn
                            main.post { if (epoch.get() == turn) started() }
                        }
                    }
                    var offset = 0
                    while (offset < samples.size && turn == epoch.get()) {
                        val n = player!!.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
                        check(n > 0) { "Local voice playback failed ($n)" }
                        offset += n
                        written += n
                        if (!reportedOutput) {
                            reportedOutput = true
                            main.post { if (epoch.get() == turn) onStarted?.invoke() }
                        }
                    }
                    if (turn == epoch.get()) 1 else 0
                }
                check(written > 0 || turn != epoch.get()) { "Local voice returned no audio" }
                val deadline = android.os.SystemClock.elapsedRealtime() + written * 1000 / 24000 + 2000
                while (turn == epoch.get() && player != null &&
                    (player!!.playbackHeadPosition.toLong() and 0xffffffffL) < written &&
                    android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(10)
            } catch (e: LinkageError) {
                if (turn == epoch.get()) main.post { if (turn == epoch.get()) failed("Local voice runtime is unavailable: ${e.message}") }
            } catch (e: Exception) {
                if (turn == epoch.get()) main.post { if (turn == epoch.get()) failed(e.message ?: "Local voice failed") }
            } finally {
                if (track === player) track = null
                runCatching { player?.release() }
                if (turn == epoch.get() && pending.decrementAndGet() == 0) {
                    startedEpoch = -1
                    main.post { if (turn == epoch.get() && pending.get() == 0) finished() }
                }
            }
        }
    }
    fun stop() {
        epoch.incrementAndGet()
        pending.set(0)
        startedEpoch = -1
        runCatching { track?.pause(); track?.flush() }
    }
    fun shutdown() { stop(); worker.shutdown() }
}
