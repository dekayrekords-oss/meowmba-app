package com.example.meowmba

import com.example.meowmba.core.vision.FacePose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 6-DOF mapping (normalization, distance proxy, head-size factor) is pure
 * math shared by the detector and the renderer — cheap to pin exactly.
 */
class FacePoseTest {

    private fun assertClose(expected: Float, actual: Float, tol: Float = 0.001f) {
        assertEquals("expected $expected got $actual", expected, actual, tol)
    }

    @Test
    fun `normalized rotations map -30 to 30 degrees onto -1 to 1`() {
        assertClose(0f, FacePose.normalizedYaw(0f))
        assertClose(1f, FacePose.normalizedYaw(30f))
        assertClose(-1f, FacePose.normalizedYaw(-30f))
        assertClose(0.5f, FacePose.normalizedYaw(15f))
        // Beyond range clamps, never runs away.
        assertClose(1f, FacePose.normalizedPitch(90f))
        assertClose(-1f, FacePose.normalizedRoll(-180f))
    }

    @Test
    fun `baseline face size is at-rest distance`() {
        // A face that is 35% of the frame width is the baseline.
        assertClose(1f, FacePose.scaleFromFaceFraction(0.35f, 1f))
        assertClose(1f, FacePose.scaleFromFaceFraction(350f, 1000f))
    }

    @Test
    fun `closer faces read bigger, farther faces smaller, clamped`() {
        val close = FacePose.scaleFromFaceFraction(0.7f, 1f)
        val far = FacePose.scaleFromFaceFraction(0.175f, 1f)
        assertTrue("close should be > 1, was $close", close > 1f)
        assertTrue("far should be < 1, was $far", far < 1f)
        // Never zero or absurd even on degenerate inputs.
        assertClose(1f, FacePose.scaleFromFaceFraction(0f, 0f))
        assertClose(1f, FacePose.scaleFromFaceFraction(-5f, 10f))
    }

    @Test
    fun `head size factor amplifies distance gently and stays bounded`() {
        // 1.0 scale -> no size change.
        assertClose(1f, FacePose.headSizeFactor(1f))
        // 1.5 scale -> ~17.5% bigger, capped at 1.15.
        assertClose(1.15f, FacePose.headSizeFactor(1.5f))
        assertClose(1.15f, FacePose.headSizeFactor(99f))
        // 0.6 scale -> ~-14%, floored at 0.85.
        assertClose(0.86f, FacePose.headSizeFactor(0.6f))
        assertClose(0.85f, FacePose.headSizeFactor(0.01f))
    }
}
