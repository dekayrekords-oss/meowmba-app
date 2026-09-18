package com.example.meowmba.ui.companion

/**
 * Stronger drive-mode reactivity: lower social-cue debounce, amplify IMU visual
 * gains, and fold OBD excitement into brace/pupil channels.
 *
 * Pure helpers so unit tests do not need Android.
 */
object DriveEmotionBoost {
    const val SOCIAL_MIN_GAP_MS = 700L
    const val SOCIAL_SAME_CUE_GAP_MS = 1_800L
    /** Extra headroom on lean/lurch inputs while driving (expressive, not calibrated). */
    const val IMU_GAIN = 1.35f
    /** Emotion valence → additional brace while driving. */
    const val EMOTION_BRACE_GAIN = 0.28f

    fun socialGaps(driving: Boolean): Pair<Long, Long> =
        if (driving) SOCIAL_MIN_GAP_MS to SOCIAL_SAME_CUE_GAP_MS else 1_800L to 4_500L

    fun amplifyImu(value: Float, driving: Boolean): Float {
        if (!driving || !value.isFinite()) return value
        return (value * IMU_GAIN).coerceIn(-1.25f, 1.25f)
    }

    fun emotionBraceAdd(valence: Float, driving: Boolean): Float {
        if (!driving) return 0f
        return (kotlin.math.abs(valence) * EMOTION_BRACE_GAIN).coerceIn(0f, 0.4f)
    }

    fun combineBrace(base: Float, emotionAdd: Float, obdExcitement: Float): Float =
        (base + emotionAdd + obdExcitement * 0.35f).coerceIn(0f, 1f)

    /** Slightly more sensitive reaction thresholds while drive-emotion boost is on. */
    fun driveReaction(
        speedMph: Float,
        lateralG: Float,
        longitudinalG: Float,
        boosted: Boolean,
    ): DriveReaction {
        if (!boosted) return driveReactionFor(speedMph, lateralG, longitudinalG)
        val turn = DriveReaction.HARD_TURN_G * 0.78f
        val brake = DriveReaction.HARD_BRAKE_G * 0.82f
        val fast = DriveReaction.FAST_MPH * 0.9f
        return when {
            longitudinalG <= -brake -> DriveReaction.LURCH
            lateralG >= turn -> DriveReaction.LEAN_RIGHT
            lateralG <= -turn -> DriveReaction.LEAN_LEFT
            speedMph >= fast -> DriveReaction.SPEED
            else -> DriveReaction.NONE
        }
    }
}
