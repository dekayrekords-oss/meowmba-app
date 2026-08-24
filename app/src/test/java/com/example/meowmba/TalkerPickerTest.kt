package com.example.meowmba

import com.example.meowmba.core.vision.SeenFace
import com.example.meowmba.core.vision.TalkerPick
import com.example.meowmba.core.vision.TalkerPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkerPickerTest {

    private fun face(
        id: Int,
        x: Float,
        mouth: Float,
        scale: Float = 1f,
    ) = SeenFace(trackingId = id, offsetX = x, offsetY = 0f, scale = scale, mouthOpen = mouth)

    @Test
    fun `no faces is none`() {
        val pick = TalkerPicker.pick(emptyList(), speechLikely = true, lastId = 1, nowMs = 10, lastAtMs = 0)
        assertNull(pick.face)
        assertEquals(TalkerPick.Reason.NONE, pick.reason)
    }

    @Test
    fun `a single face is always that person`() {
        val only = face(3, 0f, 0f)
        val pick = TalkerPicker.pick(listOf(only), speechLikely = false, lastId = null, nowMs = 0, lastAtMs = 0)
        assertEquals(3, pick.face?.trackingId)
        assertEquals(TalkerPick.Reason.LARGEST, pick.reason)
    }

    @Test
    fun `an open mouth wins while someone is speaking`() {
        val left = face(1, -0.4f, 0.04f)
        val right = face(2, 0.4f, 0.18f)
        val pick = TalkerPicker.pick(
            listOf(left, right),
            speechLikely = true,
            lastId = 1,
            nowMs = 1_000,
            lastAtMs = 900,
        )
        assertEquals(2, pick.face?.trackingId)
        assertEquals(TalkerPick.Reason.MOUTH, pick.reason)
    }

    @Test
    fun `two closed mouths do not steal on noise`() {
        val left = face(1, -0.4f, 0.02f, scale = 1.2f)
        val right = face(2, 0.4f, 0.03f, scale = 0.8f)
        val pick = TalkerPicker.pick(
            listOf(left, right),
            speechLikely = true,
            lastId = null,
            nowMs = 50,
            lastAtMs = 0,
        )
        assertEquals(1, pick.face?.trackingId)
        assertEquals(TalkerPick.Reason.LARGEST, pick.reason)
    }

    @Test
    fun `last talker is held briefly after they stop`() {
        val left = face(1, -0.4f, 0.02f)
        val right = face(2, 0.4f, 0.03f)
        val pick = TalkerPicker.pick(
            listOf(left, right),
            speechLikely = false,
            lastId = 2,
            nowMs = 500,
            lastAtMs = 10,
        )
        assertEquals(2, pick.face?.trackingId)
        assertEquals(TalkerPick.Reason.HOLD, pick.reason)
    }

    @Test
    fun `mouth opening is lip-to-chin over face height`() {
        // Lips at y=10, chin at y=16, face 40px tall → 6/40 = 0.15
        val open = TalkerPicker.mouthOpenRatio(
            mouthLeftY = 10f, mouthRightY = 10f, mouthBottomY = 16f, faceHeight = 40f,
        )
        assertEquals(0.15f, open, 0.001f)
        assertEquals(0f, TalkerPicker.mouthOpenRatio(10f, 10f, 10f, 40f), 0.001f)
        assertTrue(TalkerPicker.mouthOpenRatio(10f, 10f, 80f, 40f) <= 1f)
    }

    @Test
    fun `two faces get left and right labels`() {
        val left = face(1, -0.5f, 0f)
        val right = face(2, 0.4f, 0f)
        val both = listOf(left, right)
        assertEquals("Left", TalkerPicker.slotLabel(left, both))
        assertEquals("Right", TalkerPicker.slotLabel(right, both))
        assertNull(TalkerPicker.slotLabel(left, listOf(left)))
    }
}
