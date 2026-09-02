package com.example.meowmba.core.robot

/**
 * Visual servo: keep a person in the camera frame using the LOOI base.
 *
 * Eyes stay snappy in VisionManager. This object is the BODY: a slow
 * Schmitt-trigger so a locked face's box jitter does not left-right the
 * treads. Start a turn only past [X_START]; keep going until inside
 * [X_HOLD]; ignore sign flips shorter than [SIGN_LOCK_MS].
 *
 * Losing the face is not "do nothing". After [SEARCH_AFTER_MS] the base
 * sweeps the way they left, in bursts of [SEARCH_GIVE_UP_MS] with a
 * [SEARCH_PAUSE_MS] rest between them, for at most [MAX_SEARCH_BURSTS]
 * bursts — alternating direction, so a person who walked off to one side is
 * found again instead of the base hunting full circles forever.
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
    const val SEARCH_AFTER_MS = 400L
    /** Length of one search sweep before resting. */
    const val SEARCH_GIVE_UP_MS = 2_800L
    const val SEARCH_PAUSE_MS = 2_000L
    const val MAX_SEARCH_BURSTS = 3
    /** Search is slower than re-centering: a sweep should not fling the body. */
    const val SEARCH_SPEED = 15
    const val SEARCH_TICKS = 4
    /** Speed cap while they sit at the lip of the frame. */
    const val EDGE_SPEED = 18
    const val BODY_ALPHA = 0.40f

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

        /** Search state: which way to sweep, how far into the budget we are. */
        var searchDir: Int = 0
        var searchBursts: Int = 0
        var burstStartMs: Long = 0L
        var pauseUntilMs: Long = 0L

        fun reset() {
            turning = false
            sign = 0
            signAtMs = 0L
            bodyX = 0f
            bodyY = 0f
            hasBody = false
            clearSearch()
        }

        /** A face is back: drop the search budget so the next loss starts fresh. */
        fun clearSearch() {
            searchDir = 0
            searchBursts = 0
            burstStartMs = 0L
            pauseUntilMs = 0L
        }
    }

    /** Which way to sweep: the side they were last on, else the last turn sign. */
    private fun searchDirection(lastX: Float, tracker: Tracker): Int =
        when {
            lastX > 0f -> 1
            lastX < 0f -> -1
            tracker.sign != 0 -> tracker.sign
            else -> 1
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
            // Face gone. Hold the head, then sweep to find it again — bounded,
            // because an unbounded sweep is the spin this replaced.
            if (!tracker.hasBody) return Command(0f, 0, 0, 0f, searching = false)
            val neck = if (kotlin.math.abs(tracker.bodyY) < Y_DEAD) 0f
                else (tracker.bodyY * 0.40f).coerceIn(-0.55f, 0.55f)
            val lostMs = if (lastSeenMs > 0L) nowMs - lastSeenMs else Long.MAX_VALUE
            if (lostMs < SEARCH_AFTER_MS) {
                tracker.turning = false
                return Command(neck, 0, 0, 0f, searching = false)
            }
            if (tracker.searchDir == 0) tracker.searchDir = searchDirection(lastX, tracker)
            if (tracker.searchBursts >= MAX_SEARCH_BURSTS) {
                // Swept both ways and found nobody: stop until a face returns.
                tracker.reset()
                return Command(neck, 0, 0, 0f, searching = false)
            }
            if (nowMs < tracker.pauseUntilMs) {
                tracker.turning = false
                return Command(neck, 0, 0, 0f, searching = false)
            }
            if (tracker.burstStartMs == 0L) tracker.burstStartMs = nowMs
            if (nowMs - tracker.burstStartMs < SEARCH_GIVE_UP_MS) {
                tracker.turning = true
                return Command(
                    neck,
                    tracker.searchDir * SEARCH_SPEED,
                    SEARCH_TICKS,
                    0f,
                    searching = true,
                )
            }
            // One sweep done: rest, then sweep back the other way.
            tracker.searchBursts++
            tracker.searchDir = -tracker.searchDir
            tracker.burstStartMs = 0L
            tracker.pauseUntilMs = nowMs + SEARCH_PAUSE_MS
            tracker.turning = false
            return Command(neck, 0, 0, 0f, searching = false)
        }
        val rawX = offsetX.coerceIn(-1f, 1f)
        val rawY = offsetY.coerceIn(-1f, 1f)
        if (!tracker.hasBody) {
            tracker.bodyX = rawX
            tracker.bodyY = rawY
            tracker.hasBody = true
        } else {
            // They are back: the search budget is for the NEXT time they go.
            tracker.clearSearch()
            tracker.bodyX += (rawX - tracker.bodyX) * BODY_ALPHA
            tracker.bodyY += (rawY - tracker.bodyY) * BODY_ALPHA
        }
        return command(tracker.bodyX, tracker.bodyY, nowMs, tracker)
    }

    internal fun command(x: Float, y: Float, nowMs: Long, tracker: Tracker): Command {
        val neck = if (kotlin.math.abs(y) < Y_DEAD) 0f else (y * 0.40f).coerceIn(-0.55f, 0.55f)
        val absX = kotlin.math.abs(x)
        // Screen-right (x>0) → robot turns right. The opposite mapping
        // chased the face out of frame.
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
}
