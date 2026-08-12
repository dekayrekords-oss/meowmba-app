package com.example.meowmba.core.robot

/**
 * Visual servo: keep a person in the camera frame using the LOOI base.
 *
 * Eyes stay snappy in VisionManager. This object is the BODY: a slow
 * Schmitt-trigger so a locked face's box jitter does not left-right the
 * treads. Start a turn only past [X_START]; keep going until inside
 * [X_HOLD]; ignore sign flips shorter than [SIGN_LOCK_MS].
 */
object KeepInFrame {

    const val X_START = 0.40f
    const val X_HOLD = 0.18f
    const val X_DEAD = X_HOLD
    const val Y_DEAD = 0.28f
    const val SIGN_LOCK_MS = 350L
    const val APPROACH_SCALE = 0.78f
    const val AIMED = 0.45f
    const val SEARCH_AFTER_MS = 400L
    const val SEARCH_GIVE_UP_MS = 2_800L
    const val BODY_ALPHA = 0.18f

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

        fun reset() {
            turning = false
            sign = 0
            signAtMs = 0L
            bodyX = 0f
            bodyY = 0f
            hasBody = false
        }
    }

    fun step(
        offsetX: Float?,
        offsetY: Float?,
        scale: Float?,
        nowMs: Long,
        lastSeenMs: Long,
        lastX: Float,
        tracker: Tracker = Tracker(),
    ): Command {
        if (offsetX == null || offsetY == null) {
            // A one-frame miss used to wipe hysteresis and the next hit
            // snapped into a turn. Hold the lock through a short blink.
            if (tracker.hasBody && lastSeenMs > 0L && nowMs - lastSeenMs < 700L) {
                tracker.turning = false
                return Command(0f, 0, 0, 0f, searching = false)
            }
            tracker.reset()
            return Command(0f, 0, 0, 0f, searching = false)
        }
        val rawX = offsetX.coerceIn(-1f, 1f)
        val rawY = offsetY.coerceIn(-1f, 1f)
        if (!tracker.hasBody) {
            tracker.bodyX = rawX
            tracker.bodyY = rawY
            tracker.hasBody = true
        } else {
            tracker.bodyX += (rawX - tracker.bodyX) * BODY_ALPHA
            tracker.bodyY += (rawY - tracker.bodyY) * BODY_ALPHA
        }
        return command(tracker.bodyX, tracker.bodyY, nowMs, tracker)
    }

    internal fun command(x: Float, y: Float, nowMs: Long, tracker: Tracker): Command {
        val neck = if (kotlin.math.abs(y) < Y_DEAD) 0f else (y * 0.35f).coerceIn(-0.45f, 0.45f)
        val absX = kotlin.math.abs(x)
        // Front camera faces the person. Screen-right is the robot's left.
        val rawSign = if (x > 0f) -1 else if (x < 0f) 1 else 0
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
        val shouldTurn = if (tracker.turning) absX > X_HOLD else absX > X_START
        tracker.turning = shouldTurn && sign != 0
        val (turnSpeed, turnTicks) = if (!tracker.turning) {
            0 to 0
        } else {
            val mag = (14 + absX * 22f).toInt().coerceIn(14, 32)
            val ticks = (4 + (absX * 4f).toInt()).coerceIn(4, 8)
            (sign * mag) to ticks
        }
        // Face-lock must not also scoot — scale flicker plus a turn is the
        // lurch. Approach stays off until it has its own deadband.
        return Command(neck, turnSpeed, turnTicks, driveSpeed = 0f, searching = false)
    }
}
