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
class LumenRenderTest {
    private val painter = LumenPainter()
    private fun render(f: LumenFrame, title: String, light: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(960, 720, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(960f, 720f)) {
            drawRect(if (light) Color(0xFFECEEE9) else Color(0xFF0B131B))
            painter.draw(this, f, Color(0xFF58B9AE), Color(0xFF58B9AE))
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (light) 0xFF344B50.toInt() else 0xFFAEC2C4.toInt(); textSize = 22f; letterSpacingCompat() }
        android.graphics.Canvas(bitmap).drawText(title, 40f, 660f, text)
        return bitmap
    }
    private fun Paint.letterSpacingCompat() { typeface = android.graphics.Typeface.create("sans-serif-medium", 0) }
    private fun save(bitmap: Bitmap, path: String) {
        val file = File(path); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun lensesAndCostumesRenderInBothThemes() {
        val scenes = listOf(
            "LUMEN / attentive" to LumenInput(cameraEnabled = true),
            "LUMEN / curious" to LumenInput(0.5f, -0.15f, true, emotion = CompanionEmotion.CURIOUS),
            "LUMEN / delighted" to LumenInput(emotion = CompanionEmotion.HAPPY),
            "LUMEN / dog performance" to LumenInput(emotion = CompanionEmotion.CURIOUS, animal = AvatarAnimal.DOG),
            "LUMEN / cat performance" to LumenInput(emotion = CompanionEmotion.HAPPY, animal = AvatarAnimal.CAT),
            "LUMEN / sleepy" to LumenInput(emotion = CompanionEmotion.SLEEPY),
            "LUMEN / listening" to LumenInput(interaction = InteractionState.LISTENING),
            "LUMEN / puzzled" to LumenInput(emotion = CompanionEmotion.CONFUSED),
            "LUMEN / light appearance" to LumenInput(),
        )
        val sheet = Bitmap.createBitmap(1440, 1080, Bitmap.Config.ARGB_8888)
        scenes.forEachIndexed { index, (label, input) ->
            val rig = LumenRig(); rig.step(0f, input)
            var f = LumenFrame()
            repeat(100) { f = rig.step((it + 1f) / 60, input) }
            val b = render(f, label, index == 8)
            assertNotEquals(b.getPixel(0, 0), b.getPixel(290, 325))
            save(b, "build/reports/lumen/scene-$index.png")
            android.graphics.Canvas(sheet).drawBitmap(b, null, android.graphics.Rect(index % 3 * 480, index / 3 * 360, index % 3 * 480 + 480, index / 3 * 360 + 360), null)
            b.recycle()
        }
        save(sheet, "build/reports/lumen/contact.png"); sheet.recycle()
    }

    @Test fun optionalMotionPreviewUsesProductionRigAndPainter() {
        if (System.getenv("MEOW_LUMEN_RENDER_FRAMES") != "1") return
        val rig = LumenRig()
        repeat(420) { i ->
            val t = i / 30f
            val input = when {
                t < 2 -> LumenInput(0f, 0f, true)
                t < 4 -> LumenInput(0.75f, -0.22f, true, emotion = CompanionEmotion.CURIOUS)
                t < 7 -> LumenInput(0f, 0f, true, emotion = CompanionEmotion.HAPPY, animal = AvatarAnimal.CAT)
                t < 10 -> LumenInput(-0.3f, 0.05f, true, emotion = CompanionEmotion.CURIOUS, animal = AvatarAnimal.DOG)
                t < 12 -> LumenInput(0f, 0f, true, interaction = InteractionState.LISTENING)
                else -> LumenInput(emotion = CompanionEmotion.SLEEPY)
            }
            val title = when { t < 2 -> "LUMEN  /  eyes only"; t < 4 -> "Gaze leads; head follows"; t < 7 -> "Cat performance"; t < 10 -> "Dog performance"; t < 12 -> "Listening"; else -> "A soft settle" }
            val bitmap = render(rig.step(t, input), title)
            save(bitmap, "build/reports/lumen/frames/${i.toString().padStart(4, '0')}.png")
            bitmap.recycle()
        }
    }

    @Test fun completeCostumeSilhouettesFitPortraitCoverAndWideStages() {
        for ((w, h) in listOf(360 to 800, 720 to 520, 1600 to 500, 360 to 360)) {
            for (animal in listOf(AvatarAnimal.CAT, AvatarAnimal.DOG)) {
                for (roll in listOf(-18f, 18f)) {
                    val frame = LumenFrame(headX = 0.65f, headY = -0.30f, roll = roll,
                        cat = if (animal == AvatarAnimal.CAT) 1f else 0f,
                        dog = if (animal == AvatarAnimal.DOG) 1f else 0f, earLift = 15f)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
                        painter.draw(this, frame, Color(0xFF58B9AE), Color(0xFF58B9AE))
                    }
                    // Halos may fade through the edge; opaque lenses and ear surfaces must not crop.
                    for (x in 0 until w) {
                        assertTrue("top $w x $h $animal $roll at $x", android.graphics.Color.alpha(bitmap.getPixel(x, 0)) < 110)
                        assertTrue("bottom", android.graphics.Color.alpha(bitmap.getPixel(x, h - 1)) < 110)
                    }
                    for (y in 0 until h) {
                        assertTrue("left", android.graphics.Color.alpha(bitmap.getPixel(0, y)) < 110)
                        assertTrue("right $w x $h $animal $roll at $y", android.graphics.Color.alpha(bitmap.getPixel(w - 1, y)) < 110)
                    }
                    bitmap.recycle()
                }
            }
        }
    }
}
