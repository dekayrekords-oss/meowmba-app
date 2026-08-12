package com.example.meowmba

import com.example.meowmba.core.vision.FaceFrameMap
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Face positions are measured in the camera's upright frame, which ignores the
 * phone's rotation. Pinning the rotation → display-space mapping so landscape
 * face tracking follows horizontal head motion instead of reading it vertical.
 */
class FaceFrameMapTest {

    private fun assertClose(expected: Float, actual: Float, tol: Float = 0.001f) {
        assertEquals("expected $expected got $actual", expected, actual, tol)
    }

    @Test
    fun `portrait is identity`() {
        val (sx, sy) = FaceFrameMap.toDisplay(0.25f, 0.75f, 0)
        assertClose(0.25f, sx)
        assertClose(0.75f, sy)
    }

    @Test
    fun `rotating ninety degrees swaps and flips axes`() {
        val (cx, cy) = FaceFrameMap.toDisplay(0.5f, 0.5f, 90)
        assertClose(0.5f, cx)
        assertClose(0.5f, cy)
        val (sx, sy) = FaceFrameMap.toDisplay(0.0f, 0.5f, 90)
        assertClose(0.5f, sx)
        assertClose(0.0f, sy)
    }

    @Test
    fun `two-seventy is the other landscape direction`() {
        val (sx, sy) = FaceFrameMap.toDisplay(0.5f, 0.0f, 270)
        assertClose(0.0f, sx) // upright top-center lands at display left-center
        assertClose(0.5f, sy)
    }

    @Test
    fun `upside down flips both`() {
        val (sx, sy) = FaceFrameMap.toDisplay(0.25f, 0.75f, 180)
        assertClose(0.75f, sx)
        assertClose(0.25f, sy)
    }

    @Test
    fun `rotation wraps past 360`() {
        val (sx, sy) = FaceFrameMap.toDisplay(0.5f, 0.5f, 450)
        assertClose(0.5f, sx)
        assertClose(0.5f, sy)
    }
}
