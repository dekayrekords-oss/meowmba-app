package com.example.meowmba.core.car

import kotlin.math.sqrt

/**
 * Pure math for turning accelerometer readings into "which way is the car
 * pushing me" signals. No Android imports so it is unit-testable on the JVM.
 *
 * A phone's accelerometer lives in the *device* frame; the cat wants the *car*
 * frame. We cannot know how the phone is mounted for certain, so this uses a
 * robust assumption:
 *
 *   - Gravity (smoothed low-pass from the raw accelerometer) tells us which
 *     device axis points "up", i.e. the car's horizontal plane.
 *   - The phone's back faces the windshield in a dashboard mount, so the car's
 *     forward direction is approximately the device -Z axis projected onto the
 *     horizontal plane. This holds for portrait and landscape mounts alike.
 *   - When the phone is lying flat the -Z projection degenerates (zero length)
 *     and we fall back to the device -Y axis (the screen's bottom edge).
 *
 * Lateral/longitudinal signs are therefore approximate, but the *magnitudes*
 * are reliable, which is what the reaction thresholds care about.
 */
object CarDynamics {

    /** Decomposed horizontal acceleration in car terms. Units are G (1 = 9.80665 m/s²). */
    data class Accel(
        /** Signed lateral G; positive ≈ turning toward the phone's +X side. */
        val lateralG: Float = 0f,
        /** Signed longitudinal G; positive = accelerating forward, negative = braking. */
        val longitudinalG: Float = 0f,
        /** Magnitude of horizontal (car-plane) acceleration, always >= 0. */
        val horizontalG: Float = 0f,
    )

    private const val G = 9.80665f

    /**
     * Decompose device-frame linear acceleration into car-frame lateral/longitudinal.
     *
     * @param linear  device-frame linear acceleration, m/s² (gravity removed).
     * @param gravity smoothed gravity vector, m/s² (magnitude ~9.81).
     */
    fun decompose(linear: FloatArray, gravity: FloatArray): Accel {
        val la = Vector3(linear)
        val g = Vector3(gravity)
        val gMag = g.length()

        // No usable gravity reference yet (sensor just started): fall back to
        // the device frame so nothing crashes and magnitudes still survive.
        if (gMag < 0.5f) {
            return Accel(
                lateralG = la.x / G,
                longitudinalG = la.y / G,
                horizontalG = la.length() / G,
            )
        }

        val up = g / gMag
        val downProjected = la.dot(up)
        val h = la - up * downProjected      // linear accel lying in the car plane
        val hMag = h.length()

        // Car forward ≈ -Z (phone back) projected into the car plane.
        val back = Vector3(0f, 0f, -1f)
        var fRaw = back - up * back.dot(up)
        if (fRaw.length() < 0.25f) {
            // Phone is lying flat: -Z is (near) vertical. Use the bottom edge.
            val bottom = Vector3(0f, -1f, 0f)
            fRaw = bottom - up * bottom.dot(up)
        }
        if (fRaw.length() < 0.25f) {
            // Still degenerate (phone resting on its side edge): device frame.
            return Accel(
                lateralG = la.x / G,
                longitudinalG = la.y / G,
                horizontalG = hMag / G,
            )
        }
        val fHat = fRaw / fRaw.length()
        // Right-hand perpendicular to (forward, up): positive = "right" side.
        val right = cross(fHat, up)
        val rightHat = if (right.length() < 0.25f) Vector3(1f, 0f, 0f) else right / right.length()

        return Accel(
            lateralG = h.dot(rightHat) / G,
            longitudinalG = h.dot(fHat) / G,
            horizontalG = hMag / G,
        )
    }

    private fun cross(a: Vector3, b: Vector3): Vector3 = Vector3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x,
    )

    private class Vector3(val x: Float, val y: Float, val z: Float) {
        constructor(a: FloatArray) : this(a.getOrElse(0) { 0f }, a.getOrElse(1) { 0f }, a.getOrElse(2) { 0f })

        operator fun plus(o: Vector3) = Vector3(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: Vector3) = Vector3(x - o.x, y - o.y, z - o.z)
        operator fun times(k: Float) = Vector3(x * k, y * k, z * k)
        operator fun div(k: Float) = Vector3(x / k, y / k, z / k)
        fun dot(o: Vector3) = x * o.x + y * o.y + z * o.z
        fun length() = sqrt(x * x + y * y + z * z)
    }
}
