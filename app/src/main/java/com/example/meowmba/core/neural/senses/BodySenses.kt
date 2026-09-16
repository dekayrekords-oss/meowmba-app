package com.example.meowmba.core.neural.senses

import com.example.meowmba.core.neural.SensoryEvent
import com.example.meowmba.core.neural.SensoryKind
import com.example.meowmba.core.neural.SensorySource
import com.example.meowmba.core.sensor.MotionState
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Maps the device's own motion into sensory events: a continuous
 * [SensoryKind.DEVICE_MOTION] magnitude from the lateral/longitudinal
 * acceleration, and a [SensoryKind.SHAKE] impulse on each new shake.
 *
 * Being carried, driven or shaken is evidence about the character's own body,
 * so it feeds caution and curiosity rather than attention toward a target: the
 * device cannot tell where the motion came from.
 */
class MotionSenseAdapter(
    /** Combined g beyond which motion reads as 1. */
    private val fullScaleG: Float = 0.6f,
) {
    private var lastShakeCount: Int? = null

    fun reset() {
        lastShakeCount = null
    }

    fun mapState(state: MotionState, nowMs: Long): SensoryEvent {
        val g = sqrt(state.leanG * state.leanG + state.surgeG * state.surgeG)
        val magnitude = (g / fullScaleG).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
        val shaking = if (state.shaking) 1f else 0f
        return SensoryEvent(
            kind = SensoryKind.DEVICE_MOTION,
            source = SensorySource.DEVICE_MOTION,
            values = floatArrayOf(maxOf(magnitude, shaking)),
            confidence = 1f,
            timestampMs = nowMs,
        )
    }

    /** Emits one SHAKE per increment of the sensor's shake counter; the first observation only primes. */
    fun mapShakeCount(count: Int, nowMs: Long): SensoryEvent? {
        val prev = lastShakeCount
        lastShakeCount = count
        if (prev == null || count <= prev) return null
        val magnitude = (0.6f + 0.2f * abs(count - prev)).coerceIn(0f, 1f)
        return SensoryEvent.impulse(SensoryKind.SHAKE, magnitude, SensorySource.DEVICE_MOTION, 1f, nowMs)
    }
}

/**
 * Maps microphone energy into voice evidence. Fed from the ASR engine's
 * speech-energy gate: `(rms, gateOpen)` a few times a second. Emits a coalesced
 * [SensoryKind.VOICE_ACTIVITY] channel and a [SensoryKind.LOUD_SOUND] impulse
 * when the level jumps well above the running background.
 *
 * One microphone gives level and onset, not direction; nothing here pretends
 * otherwise.
 */
class VoiceActivitySense(
    /** RMS (0..1 normalised) that reads as full loudness. */
    private val fullScaleRms: Float = 0.25f,
    /** Jump over the background, in normalised units, that counts as a loud onset. */
    private val onsetJump: Float = 0.35f,
    private val onsetCooldownMs: Long = 1_500L,
    private val backgroundTauMs: Float = 4_000f,
) {
    private var background = 0f
    private var lastAtMs = Long.MIN_VALUE
    private var lastOnsetMs = Long.MIN_VALUE / 2

    fun reset() {
        background = 0f
        lastAtMs = Long.MIN_VALUE
        lastOnsetMs = Long.MIN_VALUE / 2
    }

    /** [rmsNormalised] is 0..1 (full-scale PCM = 1). */
    fun map(rmsNormalised: Float, gateOpen: Boolean, nowMs: Long): List<SensoryEvent> {
        val rms = if (rmsNormalised.isFinite()) rmsNormalised.coerceIn(0f, 1f) else 0f
        val loudness = (rms / fullScaleRms).coerceIn(0f, 1f)
        val out = ArrayList<SensoryEvent>(2)
        out += SensoryEvent(
            kind = SensoryKind.VOICE_ACTIVITY,
            source = SensorySource.VOICE,
            values = floatArrayOf(if (gateOpen) 1f else 0f, loudness),
            confidence = 1f,
            timestampMs = nowMs,
        )
        if (lastAtMs != Long.MIN_VALUE) {
            val dt = (nowMs - lastAtMs).coerceAtLeast(0L).toFloat()
            val jump = loudness - background
            if (jump >= onsetJump && nowMs - lastOnsetMs >= onsetCooldownMs) {
                lastOnsetMs = nowMs
                out += SensoryEvent.impulse(
                    SensoryKind.LOUD_SOUND, (jump / (onsetJump * 2f)).coerceIn(0.4f, 1f), SensorySource.VOICE, 1f, nowMs,
                )
            }
            val alpha = if (dt > 0f) 1f - kotlin.math.exp(-dt / backgroundTauMs) else 0f
            background += alpha * (loudness - background)
        } else {
            background = loudness
        }
        lastAtMs = nowMs
        return out
    }
}
