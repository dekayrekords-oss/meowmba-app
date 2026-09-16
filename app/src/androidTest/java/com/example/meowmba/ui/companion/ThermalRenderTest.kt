package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Real Android raster comparison: caching must not trade away glow detail. */
@RunWith(AndroidJUnit4::class)
class ThermalRenderTest {
    @Test fun analyticGlowMatchesOriginalAtDifferentScalesAndAlpha() {
        for (color in listOf(Color.White, Color.Green, Color(0xFFFF85A0))) {
            for (radius in listOf(2.3f, 21.8f, 93f)) {
                for (alpha in listOf(0.08f, 0.5f, 1f)) {
                    val original = render(color, radius, alpha, cached = false)
                    val optimized = render(color, radius, alpha, cached = true)
                    var totalDifference = 0L
                    var maxDifference = 0
                    for (i in original.indices) for (shift in listOf(0, 8, 16)) {
                        val difference = abs(((original[i] ushr shift) and 255) - ((optimized[i] ushr shift) and 255))
                        totalDifference += difference
                        maxDifference = maxOf(maxDifference, difference)
                    }
                    val average = totalDifference.toDouble() / (original.size * 3)
                    assertTrue("radius=$radius alpha=$alpha mean=$average max=$maxDifference",
                        average < 0.5 && maxDifference <= 8)
                }
            }
        }
    }

    private fun render(color: Color, radius: Float, alpha: Float, cached: Boolean): IntArray {
        val image = ImageBitmap(256, 256)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(256f, 256f)) {
            drawRect(Color(0xFF0D1117))
            val center = Offset(122.7f, 130.3f)
            if (cached) drawRadialGlow(center, radius, RadialGlow.brush(color), alpha)
            else drawCircle(Brush.radialGradient(
                listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center, radius), radius, center)
        }
        val bitmap = image.asAndroidBitmap()
        return IntArray(256 * 256).also { bitmap.getPixels(it, 0, 256, 0, 0, 256, 256) }
    }
}
