package com.example.meowmba

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.meowmba.ui.companion.*
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PearlRenderTest {
    @Test fun `pearl renders floating features in chosen colors`() {
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr,
            Canvas(bitmap.asImageBitmap()), Size(800f, 800f)) {
            drawRect(Color(0xFF101719))
            drawGodotCatFace(Offset(400f, 410f), 230f, CatMotionEngine().restPose(),
                GodotFaceClock(1f, 1f, 0f, 0f, 0.1f, 0f, 0f),
                eyeColor = Color(0xFF39A8A5), glowColor = Color(0xFF39A8A5),
                pearl = true, featureColor = Color(0xFFE8D5B9))
        }
        assertNotEquals(bitmap.getPixel(0, 0), bitmap.getPixel(400, 410))
        val output = File("build/reports/appearance/pearl.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
