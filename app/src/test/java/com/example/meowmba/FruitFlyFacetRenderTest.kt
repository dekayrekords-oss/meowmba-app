package com.example.meowmba

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.meowmba.ui.companion.AvatarFormTraits
import com.example.meowmba.ui.companion.LumenFrame
import com.example.meowmba.ui.companion.LumenFruitFlyParts
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FruitFlyFacetRenderTest {
    private val optimized = LumenFruitFlyParts()
    private val originalFacet = Path().apply {
        repeat(6) { i ->
            val a = i * Math.PI / 3
            val x = cos(a).toFloat(); val y = sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    @Test fun facetsMatchOriginalPixelsAcrossMorphBlinkPaletteAndTransforms() {
        val sizes = listOf(44.375f to 59.625f, 83.125f to 77.875f, 117.625f to 143.375f)
        for (weight in listOf(.006f, .03f, .25f, .5f, .75f, 1f)) {
            for ((index, radii) in sizes.withIndex()) for (open in listOf(0f, .18f, 1.31f)) {
                for (light in listOf(false, true)) {
                    compare(radii.first, radii.second, weight, open, index, light)
                }
            }
        }
    }

    @Test fun reusedPaintHandlesChangingAlphaAndLeavesTheSameCanvasState() {
        // One painter survives all these changes, as it does while forms morph.
        val weights = listOf(1f, .006f, .5f, .03f, .75f, .25f, 1f)
        repeat(3) { pass ->
            for ((index, weight) in weights.withIndex()) {
                compare(81.125f + pass * 11.75f, 103.875f - index * 2.25f,
                    weight, if (index % 2 == 0) .83f else .04f, index % 3, pass % 2 == 0)
            }
        }
    }

    private fun compare(rx: Float, ry: Float, weight: Float, open: Float, transform: Int, light: Boolean) {
        val original = render(false, rx, ry, weight, open, transform, light)
        val actual = render(true, rx, ry, weight, open, transform, light)
        try {
            val description = "rx=$rx ry=$ry weight=$weight open=$open transform=$transform light=$light"
            assertTrue(description, original.bitmap.sameAs(actual.bitmap))
            assertArrayEquals("matrix after facets: $description", original.matrix, actual.matrix, 0f)
            assertEquals("clip after facets: $description", original.clip, actual.clip)
            assertEquals("draw bounds after facets: $description", original.size, actual.size)
        } finally {
            original.bitmap.recycle()
            actual.bitmap.recycle()
        }
    }

    private data class Capture(val bitmap: Bitmap, val matrix: FloatArray, val clip: Rect, val size: Size)

    private fun render(useOptimized: Boolean, rx: Float, ry: Float, weight: Float,
        open: Float, transform: Int, light: Boolean): Capture {
        val bitmap = Bitmap.createBitmap(420, 440, Bitmap.Config.ARGB_8888)
        val matrix = FloatArray(9)
        val clip = Rect()
        var restoredSize = Size.Zero
        val frame = LumenFrame(form = AvatarFormTraits(fruitFly = weight), perspective = transform - 1f)
        val color = if (light) Color(0xFF4FC7B1) else Color(0xFFC72F46)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(420f, 440f)) {
            drawRect(if (light) Color(0xFFF0EEEA) else Color(0xFF0B131B))
            translate(210.375f, 220.625f) {
                rotate((transform - 1) * 17.25f, Offset.Zero) {
                    scale(if (transform == 2) -.91f else 1.13f,
                        if (transform == 1) .79f else 1.07f, Offset.Zero) {
                        // Same cubic aperture construction used by the lens, with
                        // asymmetric brow/lid values so clipping exercises thin slivers.
                        val upper = ry * (.10f - 1.10f * open)
                        val lower = ry * (.10f + (.88f - .31f * .17f - .13f) * open)
                        val inner = .21f * ry * open
                        val leftBrow = upper + inner * .23f
                        val rightBrow = upper - inner * .55f
                        val corner = .70f + .27f * .10f
                        val shoulder = .58f
                        val aperture = Path().apply {
                            moveTo(-rx, 0f)
                            cubicTo(-rx, leftBrow * corner, -rx * shoulder, leftBrow, -rx * .04f, upper)
                            cubicTo(rx * shoulder, rightBrow, rx, rightBrow * corner, rx, 0f)
                            cubicTo(rx, lower * (corner - .04f), rx * shoulder, lower, rx * .02f, lower)
                            cubicTo(-rx * shoulder, lower, -rx, lower * (corner - .02f), -rx, 0f)
                            close()
                        }
                        clipPath(aperture) {
                            if (useOptimized) optimized.compoundEye(this, rx, ry, frame, color)
                            else originalCompoundEye(rx, ry, frame, color)
                            val native = drawContext.canvas.nativeCanvas
                            val current = Matrix()
                            @Suppress("DEPRECATION")
                            native.getMatrix(current)
                            current.getValues(matrix)
                            native.getClipBounds(clip)
                            restoredSize = size
                            // Exercise following draw calls with different paint
                            // settings, plus an off-aperture mark that must stay clipped.
                            drawLine(Color(0xBBDD4CC1), Offset(-rx, 3.125f), Offset(rx, 13.875f),
                                2.375f, StrokeCap.Round)
                            drawCircle(Color.Yellow, 9.25f, Offset(rx * 1.4f, 0f))
                        }
                        drawCircle(Color(0xFF51A3E7), 3.75f, Offset(rx * 1.2f, -ry * 1.1f))
                    }
                }
            }
            drawRect(Color(0xFFA7E57A), Offset(7.25f, 11.75f), Size(9.5f, 6.25f))
        }
        return Capture(bitmap, matrix, clip, restoredSize)
    }

    /** Frozen pre-147 implementation: independent oracle for exact rendering. */
    private fun DrawScope.originalCompoundEye(rx: Float, ry: Float, f: LumenFrame, color: Color) {
        val a = f.form.fruitFly
        val sideShade = (f.perspective * .12f).coerceIn(-.12f, .12f)
        drawRect(Brush.radialGradient(listOf(lerp(color, Color.White, .22f + sideShade), color,
            lerp(color, Color(0xFF290D1B), .72f)), Offset(-rx * .24f, -ry * .30f), ry * 1.8f),
            Offset(-rx, -ry * 1.5f), Size(rx * 2f, ry * 3f), alpha = a)
        val radius = rx * .105f
        for (row in -7..7) for (column in -7..7) {
            val x = column * radius * 1.5f
            val y = (row + if (column % 2 == 0) 0f else .5f) * radius * 1.732f
            if (x * x / (rx * rx) + y * y / (ry * ry * 1.5f) < 1.35f) {
                translate(x, y) { scale(radius, radius, Offset.Zero) {
                    drawPath(originalFacet, Color(0xFFFFBAAA).copy(alpha = a * .17f), style = Stroke(.055f))
                } }
            }
        }
    }
}
