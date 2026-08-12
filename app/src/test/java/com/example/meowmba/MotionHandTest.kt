package com.example.meowmba

import com.example.meowmba.core.vision.MotionHand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class MotionHandTest {

    @Test
    fun `followPoint prefers the far edge of the moving patch`() {
        val w = 20
        val h = 10
        val prev = ByteArray(w * h)
        val curr = ByteArray(w * h)
        for (y in 3 until 7) {
            for (x in 10 until 19) {
                curr[y * w + x] = 120.toByte()
            }
        }
        val p = MotionHand.followPoint(prev, curr, w, h, origin = 0.2f to 0.5f)
        assertNotNull(p)
        assertTrue("hand tip should sit right of the arm, got x=${p!!.first}", p.first > 0.70f)
    }

    @Test
    fun `a moving patch on the right becomes a right-side centroid`() {
        val w = 20
        val h = 10
        val prev = ByteArray(w * h)
        val curr = ByteArray(w * h)
        // Bright square in the right half, lower third.
        for (y in 6 until 9) {
            for (x in 14 until 19) {
                curr[y * w + x] = 120.toByte()
            }
        }
        val c = MotionHand.centroid(prev, curr, w, h)
        assertNotNull(c)
        assertTrue("expected right side, got x=${c!!.first}", c.first > 0.6f)
        assertTrue("expected lower half, got y=${c.second}", c.second > 0.5f)
    }

    @Test
    fun `motion on the face is ignored`() {
        val w = 20
        val h = 10
        val prev = ByteArray(w * h)
        val curr = ByteArray(w * h)
        for (y in 4 until 7) {
            for (x in 8 until 12) {
                curr[y * w + x] = 120.toByte()
            }
        }
        assertNull(MotionHand.centroid(prev, curr, w, h, exclude = 0.5f to 0.5f, excludeR = 0.3f))
    }

    @Test
    fun `meanLuma is the average of the samples`() {
        assertEquals(0, com.example.meowmba.core.vision.MotionHand.meanLuma(byteArrayOf(0, 0)))
        assertEquals(50, com.example.meowmba.core.vision.MotionHand.meanLuma(byteArrayOf(0, 100)))
    }

    @Test
    fun `sampleY steps the luma plane`() {
        val width = 8
        val height = 4
        val buf = ByteBuffer.allocate(width * height)
        for (i in 0 until width * height) buf.put(i.toByte())
        buf.rewind()
        val sampled = MotionHand.sampleY(buf, width, height, rowStride = width, pixelStride = 1, step = 2)
        assertEquals(8, sampled.size)
        // (0,0)=0, (2,0)=2, (0,2)=16
        assertEquals(0.toByte(), sampled[0])
        assertEquals(2.toByte(), sampled[1])
        assertEquals(16.toByte(), sampled[4])
    }
}
