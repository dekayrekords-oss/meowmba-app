package com.example.meowmba

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeneratedPropRenderTest {
    @Test fun propsRenderStayBoundedAndRespectHiddenPresence() {
        val painter = GeneratedAvatarPainter()
        for (category in GeneratedPropCategory.entries) {
            val prop = GeneratedPropForm("fixture", "Cup", "", category, "",
                GeneratedAvatarExamples.coffeeCup().parts.take(2))
            for ((w, h) in listOf(360 to 800, 800 to 360)) {
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                fun draw(presence: Float) {
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr,
                        Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
                        painter.drawProp(this, prop, LumenFrame(presence = presence, roll = 24f), .4f,
                            Offset(w * .5f, h * .5f), minOf(w, h) * .7f)
                    }
                }
                try {
                    draw(0f)
                    val pixels = IntArray(w * h)
                    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                    assertTrue(pixels.all { it == 0 })
                    draw(1f)
                    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                    assertTrue("$category must visibly render", pixels.count { it != 0 } > 100)
                    assertTrue((0 until w).all { bitmap.getPixel(it, 0) == 0 && bitmap.getPixel(it, h - 1) == 0 })
                    assertTrue((0 until h).all { bitmap.getPixel(0, it) == 0 && bitmap.getPixel(w - 1, it) == 0 })
                } finally { bitmap.recycle() }
            }
        }
    }
}
