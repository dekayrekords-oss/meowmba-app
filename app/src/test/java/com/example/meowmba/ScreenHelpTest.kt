package com.example.meowmba

import com.example.meowmba.core.screen.ScreenHelp
import com.example.meowmba.core.screen.ScreenPixels
import com.example.meowmba.core.vision.VisionRouter
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class ScreenHelpTest {
    @Test fun packsPaddedRowsWithoutReadingMissingLastRowPadding() {
        val input = ByteBuffer.wrap(byteArrayOf(1,2,3,4,5,6,7,8,99,99,99,99,9,10,11,12,13,14,15,16))
        assertArrayEquals((1..16).map(Int::toByte).toByteArray(), ScreenPixels.pack(input, 2, 2, 12, 4))
        assertEquals(0, input.position())
    }
    @Test fun supportsPixelStrideAndNonzeroPlaneOffset() {
        val input = ByteBuffer.wrap(byteArrayOf(99,1,2,3,4,88,88,88,88,5,6,7,8)).apply { position(1) }
        assertArrayEquals((1..8).map(Int::toByte).toByteArray(), ScreenPixels.pack(input, 2, 1, 16, 8))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedPlanes() {
        ScreenPixels.pack(ByteBuffer.allocate(15), 2, 2, 8, 4)
    }
    @Test fun inputLimitRejectsRatherThanTruncatingAnImage() {
        assertArrayEquals(byteArrayOf(1,2,3), ScreenHelp.readBounded(ByteArrayInputStream(byteArrayOf(1,2,3)), 3))
        try { ScreenHelp.readBounded(ByteArrayInputStream(byteArrayOf(1,2,3,4)), 3); fail("Oversized images must fail") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun oldOrFutureSnapshotsAreNotReused() {
        assertTrue(ScreenHelp.fresh(100, 101))
        assertFalse(ScreenHelp.fresh(100, 100 + ScreenHelp.MAX_AGE_MS + 1))
        assertFalse(ScreenHelp.fresh(200, 100))
    }
    @Test fun spokenCameraRequestsActuallySelectScanner() {
        listOf("can you open the camera and get a QR code", "please scan this QR code", "read the barcode", "Meow scan a QR code").forEach {
            assertTrue(it, VisionRouter.parse(it)?.isQrQuery == true)
        }
        assertNull(VisionRouter.parse("how does a QR code scanner work"))
    }
}
