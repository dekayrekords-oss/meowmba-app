package com.example.meowmba

import com.example.meowmba.core.vision.faceOffsetInScreenSpace
import com.example.meowmba.core.vision.rotatedFrameSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The camera → screen axis convention, pinned.
 *
 * Horizontal face-follow broke three times because this transform lived inline
 * in a Composable and in the ViewModel, in two copies that disagreed. These are
 * the properties every consumer (avatar gaze, head pan, robot servos) relies on.
 */
class FaceTrackGeometryTest {

    @Test
    fun `90 and 270 degree frames swap width and height`() {
        // ML Kit measures boxes in the ROTATED frame, so a portrait-upright view
        // of a 640x480 sensor buffer is 480 wide by 640 tall.
        assertEquals(480 to 640, rotatedFrameSize(640, 480, 90))
        assertEquals(480 to 640, rotatedFrameSize(640, 480, 270))
    }

    @Test
    fun `0 and 180 degree frames keep buffer dimensions`() {
        assertEquals(640 to 480, rotatedFrameSize(640, 480, 0))
        assertEquals(640 to 480, rotatedFrameSize(640, 480, 180))
    }

    @Test
    fun `rotation is normalized so negative and over-360 values still swap`() {
        assertEquals(480 to 640, rotatedFrameSize(640, 480, -90))
        assertEquals(480 to 640, rotatedFrameSize(640, 480, 450))
        assertEquals(640 to 480, rotatedFrameSize(640, 480, 360))
    }

    @Test
    fun `frame center maps to zero on both axes`() {
        val (x, y) = faceOffsetInScreenSpace(240f, 320f, 480, 640, mirrored = false)!!
        assertEquals(0f, x, 1e-4f)
        assertEquals(0f, y, 1e-4f)
    }

    @Test
    fun `back lens keeps sensor handedness`() {
        val (right, _) = faceOffsetInScreenSpace(480f, 320f, 480, 640, mirrored = false)!!
        assertEquals(1f, right, 1e-4f)
        val (left, _) = faceOffsetInScreenSpace(0f, 320f, 480, 640, mirrored = false)!!
        assertEquals(-1f, left, 1e-4f)
    }

    @Test
    fun `front lens mirrors X so moving right moves the gaze right`() {
        // The user's own right appears on the LEFT of an un-mirrored front
        // sensor frame. Without this flip the avatar looks away from the user.
        val (mirrored, _) = faceOffsetInScreenSpace(0f, 320f, 480, 640, mirrored = true)!!
        assertTrue("front lens must invert X", mirrored > 0f)
        assertEquals(1f, mirrored, 1e-4f)
    }

    @Test
    fun `vertical axis is never mirrored`() {
        val (_, top) = faceOffsetInScreenSpace(240f, 0f, 480, 640, mirrored = true)!!
        val (_, bottom) = faceOffsetInScreenSpace(240f, 640f, 480, 640, mirrored = true)!!
        assertEquals("top of frame is -1", -1f, top, 1e-4f)
        assertEquals("bottom of frame is +1", 1f, bottom, 1e-4f)
    }

    @Test
    fun `horizontal and vertical stay independent`() {
        // The regression this whole file exists for: a face moving purely
        // sideways must produce X motion and NO Y motion. When the analysis
        // rotation was stale the two axes traded places and pure horizontal
        // movement showed up as vertical.
        val (x1, y1) = faceOffsetInScreenSpace(120f, 320f, 480, 640, mirrored = false)!!
        val (x2, y2) = faceOffsetInScreenSpace(360f, 320f, 480, 640, mirrored = false)!!
        assertTrue("X must respond to horizontal movement", x2 - x1 > 0.9f)
        assertEquals("Y must not move", y1, y2, 1e-6f)
    }

    @Test
    fun `offsets stay in range for boxes past the frame edge`() {
        // ML Kit can report a box whose center sits slightly outside the frame.
        val (x, y) = faceOffsetInScreenSpace(-40f, 900f, 480, 640, mirrored = false)!!
        assertTrue(x >= -1f && x <= 1f)
        assertTrue(y >= -1f && y <= 1f)
    }

    @Test
    fun `degenerate frame returns null instead of dividing by zero`() {
        assertNull(faceOffsetInScreenSpace(10f, 10f, 0, 640, mirrored = false))
        assertNull(faceOffsetInScreenSpace(10f, 10f, 480, 0, mirrored = false))
    }
}
