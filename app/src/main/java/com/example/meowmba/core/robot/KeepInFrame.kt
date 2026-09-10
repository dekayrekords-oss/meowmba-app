package com.example.meowmba.core.robot

import kotlin.math.abs
import kotlin.math.exp

/**
 * Physical follow intent for a currently observed person, never visual idle motion.
 *
 * Eyes stay snappy in VisionManager. This object is the BODY: a slow
 * Schmitt-trigger so a locked face's box jitter does not left-right the
 * treads. Start a turn only past [X_START]; keep going until inside
 * [X_HOLD]; ignore sign flips shorter than [SIGN_LOCK_MS].
 *
 * Losing the face always stops wheel intent. A brief gap preserves only the
 * filter's last pose; it never authorizes a search sweep. The caller must stop
 * following on missing/invalid observations, not actuate the fallback neck pose.
 * Actual actuation and verified direction remain the body safety gate's job.
 */
object KeepInFrame {

    const val X_START = 0.20f
    const val X_HOLD = 0.10f
    const val X_DEAD = X_HOLD
    /** Past this they are at the lip — turn harder, this is the recovery case. */
    const val X_EDGE = 0.50f
    const val Y_DEAD = 0.14f
    const val SIGN_LOCK_MS = 280L
    const val APPROACH_SCALE = 0.78f
    const val AIMED = 0.45f
    const val FACE_HOLD_MS = 400L
    /** Speed cap while they sit at the lip of the frame. */
    const val EDGE_SPEED = 18
    /** Approximately the old 0.4 blend at 10 Hz, independent of callback count. */
    const val BODY_SMOOTHING_MS = 200L

    data class Command(
        val neckPitch: Float,
        val turnSpeed: Int,
        val turnTicks: Int,
        val driveSpeed: Float,
        val searching: Boolean,
    ) {
        val turning: Boolean get() = turnSpeed != 0
        val driving: Boolean get() = driveSpeed > 0f
    }

    /** Mutable hysteresis for one live body. */
    class Tracker {
        var turning: Boolean = false
        var sign: Int = 0
        var signAtMs: Long = 0L
        var bodyX: Float = 0f
        var bodyY: Float = 0f
        var hasBody: Boolean = false

        internal var lastSampleAtMs: Long? = null

        fun reset() {
            turning = false
            sign = 0
            signAtMs = 0L
            bodyX = 0f
            bodyY = 0f
            hasBody = false
            lastSampleAtMs = null
        }
    }

    /** [nowMs] must use one monotonic clock; the caller owns observation freshness. */
    fun step(
        offsetX: Float?,
        offsetY: Float?,
        scale: Float?,
        nowMs: Long,
        lastSeenMs: Long,
        lastX: Float,
        tracker: Tracker = Tracker(),
    ): Command {
        if (nowMs < 0L || offsetX?.isFinite() == false || offsetY?.isFinite() == false ||
            scale?.isFinite() == false || !lastX.isFinite() || !tracker.isFinite()
        ) return resetAndHold(tracker)

        val previousAt = tracker.lastSampleAtMs
        if (previousAt != null && (previousAt < 0L || nowMs < previousAt)) return resetAndHold(tracker)

        if (offsetX == null || offsetY == null) {
            val seenAt = previousAt ?: lastSeenMs
            if (!tracker.hasBody || seenAt < 0L || seenAt > nowMs || nowMs - seenAt >= FACE_HOLD_MS) {
                return resetAndHold(tracker)
            }
            // Preserve a brief filter hold, but never preserve a turn or direction lock.
            tracker.turning = false
            tracker.sign = 0
            tracker.signAtMs = 0L
            return hold(neckPitch(tracker.bodyY))
        }

        // Repeated callbacks are not new tracking evidence and must not repeatedly
        // pull the filter toward the same point or replay a wheel pulse.
        if (previousAt == nowMs) {
            tracker.turning = false
            return hold(neckPitch(tracker.bodyY))
        }
        val rawX = offsetX.coerceIn(-1f, 1f)
        val rawY = offsetY.coerceIn(-1f, 1f)
        if (!tracker.hasBody) {
            tracker.bodyX = rawX
            tracker.bodyY = rawY
            tracker.hasBody = true
        } else {
            val elapsed = if (previousAt == null) 0L else nowMs - previousAt
            val alpha = (1.0 - exp(-elapsed.toDouble() / BODY_SMOOTHING_MS)).toFloat()
            tracker.bodyX += (rawX - tracker.bodyX) * alpha
            tracker.bodyY += (rawY - tracker.bodyY) * alpha
        }
        tracker.lastSampleAtMs = nowMs
        return command(tracker.bodyX, tracker.bodyY, nowMs, tracker)
    }

    internal fun command(x: Float, y: Float, nowMs: Long, tracker: Tracker): Command {
        if (!x.isFinite() || !y.isFinite() || !tracker.isFinite() || nowMs < 0L ||
            nowMs < tracker.signAtMs
        ) return resetAndHold(tracker)
        val neck = neckPitch(y.coerceIn(-1f, 1f))
        val absX = abs(x.coerceIn(-1f, 1f))
        // This is screen-space intent only, not proof of the base's motor direction.
        val rawSign = if (x > 0f) 1 else if (x < 0f) -1 else 0
        val sign = when {
            rawSign == 0 -> tracker.sign
            tracker.sign == 0 -> rawSign
            rawSign == tracker.sign -> rawSign
            absX >= 0.62f -> rawSign
            nowMs - tracker.signAtMs < SIGN_LOCK_MS -> tracker.sign
            else -> rawSign
        }
        if (sign != 0 && sign != tracker.sign) {
            tracker.sign = sign
            tracker.signAtMs = nowMs
        }
        val shouldTurn = sign != 0 &&
            if (tracker.turning) absX > X_HOLD else absX > X_START
        tracker.turning = shouldTurn
        val (turnSpeed, turnTicks) = if (!tracker.turning) {
            0 to 0
        } else {
            // At the lip they are halfway out of frame: that is when turning
            // matters most, so it gets the top of the band instead of a brake.
            val mag = if (absX >= X_EDGE) EDGE_SPEED
                else (10 + absX * 10f).toInt().coerceIn(10, 18)
            (sign * mag) to 3
        }
        // Face-lock must not also scoot — scale flicker plus a turn is the
        // lurch. Approach stays off until it has its own deadband.
        return Command(neck, turnSpeed, turnTicks, driveSpeed = 0f, searching = false)
    }

    private fun Tracker.isFinite(): Boolean = bodyX.isFinite() && bodyY.isFinite() &&
        bodyX in -1f..1f && bodyY in -1f..1f && sign in -1..1

    private fun neckPitch(y: Float): Float = if (abs(y) < Y_DEAD) 0f else y * 0.40f

    private fun hold(neck: Float = 0f) = Command(neck, 0, 0, 0f, searching = false)

    private fun resetAndHold(tracker: Tracker): Command {
        tracker.reset()
        return hold()
    }
}
