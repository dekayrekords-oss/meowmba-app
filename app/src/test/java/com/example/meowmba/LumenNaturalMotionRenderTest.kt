package com.example.meowmba

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LumenNaturalMotionRenderTest {
    private val painter = LumenPainter()

    private fun render(frame: LumenFrame, width: Int, height: Int, privacy: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
            Size(width.toFloat(), height.toFloat())) {
            painter.draw(this, frame, Color(0xFFF0B967), Color(0xFFDB9C50),
                featureColor = Color(0xFFDFBC8C), cameraOff = privacy, micOff = privacy)
        }
        return bitmap
    }

    @Test fun widestDriftFitsAllFormsAndStageShapesWithPrivacyVisible() {
        for ((width, height) in listOf(360 to 800, 360 to 360, 720 to 520, 1600 to 500, 280 to 160)) {
            for (animal in AvatarAnimal.entries) for (side in listOf(-1f, 1f)) for (vertical in listOf(-1f, 1f)) {
                val traits = AvatarFormProfiles.traits(animal)
                val f = LumenFrame(gazeX = side, gazeY = vertical, headX = side * .42f, headY = vertical * .3f,
                    driftX = side, driftY = vertical, roll = side * 18f, earLift = 15f,
                    form = traits, cat = traits.pointedEars, dog = traits.floppyEars)
                val b = render(f, width, height)
                val label = "$animal $width/$height drift=$side/$vertical"
                for (x in 0 until width) {
                    assertTrue("top $label", android.graphics.Color.alpha(b.getPixel(x, 0)) < 110)
                    assertTrue("bottom $label", android.graphics.Color.alpha(b.getPixel(x, height - 1)) < 110)
                }
                for (y in 0 until height) {
                    assertTrue("left $label", android.graphics.Color.alpha(b.getPixel(0, y)) < 110)
                    assertTrue("right $label", android.graphics.Color.alpha(b.getPixel(width - 1, y)) < 110)
                }
                val private = render(f, width, height, privacy = true)
                assertFalse("privacy remains drawn $label", b.sameAs(private))
                b.recycle(); private.recycle()
            }
        }
    }

    @Test fun optionalPreviewUsesProductionIdleTrackingAndAnimalRig() {
        if (System.getenv("MEOW_NATURAL_RENDER_FRAMES") != "1") return
        val directory = File("build/reports/lumen-natural/frames").apply { mkdirs() }
        val rig = LumenRig()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD7C6AF.toInt(); textSize = 19f }
        repeat(900) { i ->
            val t = i / 30f
            val animal = when { t < 12f -> AvatarAnimal.NONE; t < 18f -> AvatarAnimal.CAT
                t < 22f -> AvatarAnimal.DOG; t < 26f -> AvatarAnimal.SQUIRREL; else -> AvatarAnimal.RACCOON }
            val input = when {
                t < 12f -> LumenInput()
                t < 14f -> LumenInput(.75f, -.25f, true, animal = animal)
                t < 16f -> LumenInput(-.75f, .25f, true, animal = animal)
                t < 18f -> LumenInput(.55f, -.4f, true, animal = animal)
                t < 20f -> LumenInput(animal = animal) // Stale face expires; no invented velocity.
                t < 22f -> LumenInput(.3f, -.15f, true, animal = animal)
                else -> LumenInput(animal = animal)
            }
            val f = rig.step(t, input)
            val face = render(f, 720, 460, privacy = !input.cameraEnabled)
            val sheet = Bitmap.createBitmap(720, 520, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(sheet)
            canvas.drawColor(0xFF0B131B.toInt()); canvas.drawBitmap(face, 0f, 0f, null)
            val label = when { t < 12f -> "Purposeful idle / glances, holds and recovery"
                t < 18f -> "Fast tracking / eyes acquire, whole pair follows"
                t < 20f -> "Face loss / smooth release, no prediction"
                t < 22f -> "Reacquired / same rig and active clock"
                else -> "One character / ${animal.label} / quiet idle" }
            canvas.drawText(label, 24f, 473f, text)
            text.textSize = 14f
            canvas.drawText("Native preview / scripted attention, not live-camera evidence", 24f, 503f, text)
            text.textSize = 19f
            File(directory, "${i.toString().padStart(4, '0')}.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            face.recycle(); sheet.recycle()
        }
    }
}
