package com.example.meowmba.core.robot

/**
 * Visual servo: keep a person in the camera frame using the LOOI base.
 *
 * Pure. The ViewModel feeds face offsets; [RobotManager] performs the command
 * on whatever body is live. Deadzones stop chatter; we only creep forward
 * when the face is already roughly centered so we do not drive past them.
 */
object KeepInFrame {

    const val X_DEAD = 0.12f
    const val Y_DEAD = 0.10f
    const val APPROACH_SCALE = 0.78f
    const val AIMED = 0.32f
    const val SEARCH_AFTER_MS = 400L
    const val SEARCH_GIVE_UP_MS = 2_800L

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

    fun step(
        offsetX: Float?,
        offsetY: Float?,
        scale: Float?,
        nowMs: Long,
        lastSeenMs: Long,
        lastX: Float,
    ): Command {
        if (offsetX != null && offsetY != null) {
            val x = offsetX.coerceIn(-1f, 1f)
            val y = offsetY.coerceIn(-1f, 1f)
            val neck = if (kotlin.math.abs(y) < Y_DEAD) 0f else (y * 0.55f).coerceIn(-0.7f, 0.7f)
            val (turnSpeed, turnTicks) = if (kotlin.math.abs(x) < X_DEAD) {
                0 to 0
            } else {
                val sign = if (x > 0f) 1 else -1
                val mag = (18 + kotlin.math.abs(x) * 42f).toInt().coerceIn(18, 58)
                val ticks = (7 + (kotlin.math.abs(x) * 10f).toInt()).coerceIn(7, 16)
                (sign * mag) to ticks
            }
            val far = (scale ?: 1f) < APPROACH_SCALE
            val aimed = kotlin.math.abs(x) < AIMED
            val drive = if (far && aimed) 0.30f else 0f
            return Command(neck, turnSpeed, turnTicks, drive, searching = false)
        }

        val missing = nowMs - lastSeenMs
        if (lastSeenMs == 0L || missing > SEARCH_GIVE_UP_MS) {
            return Command(0f, 0, 0, 0f, searching = false)
        }
        if (missing < SEARCH_AFTER_MS) {
            return Command(0f, 0, 0, 0f, searching = false)
        }
        val sign = if (lastX >= 0f) 1 else -1
        return Command(0f, sign * 22, 8, 0f, searching = true)
    }
}
