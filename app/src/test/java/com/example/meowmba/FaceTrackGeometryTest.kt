package com.example.meowmba

import com.example.meowmba.core.vision.faceOffsetInScreenSpace
import com.example.meowmba.core.vision.faceOffsetOnDisplay
import com.example.meowmba.core.vision.analyzerTargetRotation
import com.example.meowmba.core.vision.remapEulerToDisplay
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

    // ── landscape: CameraX reports rot=0 while the display is ROTATION_90 ──
    // Measured on the Fold 7 cover (2520x1080, target=1, buf=320x240, faces=1):
    // the detector saw the user, but published y≈-0.8 / roll≈-35 because the
    // box was still in SENSOR space. Left/right on the landscape screen must
    // move gaze X, not pin the head to the top of the frame.

    @Test
    fun `when CameraX already matched the display, do not swap axes again`() {
        // Fold cover landscape: cam=0, target=ROTATION_90, disp=90.
        // Buffer is already display-aligned. Remapping would send left/right
        // onto Y and the head would not follow.
        val rot90 = android.view.Surface.ROTATION_90
        val (x1, y1) = faceOffsetOnDisplay(
            boxCenterX = 40f, boxCenterY = 120f,
            bufferW = 320, bufferH = 240,
            rotationDegrees = 0, displayDeg = 90, mirrored = false,
            targetRotation = rot90,
        )!!
        val (x2, y2) = faceOffsetOnDisplay(
            boxCenterX = 280f, boxCenterY = 120f,
            bufferW = 320, bufferH = 240,
            rotationDegrees = 0, displayDeg = 90, mirrored = false,
            targetRotation = rot90,
        )!!
        assertTrue("buffer-X must stay display-X, dx=${x2 - x1}", x2 - x1 > 0.9f)
        assertEquals("must not leak onto Y", y1, y2, 1e-4f)
    }

    @Test
    fun `rotation-lock still remaps sensor Y onto display X`() {
        val bufW = 320
        val bufH = 240
        val (xTop, yTop) = faceOffsetOnDisplay(
            boxCenterX = 160f, boxCenterY = 20f,
            bufferW = bufW, bufferH = bufH,
            rotationDegrees = 0, displayDeg = 90, mirrored = false,
            targetRotation = android.view.Surface.ROTATION_0,
        )!!
        val (xBot, yBot) = faceOffsetOnDisplay(
            boxCenterX = 160f, boxCenterY = 220f,
            bufferW = bufW, bufferH = bufH,
            rotationDegrees = 0, displayDeg = 90, mirrored = false,
            targetRotation = android.view.Surface.ROTATION_0,
        )!!
        assertTrue(
            "sensor-Y motion must become display-X, dx=${xBot - xTop}",
            kotlin.math.abs(xBot - xTop) > 0.9f,
        )
        assertEquals("that motion must not leak onto display-Y", yTop, yBot, 0.08f)
    }

    @Test
    fun `front-lens pointInDisplay01 mirrors X the same way as face offsets`() {
        val face = faceOffsetOnDisplay(
            boxCenterX = 40f, boxCenterY = 120f,
            bufferW = 320, bufferH = 240,
            rotationDegrees = 0, displayDeg = 0, mirrored = true,
        )!!
        val pt = com.example.meowmba.core.vision.pointInDisplay01(
            px = 40f, py = 120f,
            bufferW = 320, bufferH = 240,
            rotationDegrees = 0, displayDeg = 0, mirrored = true,
        )!!
        assertEquals(face.first, pt.first * 2f - 1f, 1e-4f)
        assertEquals(face.second, pt.second * 2f - 1f, 1e-4f)
    }

    @Test
    fun `portrait with a real ML Kit rotation stays identity`() {
        // rot=270, display=0 is the working portrait path: boxes are already
        // in the rotated (screen) frame. Remapping them again would swap axes.
        val (w, h) = rotatedFrameSize(320, 240, 270)
        val (x1, y1) = faceOffsetOnDisplay(
            boxCenterX = 60f, boxCenterY = h / 2f,
            bufferW = 320, bufferH = 240,
            rotationDegrees = 270, displayDeg = 0, mirrored = false,
        )!!
        val (x2, y2) = faceOffsetOnDisplay(
            boxCenterX = w - 60f, boxCenterY = h / 2f,
            bufferW = 320, bufferH = 240,
            rotationDegrees = 270, displayDeg = 0, mirrored = false,
        )!!
        assertTrue("portrait X must still respond", x2 - x1 > 0.5f)
        assertEquals("portrait Y must stay put", y1, y2, 1e-4f)
    }

    @Test
    fun `analyzer follows the window, not a noisy IMU, when the display actually rotated`() {
        val rot90 = android.view.Surface.ROTATION_90
        val rot0 = android.view.Surface.ROTATION_0
        val rot270 = android.view.Surface.ROTATION_270
        assertEquals(rot90, analyzerTargetRotation(rot90, rot0))
        assertEquals(rot90, analyzerTargetRotation(rot90, rot270))
        assertEquals(rot90, analyzerTargetRotation(rot90, null))
        // Rotation-lock: display stuck at 0, phone on its side — then use IMU.
        assertEquals(rot90, analyzerTargetRotation(rot0, rot90))
        assertEquals(rot0, analyzerTargetRotation(rot0, rot0))
        assertEquals(rot0, analyzerTargetRotation(rot0, null))
        assertEquals(rot270, analyzerTargetRotation(null, rot270))
    }

    @Test
    fun `sensor yaw becomes display pitch only when we actually remapped`() {
        val (yaw, pitch, roll) = remapEulerToDisplay(
            yaw = 20f, pitch = 5f, roll = 3f,
            rotationDegrees = 0, displayDeg = 90,
            targetRotation = android.view.Surface.ROTATION_0,
        )
        assertEquals(5f, yaw, 1e-4f)
        assertEquals(-20f, pitch, 1e-4f)
        assertEquals(3f, roll, 1e-4f)
        val identity = remapEulerToDisplay(
            yaw = 20f, pitch = 5f, roll = 3f,
            rotationDegrees = 0, displayDeg = 90,
            targetRotation = android.view.Surface.ROTATION_90,
        )
        assertEquals(20f, identity.first, 1e-4f)
        assertEquals(5f, identity.second, 1e-4f)
    }

    @Test
    fun `euler remap is identity once ML Kit already rotated`() {
        val (yaw, pitch, roll) = remapEulerToDisplay(
            yaw = 20f, pitch = 5f, roll = 3f,
            rotationDegrees = 90, displayDeg = 90,
        )
        assertEquals(20f, yaw, 1e-4f)
        assertEquals(5f, pitch, 1e-4f)
        assertEquals(3f, roll, 1e-4f)
    }

    // ── smoothing: a stream to integrate, not a staircase ───────────────────

    @Test
    fun `following converges without ever stepping a fixed distance`() {
        // The reported symptom: the head lurched in discrete increments. A
        // maxStep clamp capped travel at 0.14 per sample regardless of how far
        // the face had moved, so every fast movement produced equal-sized
        // stairs. An EMA step is proportional to the distance REMAINING, so
        // each step must be strictly smaller than the last.
        var smoothed = 0f
        val target = 1f
        val steps = mutableListOf<Float>()
        repeat(12) {
            val before = smoothed
            smoothed = com.example.meowmba.core.vision.smoothFaceOffset(
                smoothed, target, com.example.meowmba.core.vision.faceSmoothingAlpha(true),
            )
            steps += smoothed - before
        }
        for (i in 1 until steps.size) {
            assertTrue(
                "step $i (${steps[i]}) must be smaller than step ${i - 1} (${steps[i - 1]})",
                steps[i] < steps[i - 1],
            )
        }
        assertTrue("must actually converge, got $smoothed", smoothed > 0.98f)
    }

    @Test
    fun `a small movement is not swallowed`() {
        // Slow movement used to clear no threshold at all and be discarded,
        // then arrive as a jump once drift accumulated.
        val moved = com.example.meowmba.core.vision.smoothFaceOffset(
            0f, 0.02f, com.example.meowmba.core.vision.faceSmoothingAlpha(true),
        )
        assertTrue("a 0.02 movement must produce some motion, got $moved", moved > 0f)
        assertTrue("and must not overshoot it", moved < 0.02f)
    }

    @Test
    fun `the first sample of a new face is trusted more than later ones`() {
        // Otherwise the head eases in from wherever the previous face left off,
        // sweeping across the screen when someone new steps into frame.
        assertTrue(
            com.example.meowmba.core.vision.faceSmoothingAlpha(false) >
                com.example.meowmba.core.vision.faceSmoothingAlpha(true)
        )
    }

    @Test
    fun `smoothing never overshoots the target`() {
        for (alpha in listOf(0.32f, 0.65f)) {
            var v = -1f
            repeat(200) { v = com.example.meowmba.core.vision.smoothFaceOffset(v, 1f, alpha) }
            assertTrue("overshot: $v", v <= 1f + 1e-4f)
        }
    }
}
