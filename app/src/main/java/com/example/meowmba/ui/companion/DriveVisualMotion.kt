package com.example.meowmba.ui.companion

import kotlin.math.abs
import kotlin.math.sign

/** Visual inertia only. Never commands the robot, microphone, alerts, or crash detector. */
data class DriveVisualFrame(
    val lean: Float = 0f,
    val lurch: Float = 0f,
    val brace: Float = 0f,
    val driving: Float = 0f,
    val tilt: Float = 0f,
)

/**
 * Runs on the avatar's existing active draw clock. Inputs are normalized inertial
 * displacement (opposite vehicle acceleration), not raw accelerometer axes.
 * Freshness uses Android elapsed realtime, including sleep; animation uses the
 * pause-aware clock so background time cannot produce a spring jump on resume.
 */
class DriveVisualRig {
    private val lean = LumenSpring()
    private val lurch = LumenSpring()
    private val brace = LumenSpring()
    private val driving = LumenSpring()
    private val tilt = LumenSpring()
    private var lastTime: Float? = null
    private var frame = DriveVisualFrame()

    fun step(time: Float, nowMs: Long, observedAtMs: Long, enabled: Boolean,
             leanInput: Float, lurchInput: Float, tiltInput: Float = 0f): DriveVisualFrame {
        if (!time.isFinite()) return frame
        val previous = lastTime
        lastTime = time
        val dt = if (previous == null) 0f else (time - previous).coerceIn(0f, 0.05f)
        if (previous != null && dt == 0f) return frame
        val fresh = observedAtMs > 0 && nowMs >= observedAtMs && nowMs - observedAtMs <= STALE_MS
        val x = if (fresh) quiet(leanInput) else 0f
        val y = if (fresh) quiet(lurchInput) else 0f
        val intensity = maxOf(abs(x), y.coerceAtLeast(0f))
        val bracing = if (enabled) ((intensity - 0.32f) / 0.60f).coerceIn(0f, 1f) else 0f
        frame = DriveVisualFrame(
            lean = lean.step(x, dt, 14f).coerceIn(-1f, 1f),
            lurch = lurch.step(y, dt, 14f).coerceIn(-1f, 1f),
            brace = brace.step(bracing, dt, if (bracing > frame.brace) 18f else 7f).coerceIn(0f, 1f),
            driving = driving.step(if (enabled) 1f else 0f, dt, 9f).coerceIn(0f, 1f),
            tilt = tilt.step(if (fresh && tiltInput.isFinite()) tiltInput.coerceIn(-14f, 14f) else 0f, dt, 10f),
        )
        return frame
    }

    companion object {
        const val STALE_MS = 700L
        // About 0.05g: small road buzz should not read as a nervous character.
        internal fun quiet(value: Float): Float {
            if (!value.isFinite()) return 0f
            val v = value.coerceIn(-1f, 1f)
            return sign(v) * ((abs(v) - 0.08f) / 0.92f).coerceIn(0f, 1f)
        }
    }
}
