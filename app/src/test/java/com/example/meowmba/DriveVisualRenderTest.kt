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
class DriveVisualRenderTest {
    private fun render(frame: LumenFrame, cat: Boolean, w: Int = 480, h: Int = 480, privacy: Boolean = false, tilt: Float = 0f): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            // Production applies filtered screen tilt about the Lumen root,
            // with privacy outside the transform, not around the whole canvas.
            LumenPainter().draw(this, frame.copy(screenRoll = tilt), Color(0xFF58B9AE), Color(0xFF58B9AE),
                catFeatures = cat, featureColor = Color(0xFFE8D5B9),
                cameraOff = privacy, micOff = privacy, speakerOff = privacy)
        }
        return bitmap
    }

    @Test fun strongestDrivingPosesFitPortraitSquareAndCover() {
        for ((w, h) in listOf(360 to 800, 720 to 520, 1600 to 500, 360 to 360)) {
            for (animal in AvatarAnimal.entries) for (x in listOf(-1f, 0f, 1f)) for (y in listOf(-1f, 1f)) {
                val cat = animal == AvatarAnimal.CAT
                val rig = LumenRig()
                val input = LumenInput(x, y, true, animal = animal,
                    lean = x, lurch = y, driveWeight = 1f, brace = 1f)
                rig.step(0f, input); var f = LumenFrame()
                repeat(120) { f = rig.step((it + 1f) / 60, input) }
                val b = render(f, cat, w, h, tilt = x * 14f)
                // Diffuse glow may reach the edge; solid face/ears must not.
                for (px in 0 until w) {
                    assertTrue("top cat=$cat x=$x y=$y $w/$h", android.graphics.Color.alpha(b.getPixel(px, 0)) < 110)
                    assertTrue("bottom cat=$cat x=$x y=$y $w/$h", android.graphics.Color.alpha(b.getPixel(px, h - 1)) < 110)
                }
                for (py in 0 until h) {
                    assertTrue("left cat=$cat x=$x y=$y $w/$h", android.graphics.Color.alpha(b.getPixel(0, py)) < 110)
                    assertTrue("right cat=$cat x=$x y=$y $w/$h", android.graphics.Color.alpha(b.getPixel(w - 1, py)) < 110)
                }
                val private = render(f, cat, w, h, privacy = true, tilt = x * 14f)
                assertFalse("privacy badges must survive inertia", b.sameAs(private))
                b.recycle(); private.recycle()
            }
        }
    }

    @Test fun optionalDrivingPreviewUsesProductionSpringsAndPainter() {
        if (System.getenv("MEOW_DRIVE_RENDER_FRAMES") != "1") return
        val eyes = LumenRig(); val cat = LumenRig(); val motion = DriveVisualRig()
        val framesDir = File("build/reports/drive-visual/frames").apply { mkdirs() }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD4E7E8.toInt(); textSize = 23f }
        repeat(330) { i ->
            val t = i / 30f
            val (label, x, y) = when {
                t < 1.5f -> Triple("CRUISING", 0f, 0f)
                t < 2.2f -> Triple("HARD STOP / forward lurch + brace", 0f, 1f)
                t < 4f -> Triple("SMOOTH RECOVERY", 0f, 0f)
                t < 5.5f -> Triple("RIGHT TURN / inertia to the left", -0.9f, 0f)
                t < 7f -> Triple("LEFT TURN / inertia to the right", 0.9f, 0f)
                t < 8f -> Triple("SMOOTH RECOVERY", 0f, 0f)
                t < 9f -> Triple("ACCELERATING / gentle lean back", 0f, -0.65f)
                else -> Triple("SETTLED / still attentive", 0f, 0f)
            }
            val now = 1000L + i * 1000L / 30
            val force = motion.step(t, now, now, true, x, y)
            val input = LumenInput(0f, -0.06f, true, lean = force.lean, lurch = force.lurch,
                driveWeight = force.driving, brace = force.brace)
            val e = render(eyes.step(t, input), false)
            val c = render(cat.step(t, input.copy(animal = AvatarAnimal.CAT)), true)
            val sheet = Bitmap.createBitmap(960, 620, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(sheet)
            canvas.drawColor(0xFF0B131B.toInt())
            canvas.drawBitmap(e, 0f, 65f, null); canvas.drawBitmap(c, 480f, 65f, null)
            canvas.drawText("LUMEN EYES", 35f, 45f, text); canvas.drawText("LUMEN CAT", 515f, 45f, text)
            canvas.drawText(label, 35f, 550f, text)
            text.textSize = 17f
            canvas.drawText("Drive Mode preview / simulated IMU inputs / not a road recording", 35f, 588f, text)
            text.textSize = 23f
            File(framesDir, "${i.toString().padStart(4, '0')}.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            if (i == 61 || i == 153 || i == 199) {
                File(framesDir.parentFile, "sample-$i.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            sheet.recycle(); e.recycle(); c.recycle()
        }
    }
}
