package com.example.meowmba

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.core.background.BackgroundSelection
import com.example.meowmba.ui.companion.PET_ENVIRONMENT_TABLE_Y
import com.example.meowmba.ui.companion.PetEnvironmentBackgroundPainter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PetEnvironmentBackgroundTest {
    private val entries = BackgroundAnimation.entries.filter { it.isPetEnvironment }

    private fun render(animation: BackgroundAnimation, width: Int = 320, height: Int = 480,
        clock: Float = .25f, slow: Float = .15f, painter: PetEnvironmentBackgroundPainter = PetEnvironmentBackgroundPainter()): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(width.toFloat(), height.toFloat())) {
            painter.draw(this, animation, clock, slow)
        }
        return bitmap
    }

    @Test fun tenNewEnvironmentsRoundTripAndAreDistinct() {
        assertEquals(10, entries.size)
        assertEquals(.78f, PET_ENVIRONMENT_TABLE_Y, 0f)
        val samples = entries.map { animation ->
            assertNull(animation.condition)
            assertEquals(animation, BackgroundSelection.parse("ANIMATION", animation.id, "0").animation)
            render(animation)
        }
        samples.forEachIndexed { index, sample ->
            samples.drop(index + 1).forEach { other -> assertFalse("distinct environment", sample.sameAs(other)) }
        }
        samples.forEach { it.recycle() }
    }

    @Test fun nativeContactSheetAndAspectRatiosStayOpaque() {
        val sheet = Bitmap.createBitmap(1600, 960, Bitmap.Config.ARGB_8888)
        entries.forEachIndexed { index, animation ->
            val portrait = render(animation)
            android.graphics.Canvas(sheet).drawBitmap(portrait, index % 5 * 320f, index / 5 * 480f, null)
            save(portrait, "${animation.id}-portrait.png")
            portrait.recycle()
            for ((width, height) in listOf(960 to 540, 160 to 120, 360 to 360, 900 to 280)) {
                val bitmap = render(animation, width, height)
                assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(width - 1, height - 1)))
                assertNotEquals(bitmap.getPixel(width / 2, height / 10), bitmap.getPixel(width / 2, height * 9 / 10))
                if (width == 960) save(bitmap, "${animation.id}-landscape.png")
                bitmap.recycle()
            }
        }
        save(sheet, "contact.png"); sheet.recycle()
    }

    @Test fun allScenesAnimateButNeverMoveTheirTabletop() {
        entries.forEach { animation ->
            val a = render(animation)
            val b = render(animation)
            val later = render(animation, clock = .61f, slow = .47f)
            assertTrue("repeatable $animation", a.sameAs(b))
            assertFalse("animated $animation", a.sameAs(later))
            for (y in 376 until 480 step 7) for (x in 0 until 320 step 9)
                assertEquals("stationary contact stage $animation", a.getPixel(x, y), later.getPixel(x, y))
            a.recycle(); b.recycle(); later.recycle()
        }
    }

    @Test fun normalizedPhasesAndReusablePainterSurviveResize() {
        val painter = PetEnvironmentBackgroundPainter()
        entries.forEach { animation ->
            val zero = render(animation, clock = 0f, slow = 0f, painter = painter)
            val seam = render(animation, clock = 1f, slow = 1f, painter = painter)
            val invalid = render(animation, clock = Float.NaN, slow = Float.POSITIVE_INFINITY, painter = painter)
            assertTrue("cycle wraps $animation", zero.sameAs(seam))
            assertTrue("invalid phase fallback $animation", zero.sameAs(invalid))
            val resized = render(animation, 160, 120, painter = painter)
            val restored = render(animation, clock = 0f, slow = 0f, painter = painter)
            assertTrue("resize does not retain stale geometry", zero.sameAs(restored))
            zero.recycle(); seam.recycle(); invalid.recycle(); resized.recycle(); restored.recycle()
        }
    }

    @Test fun tableHasMaterialDepthWithoutAnimatedContactPixels() {
        entries.forEach { animation ->
            val first = render(animation, 400, 600, clock = .1f, slow = .2f)
            val later = render(animation, 400, 600, clock = .7f, slow = .8f)
            val top = first.getPixel(200, 480)
            val bottom = first.getPixel(200, 590)
            assertNotEquals("table material has depth $animation", top, bottom)
            for (y in 478 until 600 step 5) {
                assertEquals("ambient details stay above interactive contact stage", first.getPixel(200, y), later.getPixel(200, y))
            }
            first.recycle(); later.recycle()
        }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/reports/pet-environments/$name")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
