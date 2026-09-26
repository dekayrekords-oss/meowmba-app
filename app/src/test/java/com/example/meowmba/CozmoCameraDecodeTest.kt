package com.example.meowmba

import android.graphics.Bitmap
import com.example.meowmba.core.robot.cozmo.decodeCozmoFrame
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CozmoCameraDecodeTest {
    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
    @Test fun cameraDecodesOnlyExpectedDimensions() {
        val frame = decodeCozmoFrame(jpeg(320, 240))!!
        assertEquals(320, frame.width)
        assertEquals(240, frame.height)
        frame.recycle()
        assertNull(decodeCozmoFrame(jpeg(640, 480)))
        assertNull(decodeCozmoFrame(jpeg(240, 320)))
    }
    @Test fun malformedFramesAreDroppedWithoutThrowing() {
        assertNull(decodeCozmoFrame(byteArrayOf()))
        assertNull(decodeCozmoFrame(byteArrayOf(-1, -40, 0, 1, 2)))
    }
}
