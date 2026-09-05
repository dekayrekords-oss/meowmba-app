package com.example.meowmba.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** Plays model-generated PCM WAV and reports real waveform energy to the avatar. */
class AndroidNativeSpeechPlayer : NativeSpeechPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playbackJob: Job? = null
    @Volatile private var track: AudioTrack? = null

    override fun play(
        wav: ByteArray,
        onStarted: () -> Unit,
        onEnergy: (Float) -> Unit,
        onFinished: () -> Unit,
        onError: (String) -> Unit,
    ) {
        stop()
        val decoded = PcmWav.decode(wav) ?: run {
            onError("The speech model returned an unsupported WAV file")
            return
        }
        playbackJob = scope.launch {
            val audioTrack = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(decoded.sampleRate)
                            .setChannelMask(
                                if (decoded.channels == 1) AudioFormat.CHANNEL_OUT_MONO
                                else AudioFormat.CHANNEL_OUT_STEREO,
                            )
                            .build(),
                    )
                    .setBufferSizeInBytes(decoded.pcm.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrElse {
                withContext(Dispatchers.Main.immediate) {
                    onError(it.message ?: "Audio playback could not start")
                }
                return@launch
            }
            track = audioTrack
            try {
                val written = audioTrack.write(decoded.pcm, 0, decoded.pcm.size)
                if (written <= 0) error("AudioTrack rejected the speech audio ($written)")
                withContext(Dispatchers.Main.immediate) { onStarted() }
                audioTrack.play()
                val frames = decoded.pcm.size / (2 * decoded.channels)
                val deadline = System.currentTimeMillis() +
                    ((frames.toLong() * 1_000L / decoded.sampleRate) + 2_000L)
                while (isActive && audioTrack.playbackHeadPosition < frames &&
                    System.currentTimeMillis() < deadline
                ) {
                    val frame = audioTrack.playbackHeadPosition.coerceIn(0, frames)
                    val energy = decoded.energyAtFrame(frame)
                    withContext(Dispatchers.Main.immediate) { onEnergy(energy) }
                    delay(45)
                }
                withContext(Dispatchers.Main.immediate) {
                    onEnergy(0f)
                    onFinished()
                }
            } catch (e: Exception) {
                if (isActive) withContext(Dispatchers.Main.immediate) {
                    onEnergy(0f)
                    onError(e.message ?: "Speech playback failed")
                }
            } finally {
                runCatching { audioTrack.stop() }
                audioTrack.release()
                if (track === audioTrack) track = null
            }
        }
    }

    override fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        track = null
    }

    override fun release() {
        stop()
        scope.cancel()
    }
}

/** Minimal RIFF/PCM16 decoder; pure so malformed provider output is unit-testable. */
internal object PcmWav {
    data class Decoded(
        val sampleRate: Int,
        val channels: Int,
        val pcm: ByteArray,
    ) {
        fun energyAtFrame(frame: Int, windowFrames: Int = 1_080): Float {
            val start = (frame * channels * 2).coerceIn(0, pcm.size)
            val end = (start + windowFrames * channels * 2).coerceAtMost(pcm.size)
            if (end - start < 2) return 0f
            var sum = 0.0
            var count = 0
            var i = start
            while (i + 1 < end) {
                val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort()
                val normalized = sample / 32768.0
                sum += normalized * normalized
                count++
                i += 2
            }
            return (sqrt(sum / count) * 4.0).toFloat().coerceIn(0f, 1f)
        }
    }

    fun decode(bytes: ByteArray): Decoded? {
        if (bytes.size < 44 || ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WAVE") return null
        var offset = 12
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var encoding = 0
        var pcm: ByteArray? = null
        while (offset + 8 <= bytes.size) {
            val id = ascii(bytes, offset)
            val size = le32(bytes, offset + 4) ?: return null
            val dataAt = offset + 8
            if (size < 0 || dataAt + size > bytes.size) return null
            when (id) {
                "fmt " -> if (size >= 16) {
                    encoding = le16(bytes, dataAt) ?: return null
                    channels = le16(bytes, dataAt + 2) ?: return null
                    sampleRate = le32(bytes, dataAt + 4) ?: return null
                    bitsPerSample = le16(bytes, dataAt + 14) ?: return null
                }
                "data" -> pcm = bytes.copyOfRange(dataAt, dataAt + size)
            }
            offset = dataAt + size + (size and 1)
        }
        val data = pcm ?: return null
        if (encoding != 1 || channels !in 1..2 || sampleRate !in 8_000..96_000 ||
            bitsPerSample != 16 || data.isEmpty()
        ) return null
        return Decoded(sampleRate, channels, data)
    }

    private fun ascii(bytes: ByteArray, at: Int): String =
        if (at + 4 <= bytes.size) String(bytes, at, 4, Charsets.US_ASCII) else ""

    private fun le16(bytes: ByteArray, at: Int): Int? = if (at + 2 <= bytes.size) {
        (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)
    } else null

    private fun le32(bytes: ByteArray, at: Int): Int? = if (at + 4 <= bytes.size) {
        (bytes[at].toInt() and 0xff) or
            ((bytes[at + 1].toInt() and 0xff) shl 8) or
            ((bytes[at + 2].toInt() and 0xff) shl 16) or
            ((bytes[at + 3].toInt() and 0xff) shl 24)
    } else null
}

