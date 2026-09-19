package com.example.meowmba

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.core.background.BackgroundSelection
import com.example.meowmba.ui.companion.ScenicBackgroundPainter
import com.example.meowmba.ui.companion.isScenicBackground
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScenicBackgroundTest {
    private val scenes = BackgroundAnimation.entries.filter(::isScenicBackground)

    private fun render(painter: ScenicBackgroundPainter, width: Int = 320, height: Int = 480,
        clock: Float = .25f, slow: Float = .15f, stillOnly: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(width.toFloat(), height.toFloat())) {
            if (stillOnly) painter.drawStill(this) else painter.draw(this, clock, slow)
        }
        return bitmap
    }

    @Test fun fiveStableIdsRoundTripWithoutMovingAnyExistingOrdinalOrHabitat() {
        val expected = listOf("saffron_dunes", "alpine_cableway", "coral_twilight", "lantern_courtyard", "crystal_cavern")
        assertEquals(expected, scenes.map { it.id })
        assertEquals(BackgroundAnimation.entries.size, BackgroundAnimation.entries.map { it.id }.distinct().size)
        val originalIds = listOf("aurora", "starfield", "nebula", "embers", "woodland", "lagoon", "rainy_loft",
            "clear", "clouds", "rain", "snow", "fog", "dust", "storm", "kitchen", "library", "greenhouse",
            "workshop", "pond", "orchard", "cafe", "observatory", "beach", "bedroom", "cartoon_nook",
            "cartoon_galley", "cartoon_snack_lab", "cartoon_laundromat", "cartoon_diner", "cartoon_portal_arcade",
            "cartoon_moon_garden", "cartoon_alien_aquarium", "cartoon_meteor_garage")
        originalIds.forEachIndexed { ordinal, id -> assertEquals("old seeded artwork must not change", ordinal, BackgroundAnimation.byId(id)!!.ordinal) }
        scenes.forEach {
            val parsed = BackgroundSelection.parse("ANIMATION", it.id, "47")
            assertEquals(it, parsed.animation)
            assertEquals(47, parsed.dimPercent)
            assertFalse(it.isPetEnvironment)
            assertFalse(it.isCartoonEnvironment)
            assertNull(it.condition)
        }
        assertEquals(19, BackgroundAnimation.entries.count { it.isPetEnvironment })
        assertEquals(BackgroundSelection(), BackgroundSelection.parse("ANIMATION", "missing_scene", "47"))
    }

    @Test fun nativePortraitLandscapeSquareAndPickerFramesAreOpaqueDistinctAndQuiet() {
        val sheet = Bitmap.createBitmap(1600, 480, Bitmap.Config.ARGB_8888)
        val portraits = scenes.mapIndexed { index, animation ->
            val painter = ScenicBackgroundPainter(animation)
            val portrait = render(painter)
            save(portrait, "${animation.id}-portrait.png")
            android.graphics.Canvas(sheet).drawBitmap(portrait, index * 320f, 0f, null)
            for ((width, height) in listOf(960 to 540, 160 to 96, 360 to 360, 900 to 280)) {
                val image = render(painter, width, height)
                for ((x, y) in listOf(0 to 0, width - 1 to 0, 0 to height - 1, width - 1 to height - 1))
                    assertEquals("opaque viewport $animation $width x $height", 255, android.graphics.Color.alpha(image.getPixel(x, y)))
                assertNotEquals("foreground depth $animation", image.getPixel(width / 2, height / 10), image.getPixel(width / 2, height * 9 / 10))
                for (x in width * 32 / 100..width * 68 / 100 step 9)
                    for (y in height * 45 / 100..height * 60 / 100 step 9) {
                        val color = image.getPixel(x, y)
                        assertTrue("quiet face clearing $animation", maxOf(android.graphics.Color.red(color),
                            android.graphics.Color.green(color), android.graphics.Color.blue(color)) < 145)
                    }
                if (width == 960) {
                    save(image, "${animation.id}-landscape.png")
                    val later = render(painter, width, height, .61f, .47f)
                    save(later, "${animation.id}-landscape-frame2.png")
                    later.recycle()
                }
                image.recycle()
            }
            portrait
        }
        portraits.forEachIndexed { index, image ->
            portraits.drop(index + 1).forEach { other -> assertFalse("original distinct art", image.sameAs(other)) }
        }
        portraits.forEach(Bitmap::recycle)
        save(sheet, "contact.png")
        sheet.recycle()
    }

    @Test fun animationIsDeterministicWhileTheRetainedArchitectureStaysStill() {
        scenes.forEach { animation ->
            val painter = ScenicBackgroundPainter(animation)
            val first = render(painter)
            val repeated = render(ScenicBackgroundPainter(animation))
            val later = render(painter, clock = .61f, slow = .47f)
            val still = render(painter, stillOnly = true)
            val stillLater = render(painter, clock = .61f, slow = .47f, stillOnly = true)
            save(later, "${animation.id}-portrait-frame2.png")
            assertTrue("deterministic $animation", first.sameAs(repeated))
            assertFalse("visible motion $animation", first.sameAs(later))
            assertTrue("stationary retained artwork $animation", still.sameAs(stillLater))
            listOf(first, repeated, later, still, stillLater).forEach(Bitmap::recycle)
        }
    }

    @Test fun clockWrapInvalidInputsAndRepeatedResizeDoNotCorruptThePainter() {
        scenes.forEach { animation ->
            val painter = ScenicBackgroundPainter(animation)
            val zero = render(painter, clock = 0f, slow = 0f)
            val seam = render(painter, clock = 1f, slow = 1f)
            val invalid = render(painter, clock = Float.NaN, slow = Float.NEGATIVE_INFINITY)
            val wrapped = render(painter, clock = 2.25f, slow = -1.75f)
            val fractional = render(painter, clock = .25f, slow = .25f)
            render(painter, 900, 280).recycle()
            render(painter, 160, 96).recycle()
            val restored = render(painter, clock = 0f, slow = 0f)
            assertTrue("exact cycle seam $animation", zero.sameAs(seam))
            assertTrue("invalid phase fallback $animation", zero.sameAs(invalid))
            assertTrue("normalized phases $animation", wrapped.sameAs(fractional))
            assertTrue("no stale viewport cache $animation", zero.sameAs(restored))
            listOf(zero, seam, invalid, wrapped, fractional, restored).forEach(Bitmap::recycle)
        }
    }

    @Test fun movingLayerUsesAtMost64NativePrimitivesAtEverySampledPhase() {
        val bitmap = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)
        val canvas = CountingCanvas(Canvas(bitmap.asImageBitmap()))
        val scope = CanvasDrawScope()
        scenes.forEach { animation ->
            val painter = ScenicBackgroundPainter(animation)
            repeat(24) { frame ->
                canvas.primitives = 0
                scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(320f, 480f)) {
                    painter.drawMotion(this, frame / 24f, frame / 19f)
                }
                assertTrue("bounded detail work $animation: ${canvas.primitives}", canvas.primitives in 1..64)
            }
        }
        bitmap.recycle()
    }

    private class CountingCanvas(private val delegate: Canvas) : Canvas by delegate {
        var primitives = 0
        override fun drawCircle(center: Offset, radius: Float, paint: Paint) {
            primitives++; delegate.drawCircle(center, radius, paint)
        }
        override fun drawLine(p1: Offset, p2: Offset, paint: Paint) {
            primitives++; delegate.drawLine(p1, p2, paint)
        }
        override fun drawPath(path: Path, paint: Paint) {
            primitives++; delegate.drawPath(path, paint)
        }
        override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float, paint: Paint) {
            primitives++; delegate.drawRoundRect(left, top, right, bottom, radiusX, radiusY, paint)
        }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/reports/scenic-backgrounds/$name")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
