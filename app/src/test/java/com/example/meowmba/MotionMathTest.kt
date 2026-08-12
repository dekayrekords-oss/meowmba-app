package com.example.meowmba

import com.example.meowmba.core.sensor.MotionMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The screen-plane tilt is the channel that keeps the avatar tracking in a
 * horizontal car dock — the old roll math saturated there. Pin the angle
 * convention so a refactor can't quietly rotate the cat the wrong way.
 */
class MotionMathTest {

    private fun assertClose(expected: Float, actual: Float?, tol: Float = 0.01f) {
        requireNotNull(actual) { "expected $expected, got null" }
        assertEquals(expected, actual, tol)
    }

    @Test
    fun `portrait upright reads zero`() {
        // Gravity at rest points along +Y in portrait.
        assertClose(0f, MotionMath.screenLeanDeg(0f, 9.81f))
    }

    @Test
    fun `landscape both ways reads plus or minus 90`() {
        assertClose(-90f, MotionMath.screenLeanDeg(9.81f, 0f))
        assertClose(90f, MotionMath.screenLeanDeg(-9.81f, 0f))
    }

    @Test
    fun `upside down reads plus or minus 180 without wrapping`() {
        val lean = MotionMath.screenLeanDeg(0f, -9.81f)
        requireNotNull(lean)
        assertEquals(180f, kotlin.math.abs(lean), 0.01f)
    }

    @Test
    fun `small in-plane tilts are proportional`() {
        // 45° off upright, top leaning right: gravity splits evenly.
        val g = 9.81f * 0.7071f
        assertClose(-45f, MotionMath.screenLeanDeg(g, g))
        assertClose(45f, MotionMath.screenLeanDeg(-g, g))
    }

    @Test
    fun `phone too flat returns null so the caller holds the last angle`() {
        assertNull(MotionMath.screenLeanDeg(0f, 0f))
        assertNull(MotionMath.screenLeanDeg(0.5f, 0.5f))
        assertNull(MotionMath.screenLeanDeg(1.9f, 0f))
    }
}
