package com.example.meowmba.core.audio

import kotlin.math.sqrt

/**
 * Energy-based voice gate for the always-on recognizer.
 *
 * This replaces a digital-zero test that opened on any single non-zero sample
 * and then latched open until the next endpoint. A real microphone never
 * returns exact zeros — room tone is plenty — so that gate stayed open for as
 * long as the mic was and the zipformer decoded every 100 ms chunk on silence.
 * That is where the sustained `DefaultDispatch` load came from: not animation,
 * not a runaway poll, just a recognizer transcribing an empty room.
 *
 * This one measures RMS energy and releases the decoder once the room has been
 * quiet for [HANGOVER_MS]. The hangover is deliberately longer than the
 * recognizer's own trailing-silence endpoint rule (1.6 s, set in
 * [SherpaAsrEngine.buildRecognizer]): the decoder has to keep running through
 * the tail or the utterance never endpoints and the transcript never lands.
 * Shortening it below the endpoint rule will look like a CPU win and will
 * silently break turn detection.
 *
 * This is not a general VAD. It decides only whether a chunk is worth spending
 * encoder time on, and it errs towards decoding: a missed syllable costs a
 * whole utterance, while a wasted decode costs microseconds.
 */
internal class SpeechEnergyGate(private val sampleRate: Int) {

    private var active = false
    private var quietMs = 0L
    private var openRms = OPEN_RMS
    private var closeRms = CLOSE_RMS

    /**
     * Multiplies both thresholds, so hysteresis is preserved and only the
     * absolute loudness required to win decoder time changes.
     *
     * Used by the thermal governor: a hot device cannot afford to decode room
     * tone, so it demands a louder onset. Applied between utterances only —
     * see [SherpaAsrEngine.captureLoop] — never while [active], so nobody is
     * cut off mid-sentence by the phone warming up.
     */
    fun setThresholdScale(scale: Float) {
        val factor = if (scale.isFinite()) scale.coerceAtLeast(1f).toDouble() else 1.0
        openRms = OPEN_RMS * factor
        closeRms = CLOSE_RMS * factor
    }

    /** True when this chunk should be decoded. */
    fun shouldDecode(pcm: ShortArray, count: Int): Boolean {
        if (count <= 0) return active

        var sum = 0.0
        for (i in 0 until count) {
            val v = pcm[i] / PCM16_FULL_SCALE
            sum += v * v
        }
        val rms = sqrt(sum / count)
        val chunkMs = count * 1000L / sampleRate

        when {
            rms >= openRms -> {
                active = true
                quietMs = 0L
            }
            active && rms < closeRms -> {
                quietMs += chunkMs
                if (quietMs >= HANGOVER_MS) {
                    active = false
                    quietMs = 0L
                }
            }
            active -> quietMs = 0L
        }
        return active
    }

    /** Called at every endpoint, and on gate release between utterances. */
    fun reset() {
        active = false
        quietMs = 0L
    }

    companion object {
        private const val PCM16_FULL_SCALE = 32_768.0
        /** Roughly -34 dBFS: clearly speech, not room tone. */
        private const val OPEN_RMS = 0.020
        /** Roughly -42 dBFS. Hysteresis, so a pause between words does not close. */
        private const val CLOSE_RMS = 0.008
        private const val HANGOVER_MS = 1_800L
    }
}
