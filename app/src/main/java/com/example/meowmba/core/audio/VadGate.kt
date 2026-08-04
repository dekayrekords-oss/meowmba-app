package com.example.meowmba.core.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * Speech gate in front of the keyword spotter — the VAD stage of a VAD → KWS pipeline.
 *
 * The keyword model otherwise runs on every 100 ms frame all day, including hours of
 * silence. A VAD is far cheaper (629 KB vs 5 MB, and much less compute), so gating on it
 * is most of the battery win with none of the accuracy loss — provided the lead-in is
 * handled, which is the subtle part.
 *
 * **The lead-in problem.** A VAD needs a moment of speech before it flips to "speaking".
 * Feeding the spotter only from that point clips the start of the phrase — "yo" is already
 * gone by the time the gate opens, and the wake never fires. So this class keeps its own
 * short ring buffer and hands it back on the rising edge via [leadIn], to be replayed into
 * the spotter before the current frame.
 *
 * Fails OPEN: if the model is missing or errors, every frame is reported as speech and the
 * spotter behaves exactly as it did without a gate.
 */

/**
 * Window alignment across frames for the VAD.
 *
 * Silero needs EXACT 512-sample windows, and a 1600-sample capture frame does not divide
 * evenly — 64 samples are left over each time. The old implementation counted those and
 * threw them away: a steady 4% of the audio never reached the VAD, and every window
 * boundary drifted by the same amount. Quiet damage of exactly the kind a short wake word
 * does not survive. The leftovers are now kept as PCM and prepended to the next frame, so
 * every sample reaches the VAD exactly once, in order.
 *
 * Internal (not private) so the bookkeeping can be unit-tested without a native VAD.
 */
internal class WindowCarry(private val window: Int) {

    /** Samples waiting for the next frame to complete a window. */
    private var pending = ShortArray(0)

    val pendingSamples: Int get() = pending.size

    /**
     * Feeds one frame; returns the sample stream that fills whole windows (a multiple of
     * [window]) and keeps the remainder for the next call.
     */
    fun feed(frame: FloatArray): FloatArray {
        val combined = FloatArray(pending.size + frame.size)
        for (i in pending.indices) combined[i] = pending[i] / 32_768.0f
        frame.copyInto(combined, pending.size)
        val usable = (combined.size / window) * window
        pending = if (usable < combined.size) {
            ShortArray(combined.size - usable) {
                (combined[usable + it] * 32_768.0f).toInt().coerceIn(-32_768, 32_767).toShort()
            }
        } else {
            ShortArray(0)
        }
        return if (usable == combined.size) combined else combined.copyOf(usable)
    }

    fun clear() {
        pending = ShortArray(0)
    }
}

class VadGate private constructor(private val vad: Vad) {

    enum class Decision {
        /** No speech; skip the keyword model entirely. */
        SILENT,

        /** Speech just began — replay [leadIn] before this frame. */
        SPEECH_STARTED,

        /** Speech ongoing; feed the frame straight through. */
        SPEAKING,
    }

    /** Audio held back for replay when the gate opens. */
    private val preSpeech = AudioPreRoll.forSeconds(LEAD_IN_SECONDS)

    private var wasSpeaking = false

    /** Window alignment across frames — see [WindowCarry]. */
    private val carry = WindowCarry(WINDOW)

    /** Frames of trailing silence still treated as speech, so a pause mid-phrase is kept. */
    private var hangoverFrames = 0

    /**
     * Classifies one frame (16 kHz mono, -1..1) and updates the gate.
     *
     * Must be called with every captured frame, speech or not, or the lead-in buffer goes
     * stale.
     */
    fun classify(frame: FloatArray): Decision {
        val speech = try {
            // Silero requires EXACTLY windowSize samples per call. Handing it a whole
            // 100ms frame (1600 samples) instead of 512-sample windows is not merely
            // inefficient — it left the capture path delivering digital silence a few
            // seconds in, bisected by disabling this gate and watching the mic recover.
            val aligned = carry.feed(frame)
            var offset = 0
            while (offset + WINDOW <= aligned.size) {
                vad.acceptWaveform(aligned.copyOfRange(offset, offset + WINDOW))
                offset += WINDOW
            }
            vad.isSpeechDetected()
        } catch (e: Exception) {
            Log.w(TAG, "VAD errored, passing audio through: ${e.message}")
            return Decision.SPEAKING
        }

        if (speech) {
            hangoverFrames = HANGOVER_FRAMES
        } else if (hangoverFrames > 0) {
            hangoverFrames--
        }
        val active = speech || hangoverFrames > 0

        return when {
            active && !wasSpeaking -> {
                wasSpeaking = true
                Decision.SPEECH_STARTED
            }
            active -> Decision.SPEAKING
            else -> {
                wasSpeaking = false
                // Only silence is buffered: it is what precedes the next rising edge.
                preSpeech.append(toPcm(frame))
                Decision.SILENT
            }
        }
    }

    /** The buffered lead-in, or null when there is nothing to replay. */
    fun leadIn(): FloatArray? {
        val pcm = preSpeech.snapshot()
        preSpeech.clear()
        if (pcm.isEmpty()) return null
        return FloatArray(pcm.size) { pcm[it] / 32_768.0f }
    }

    fun reset() {
        runCatching { vad.reset() }
        preSpeech.clear()
        wasSpeaking = false
        hangoverFrames = 0
        carry.clear()
    }

    fun release() {
        runCatching { vad.release() }
    }

    private fun toPcm(frame: FloatArray) = ShortArray(frame.size) {
        (frame[it] * 32_768.0f).toInt().coerceIn(-32_768, 32_767).toShort()
    }

    companion object {
        private const val TAG = "VadGate"

        const val MODEL = "silero_vad.onnx"

        /** Must match SileroVadModelConfig.windowSize. */
        private const val WINDOW = 512

        /**
         * How much pre-speech audio to replay when the gate opens. 0.6s comfortably covers
         * the syllables a VAD swallows deciding that speech has begun.
         */
        private const val LEAD_IN_SECONDS = 0.6f

        /**
         * Frames (100 ms each) of silence still passed through after speech stops. Keeps a
         * brief pause inside a phrase from closing the gate mid-wake-word.
         */
        private const val HANGOVER_FRAMES = 8

        /** True when the VAD model is bundled at the assets root. */
        fun isModelAvailable(context: Context): Boolean = try {
            context.assets.open(MODEL).close()
            true
        } catch (e: Exception) {
            false
        }

        /** Null when the model is absent or fails to load; the caller then runs ungated. */
        fun create(context: Context, sampleRate: Int = 16_000): VadGate? {
            if (!isModelAvailable(context)) return null
            return try {
                VadGate(
                    Vad(
                        assetManager = context.assets,
                        config = VadModelConfig(
                            sileroVadModelConfig = SileroVadModelConfig(
                                model = MODEL,
                                threshold = 0.5f,
                                minSilenceDuration = 0.25f,
                                minSpeechDuration = 0.10f,   // react fast; a wake word is short
                                windowSize = 512,
                            ),
                            sampleRate = sampleRate,
                            numThreads = 1,
                        ),
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "VAD model failed to load: ${e.message}")
                null
            }
        }
    }
}
