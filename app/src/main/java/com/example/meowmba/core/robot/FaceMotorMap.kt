package com.example.meowmba.core.robot

import com.example.meowmba.core.personality.Mood

/**
 * Maps every avatar behavior (built-in animations, mood, IMU tilt) to servo
 * commands for a physical LOOI-style body. Channel layout is provisional —
 * swap freely when real hardware arrives; the mapping is pure and tested.
 */
object FaceMotorMap {
    const val SERVO_HEAD_TILT = 0
    const val SERVO_HEAD_PAN = 1
    const val SERVO_EAR_LEFT = 2
    const val SERVO_EAR_RIGHT = 3
    const val SERVO_TAIL = 4
    const val SERVO_BODY = 5

    private const val CENTER = 90f

    /** Built-in avatar animation → servo frame. */
    fun mapAnimation(name: String): Map<Int, Float> = when (name) {
        "ear wiggle" -> mapOf(SERVO_EAR_LEFT to 70f, SERVO_EAR_RIGHT to 110f)
        "happy bop" -> mapOf(SERVO_BODY to 100f, SERVO_TAIL to 120f)
        "slow blink" -> mapOf(SERVO_HEAD_TILT to 85f)
        "paw lick" -> mapOf(SERVO_HEAD_TILT to 75f, SERVO_TAIL to 80f)
        else -> mapOf(SERVO_HEAD_PAN to CENTER)
    }

    /** Mood → resting servo pose + expression name. */
    fun mapMood(mood: Mood): Pair<Map<Int, Float>, String> = when (mood) {
        Mood.EXCITED -> mapOf(SERVO_BODY to 100f, SERVO_TAIL to 120f, SERVO_EAR_LEFT to 100f, SERVO_EAR_RIGHT to 100f) to "happy"
        Mood.SLEEPY -> mapOf(SERVO_HEAD_TILT to 75f, SERVO_BODY to 80f) to "sleepy"
        Mood.STARTLED -> mapOf(SERVO_EAR_LEFT to 60f, SERVO_EAR_RIGHT to 60f, SERVO_HEAD_TILT to 100f) to "alert"
        Mood.PROUD -> mapOf(SERVO_HEAD_TILT to 100f, SERVO_TAIL to 110f) to "proud"
        Mood.CURIOUS -> mapOf(SERVO_HEAD_PAN to 100f, SERVO_EAR_LEFT to 105f) to "curious"
        Mood.CONTENT -> mapOf(SERVO_HEAD_TILT to CENTER, SERVO_BODY to CENTER) to "content"
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
     * TILT looks up/down at the face (the LOOI BLE neck channel). PAN looks
     * left/right: a real pan servo on TCP/serial, a short in-place turn on BLE.
     * Null offsets recenter.
     */
    fun followFace(offsetX: Float?, offsetY: Float?): Map<Int, Float> {
        val pan = panForFace(offsetX)
        val tilt = if (offsetY == null) {
            mapOf(SERVO_HEAD_TILT to CENTER)
        } else {
            val y = offsetY.coerceIn(-1f, 1f)
            val angle = if (kotlin.math.abs(y) < 0.1f) CENTER else CENTER + y * 30f
            mapOf(SERVO_HEAD_TILT to angle)
        }
        return pan + tilt
    }
}
