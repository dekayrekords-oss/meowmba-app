package com.example.meowmba.core.audio

import kotlin.math.log10
import kotlin.math.sqrt

data class InputSoundSample(val activity: Float, val loudness: Float, val fresh: Boolean, val source: String = "none",
    val humanVocalization: Boolean = false)

/** Latest microphone energy only: no recording, transcript, direction, or speech
 * recognition claim. Capture paths publish at most 10 Hz using their existing
 * reads. Consumers must use the same monotonic clock (monotonicNowMs).
 * Activity is an energy-based proxy, so music/noise can also activate it.
 */
object InputSoundLevel {
    private var sample = InputSoundSample(0f, 0f, false)
    private var atMs = -1L
    private var humanAtMs = -1L
    const val FRESH_MS = 800L
    private const val MIN_UPDATE_MS = 100L

    fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L

    /** Only accepted speech / human-vocalization labels call this. RMS is not human evidence. */
    @Synchronized fun noteHumanVocalization(nowMs: Long = monotonicNowMs()) {
        humanAtMs = nowMs
    }

    /** PCM16 amplitude RMS converted to dBFS; -60..0 dBFS maps to 0..1.
     * Activity rises above -45 dBFS and saturates at -25 dBFS. These are
     * cosmetic input-energy thresholds, not calibrated acoustic SPL or VAD.
     * At most 4096 samples are inspected; no array is retained or copied.
     */
    @Synchronized fun publishPcm(pcm: ShortArray, count: Int, nowMs: Long = monotonicNowMs(), source: String = "sherpa_pcm") {
        if (!acceptTime(nowMs) || count <= 0 || count > pcm.size) return
        val n = minOf(count, 4096)
        var squared = 0.0
        for (i in 0 until n) { val value = pcm[i] / 32768.0; squared += value * value }
        val rms = sqrt(squared / n)
        val db = if (rms > 0) (20 * log10(rms)).toFloat() else -120f
        publish(((db + 45f) / 20f).coerceIn(0f, 1f), ((db + 60f) / 60f).coerceIn(0f, 1f), nowMs, source)
    }

    /** Android RecognitionListener's provider-relative RMS dB scale has no
     * portable dBFS calibration. Map -2..10 to loudness and 2..8 to activity;
     * source identifies this different proxy. Never infer speech from armed mic.
     */
    @Synchronized fun publishSystemRms(rmsDb: Float, nowMs: Long = monotonicNowMs()) {
        if (!rmsDb.isFinite() || !acceptTime(nowMs)) return
        publish(((rmsDb - 2f) / 6f).coerceIn(0f, 1f), ((rmsDb + 2f) / 12f).coerceIn(0f, 1f), nowMs, "system_rms")
    }

    @Synchronized fun snapshot(nowMs: Long, suppressOwnSpeech: Boolean): InputSoundSample {
        // Erase own-speech energy so it cannot reappear when TTS suppression ends.
        if (suppressOwnSpeech) { clear(); return sample }
        val human = humanAtMs >= 0 && nowMs >= humanAtMs && nowMs - humanAtMs < 1500L
        if (atMs < 0 || nowMs < atMs || nowMs - atMs >= FRESH_MS)
            return InputSoundSample(0f, 0f, false, humanVocalization = human)
        return sample.copy(humanVocalization = human)
    }

    /** Source-aware close avoids a previous engine clearing a newer source. */
    @Synchronized fun clear(source: String? = null) {
        if (source != null && sample.source != source) return
        sample = InputSoundSample(0f, 0f, false)
        atMs = -1L
        humanAtMs = -1L
    }

    private fun acceptTime(nowMs: Long): Boolean = nowMs >= 0 &&
        (atMs < 0 || (nowMs >= atMs && nowMs - atMs >= MIN_UPDATE_MS))

    private fun publish(activity: Float, loudness: Float, nowMs: Long, source: String) {
        sample = InputSoundSample(activity, loudness, true, source)
        atMs = nowMs
        // Surroundings for Meow's context stamp (only while the mic is already listening).
        com.example.meowmba.core.context.live.LiveContext.noteLoudness(loudness, nowMs)
    }
}
