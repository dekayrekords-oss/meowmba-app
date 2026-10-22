package com.example.meowmba

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.meowmba.ui.companion.FullFlyPainter
import com.example.meowmba.ui.companion.FullFlyPose
import com.example.meowmba.ui.companion.LumenFrame
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
class FullFlyRenderTest {
    private val painter = FullFlyPainter()
    private fun render(pose: FullFlyPose = FullFlyPose(), frame: LumenFrame = LumenFrame(), w: Int = 480, h: Int = 480): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            painter.draw(this, frame, pose)
        }
        return bitmap
    }

    @Test fun cornersFlightHeldPropsAndLargeScaleStayContainedInBothOrientations() {
        for ((w, h) in listOf(360 to 800, 800 to 360, 280 to 160)) {
            for (corner in listOf(0f, 1f)) for (phase in listOf(-1.57f, 0f, 1.57f)) {
                val b = render(FullFlyPose(x = corner, y = corner, flight = 1f, scale = 1.8f,
                    wingPhase = phase, armReach = 1f, holding = "book", handDrop = .56f, handX = .85f), w = w, h = h)
                for (x in 0 until w) {
                    assertEquals("top", 0, b.getPixel(x, 0)); assertEquals("bottom", 0, b.getPixel(x, h - 1))
                }
                for (y in 0 until h) {
                    assertEquals("left", 0, b.getPixel(0, y)); assertEquals("right", 0, b.getPixel(w - 1, y))
                }
                b.recycle()
            }
        }
    }

    @Test fun clocklessReplayIsStableAndLocomotionSpeechAndPropsActuallyChangePixels() {
        val baseline = render()
        val replay = render()
        assertTrue(baseline.sameAs(replay)); replay.recycle()
        for (pose in listOf(FullFlyPose(walkPhase = 1.57f), FullFlyPose(flight = 1f, wingPhase = 1.57f),
            FullFlyPose(holding = "book"), FullFlyPose(holding = "glass"), FullFlyPose(armReach = 1f))) {
            val b = render(pose); assertFalse("pose changes visible pixels: $pose", baseline.sameAs(b)); b.recycle()
        }
        for (frame in listOf(LumenFrame(mouthOpen = .9f), LumenFrame(leftOpen = .05f, rightOpen = .05f),
            LumenFrame(gazeX = 1f, gazeY = -.7f), LumenFrame(earLag = 25f))) {
            val b = render(frame = frame); assertFalse("Lumen expression changes visible pixels", baseline.sameAs(b)); b.recycle()
        }
        baseline.recycle()
    }

    @Test fun nativeCharacterSheet() {
        val poses = listOf(
            "Hello" to (FullFlyPose() to LumenFrame(smile = .7f)),
            "Flying" to (FullFlyPose(flight = 1f, wingPhase = 1.1f) to LumenFrame(smile = .9f)),
            "Walking" to (FullFlyPose(walkPhase = 1.57f) to LumenFrame()),
            "Book" to (FullFlyPose(holding = "book") to LumenFrame(gazeY = .7f)),
            "Water" to (FullFlyPose(holding = "glass") to LumenFrame(smile = .5f)),
            "Talking" to (FullFlyPose(armReach = .6f) to LumenFrame(mouthOpen = .8f, smile = .6f)),
            "Sleepy" to (FullFlyPose() to LumenFrame(leftOpen = .16f, rightOpen = .16f)),
            "Wave" to (FullFlyPose(armReach = 1f) to LumenFrame(smile = 1f, gazeX = -.5f)),
        )
        val sheet = Bitmap.createBitmap(1440, 800, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        canvas.drawColor(0xFFF0EEE3.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF324742.toInt(); textSize = 22f }
        poses.forEachIndexed { i, (label, fixture) ->
            val b = render(fixture.first.copy(scale = 1.5f), fixture.second, 360, 360)
            canvas.drawBitmap(b, (i % 4) * 360f, (i / 4) * 400f, null)
            canvas.drawText(label, (i % 4) * 360f + 28f, (i / 4) * 400f + 373f, paint)
            b.recycle()
        }
        val out = File("build/reports/full-fly/full-fly-character-sheet.png")
        out.parentFile.mkdirs(); out.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sheet.recycle()
        assertTrue(out.length() > 1000)
    }
}
