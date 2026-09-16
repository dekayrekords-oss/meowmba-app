package com.example.meowmba

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.ui.companion.CompanionSceneBackgroundPainter
import com.example.meowmba.ui.companion.isCompanionSceneBackground
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
class CompanionSceneBackgroundTest {
    private val entries = listOf(BackgroundAnimation.WOODLAND, BackgroundAnimation.LAGOON, BackgroundAnimation.RAINY_LOFT)

    private fun render(animation: BackgroundAnimation, width: Int, height: Int,
        clock: Float = .25f, slow: Float = .15f, painter: CompanionSceneBackgroundPainter = CompanionSceneBackgroundPainter()): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(width.toFloat(), height.toFloat())) {
            painter.draw(this, animation, clock, slow)
        }
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/reports/companion-backgrounds/$name"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun completeSceneSnapshotsFillPortraitLandscapeAndTinyPreviews() {
        val sheet = Bitmap.createBitmap(1620, 960, Bitmap.Config.ARGB_8888)
        entries.forEachIndexed { index, animation ->
            val portrait = render(animation, 540, 960)
            save(portrait, "${animation.id}-portrait.png")
            android.graphics.Canvas(sheet).drawBitmap(portrait, index * 540f, 0f, null)
            portrait.recycle()
            for ((width, height) in listOf(960 to 540, 160 to 120, 360 to 360, 900 to 280)) {
                val bitmap = render(animation, width, height)
                assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(width - 1, height - 1)))
                assertNotEquals("foreground and sky have depth", bitmap.getPixel(width / 2, height / 10), bitmap.getPixel(width / 2, height * 9 / 10))
                var maxCenter = 0
                for (x in width * 32 / 100..width * 68 / 100 step 7)
                    for (y in height * 30 / 100..height * 60 / 100 step 7) {
                        val c = bitmap.getPixel(x, y)
                        maxCenter = maxOf(maxCenter, android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
                    }
                assertTrue("quiet face clearing $animation", maxCenter < 135)
                if (width == 960) save(bitmap, "${animation.id}-landscape.png")
                bitmap.recycle()
            }
        }
        save(sheet, "contact.png"); sheet.recycle()
    }

    @Test fun scenesAreDeterministicAndAnimateWithoutChangingTheirLayout() {
        for (animation in entries) {
            val first = render(animation, 480, 320)
            val same = render(animation, 480, 320)
            val later = render(animation, 480, 320, .49f, .31f)
            assertTrue(first.sameAs(same))
            assertFalse("scene has live details: $animation", first.sameAs(later))
            assertEquals("center stays quiet", first.getPixel(240, 140), later.getPixel(240, 140))
            first.recycle(); same.recycle(); later.recycle()
        }
    }

    @Test fun cycleSeamsAndInvalidPhasesRemainDeterministic() {
        for (animation in entries) {
            val initial = render(animation, 240, 160, 0f, 0f)
            val wrapped = render(animation, 240, 160, 1f, 1f)
            val invalid = render(animation, 240, 160, Float.NaN, Float.POSITIVE_INFINITY)
            assertTrue(initial.sameAs(wrapped)); assertTrue(initial.sameAs(invalid))
            initial.recycle(); wrapped.recycle(); invalid.recycle()
        }
    }

    @Test fun onePainterCanBeResizedAndReusedForStaticPickerFrames() {
        val painter = CompanionSceneBackgroundPainter()
        for (animation in entries) {
            assertTrue(isCompanionSceneBackground(animation))
            val thumb = render(animation, 160, 120, painter = painter)
            val full = render(animation, 960, 540, painter = painter)
            val restored = render(animation, 160, 120, painter = painter)
            assertTrue(thumb.sameAs(restored))
            thumb.recycle(); full.recycle(); restored.recycle()
        }
        assertFalse(isCompanionSceneBackground(BackgroundAnimation.AURORA))
    }
}
