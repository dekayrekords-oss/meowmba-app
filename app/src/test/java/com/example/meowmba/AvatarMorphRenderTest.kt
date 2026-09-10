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
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarMorphRenderTest {
    private val painter = LumenPainter()
    private fun render(f: LumenFrame, w: Int = 720, h: Int = 640, light: Boolean = false,
        transparent: Boolean = false, private: Boolean = false, violet: Boolean = false): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            if (!transparent) drawRect(if (light) Color(0xFFF0EEEA) else Color(0xFF0B131B))
            painter.draw(this, f, if (violet) Color(0xFFAD77EE) else Color(0xFF58B9AE),
                if (violet) Color(0xFF9373CE) else Color(0xFF58B9AE),
                featureColor = if (violet) Color(0xFFFFA6C9) else Color(0xFFE8D5B9),
                cameraOff = private, micOff = private, speakerOff = private)
        }
        return b
    }
    private fun save(b: Bitmap, name: String) {
        val out = File("build/reports/avatar-morph/$name"); out.parentFile!!.mkdirs()
        out.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun label(b: Bitmap, text: String) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB6C7C8.toInt(); textSize = b.width * 0.035f }
        android.graphics.Canvas(b).drawText(text, b.width * 0.05f, b.height * 0.91f, p)
    }

    @Test fun everyFormHasDistinctNativeFeaturesAndColorControls() {
        val sheet = Bitmap.createBitmap(1440, ((AvatarAnimal.entries.size - 1 + 1) / 2) * 640, Bitmap.Config.ARGB_8888)
        for ((i, animal) in AvatarAnimal.entries.drop(1).withIndex()) {
            val rig = LumenRig(); var f = LumenFrame()
            repeat(120) { f = rig.step(it / 60f, LumenInput(0f, -0.08f, true, animal = animal)) }
            val b = render(f); val recolor = render(f, light = true, violet = true)
            assertFalse(b.sameAs(recolor))
            label(b, animal.label.uppercase()); save(b, "${animal.key}.png")
            save(recolor, "${animal.key}-light.png")
            android.graphics.Canvas(sheet).drawBitmap(b, (i % 2 * 720).toFloat(), (i / 2 * 640).toFloat(), null)
            b.recycle(); recolor.recycle()
        }
        save(sheet, "forms-contact.png"); sheet.recycle()
    }

    @Test fun mixedMorphFramesFitShortAndTallStagesWithPrivacyIntact() {
        for ((w, h) in listOf(360 to 800, 360 to 360, 720 to 520, 1600 to 500)) {
            val blank = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val rig = LumenRig()
            for (i in 0..720) {
                val input = LumenInput(if (i % 240 < 120) -1f else 1f, -1f, true,
                    animal = AvatarAnimal.entries[(i / 45) % AvatarAnimal.entries.size], tilt = if (i % 180 < 90) -18f else 18f)
                val f = rig.step(i / 60f, input)
                if (i % 15 != 0) continue
                val b = render(f, w, h, transparent = true)
                for (x in 0 until w) {
                    assertTrue("top $w/$h frame $i", android.graphics.Color.alpha(b.getPixel(x, 0)) < 110)
                    assertTrue("bottom $w/$h frame $i", android.graphics.Color.alpha(b.getPixel(x, h - 1)) < 110)
                }
                for (y in 0 until h) {
                    assertTrue("left $w/$h frame $i", android.graphics.Color.alpha(b.getPixel(0, y)) < 110)
                    assertTrue("right $w/$h frame $i", android.graphics.Color.alpha(b.getPixel(w - 1, y)) < 110)
                }
                val hidden = render(f.copy(presence = 0f), w, h, transparent = true, private = true)
                assertFalse("privacy must remain when character is hidden", hidden.sameAs(blank))
                b.recycle(); hidden.recycle()
            }
            blank.recycle()
        }
    }

    @Test fun optionalMorphPreviewUsesTheActualContinuousRig() {
        if (System.getenv("MEOW_MORPH_RENDER_FRAMES") != "1") return
        val rig = LumenRig()
        repeat(420) { i ->
            val t = i / 30f
            val animal = when {
                t < 1.5f -> AvatarAnimal.NONE
                t < 4f -> AvatarAnimal.CAT
                t < 6.5f -> AvatarAnimal.DOG
                t < 9f -> AvatarAnimal.SQUIRREL
                t < 11.5f -> AvatarAnimal.RACCOON
                else -> AvatarAnimal.NONE
            }
            val f = rig.step(t, LumenInput(sin(t * 0.60f) * 0.40f, -0.08f, true,
                animal = animal, speaking = if (t > 7f && t < 11f) (sin(t * 12f) * 0.25f + 0.25f) else 0f))
            val b = render(f, w = 960, h = 720)
            label(b, "LUMEN / ${animal.label}")
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF849DA5.toInt(); textSize = 19f }
            android.graphics.Canvas(b).drawText("One eye rig / simulated gaze & speech / native app renderer", 48f, 698f, p)
            save(b, "frames/${i.toString().padStart(4, '0')}.png"); b.recycle()
        }
    }
}
