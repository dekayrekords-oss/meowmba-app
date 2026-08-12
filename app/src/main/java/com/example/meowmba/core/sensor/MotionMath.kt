package com.example.meowmba.core.sensor

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure helpers for the IMU path. No Android imports, so the math is pinned in
 * JVM unit tests.
 */
object MotionMath {

    /**
     * Below this in-plane gravity magnitude (m/s²) the phone is too close to
     * flat for a screen-rotation reading — the projection collapses and the
     * angle would jitter wildly. The caller holds its last value instead.
     */
    const val MIN_IN_PLANE = 2f

    /**
     * Rotation of the phone about the screen normal, from the gravity vector's
     * in-plane projection. 0° = portrait upright, ±90° = landscape, ±180° =
     * upside down.
     *
     * This is the channel that keeps working in a horizontal car dock: the old
     * roll math (atan2(x, z)) saturates the moment gravity leaves the z axis,
     * which is exactly what "landscape" means — the avatar looked dead there.
     *
     * Returns null when the phone is too flat to call an angle.
     */
    fun screenLeanDeg(gx: Float, gy: Float): Float? {
        val inPlane = sqrt(gx * gx + gy * gy)
        if (inPlane < MIN_IN_PLANE) return null
        val deg = Math.toDegrees(atan2(gy.toDouble(), gx.toDouble())).toFloat() - 90f
        // Normalize to [-180, 180] so upside-down doesn't wrap to a jump.
        return ((deg + 540f) % 360f) - 180f
    }
}
