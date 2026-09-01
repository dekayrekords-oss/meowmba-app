package com.example.meowmba.core.robot

import com.example.meowmba.core.personality.Mood

/**
 * Maps mood, IMU tilt and face tracking to a physical body.
 *
 * Mood now speaks [BodyVerb] — the LOOI base's own vocabulary. It used to emit
 * servo angles for ears, a tail and a pan axis the hardware does not have, of
 * which four channels were silently dropped in translation.
 *
 * [balanceTilt], [panForFace] and [followFace] still speak servo angles: they
 * drive the legacy servo path and are unrelated to the clip-driven body.
 */
object FaceMotorMap {
    const val SERVO_HEAD_TILT = 0
    const val SERVO_HEAD_PAN = 1
    const val SERVO_EAR_LEFT = 2
    const val SERVO_EAR_RIGHT = 3
    const val SERVO_TAIL = 4
    const val SERVO_BODY = 5

    private const val CENTER = 90f

    /** Mood → resting body pose + expression name. Never a drive. */
    fun mapMood(mood: Mood): Pair<List<BodyVerb>, String> = when (mood) {
        Mood.EXCITED -> listOf(BodyVerb.Neck(-0.4f), BodyVerb.Light(true)) to "happy"
        Mood.SLEEPY -> listOf(BodyVerb.Neck(0.6f), BodyVerb.Light(false)) to "sleepy"
        Mood.STARTLED -> listOf(BodyVerb.Neck(-0.7f), BodyVerb.Light(true)) to "alert"
        Mood.PROUD -> listOf(BodyVerb.Neck(-0.5f), BodyVerb.Light(true)) to "proud"
        Mood.CURIOUS -> listOf(BodyVerb.Neck(-0.2f), BodyVerb.Light(true)) to "curious"
        Mood.CONTENT -> listOf(BodyVerb.Neck(0f), BodyVerb.Light(true)) to "content"
    }

    /**
     * IMU balance demo: the head counter-rotates against the phone's tilt
     * so the body appears to stay level. Clamped to ±15° around center.
     */
    fun balanceTilt(tiltDeg: Float): Map<Int, Float> {
        val correction = (-tiltDeg).coerceIn(-15f, 15f)
        return mapOf(SERVO_HEAD_TILT to CENTER + correction)
    }

    /**
     * Face tracking → head pan. [offsetX] is the face's horizontal position
     * normalized to -1..1 (negative = subject left of frame). A small deadzone
     * (±0.1) keeps jitter from chattering the servo at center; the head pans
     * ±35° to follow the face and recenters when the face leaves the frame
     * ([offsetX] is null there — callers pass a centered value or empty map).
     */
    fun panForFace(offsetX: Float?): Map<Int, Float> {
        if (offsetX == null) return mapOf(SERVO_HEAD_PAN to CENTER)
        val clamped = offsetX.coerceIn(-1f, 1f)
        if (kotlin.math.abs(clamped) < 0.1f) return mapOf(SERVO_HEAD_PAN to CENTER)
        return mapOf(SERVO_HEAD_PAN to CENTER + clamped * 35f)
    }

    /**
     * Face tracking → neck + base. [offsetX]/[offsetY] are screen-space -1..1
     * (negative X = subject left, positive Y = subject lower on the display).
     *
     * TILT looks up/down at the face (the LOOI BLE neck channel).
     * PAN looks left/right (a real pan servo on TCP, a short base turn on BLE).
     * Null offsets recenter.
     */
    fun followFace(offsetX: Float?, offsetY: Float?): Map<Int, Float> {
        val pan = panForFace(offsetX)
        val tilt = if (offsetY == null) {
            mapOf(SERVO_HEAD_TILT to CENTER)
        } else {
            val y = offsetY.coerceIn(-1f, 1f)
            val angle = if (kotlin.math.abs(y) < 0.12f) CENTER else CENTER + y * 50f
            mapOf(SERVO_HEAD_TILT to angle.coerceIn(45f, 135f))
        }
        return pan + tilt
    }
}