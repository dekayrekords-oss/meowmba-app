package com.example.meowmba

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.meowmba.ui.companion.*
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
class GeneratedAvatarRenderTest {
    private val painter = GeneratedAvatarPainter()
    private val forms get() = listOf(GeneratedAvatarExamples.dragon(), GeneratedAvatarExamples.coffeeCup(), GeneratedAvatarExamples.spaceship())

    @Test fun formsHaveDistinctSilhouettesWithLiveGazeBlinkSpeechAndMotion() {
        val idle = LumenFrame()
        val images = forms.map { render(it, idle, 0f) }
        try {
            assertTrue(changed(images[0], images[1]) > 1000)
            assertTrue(changed(images[1], images[2]) > 1000)
            forms.forEachIndexed { i, form ->
                for ((label, frame) in listOf("blink" to idle.copy(leftOpen = 0f, rightOpen = 0f),
                    "gaze" to idle.copy(gazeX = 1f, gazeY = -.65f), "speech" to idle.copy(mouthOpen = 1f),
                    "concern" to idle.copy(concern = 1f), "touch" to idle.copy(squash = 1f, warmth = 1f, smile = 1f))) {
                    val image = render(form, frame, 0f)
                    try { assertTrue("${form.name} $label visibly reacts", changed(images[i], image) > 25) }
                    finally { image.recycle() }
                }
                val moving = render(form, idle, .31f)
                try { assertTrue("${form.name} appendages use the active clock", changed(images[i], moving) > 25) }
                finally { moving.recycle() }
            }
        } finally { images.forEach(Bitmap::recycle) }
    }

    @Test fun frozenClockIsReproducibleAndHiddenFormDrawsNothing() {
        val form = GeneratedAvatarExamples.dragon()
        val first = render(form, LumenFrame(), 1f); val second = render(form, LumenFrame(), 1f)
        val hidden = render(form, LumenFrame(presence = 0f), 1f)
        val empty = Bitmap.createBitmap(440, 560, Bitmap.Config.ARGB_8888)
        try { assertTrue(first.sameAs(second)); assertTrue(empty.sameAs(hidden)) }
        finally { first.recycle(); second.recycle(); hidden.recycle(); empty.recycle() }
    }

    @Test fun wingsFollowTheSharedNeuralRigBeatAtTheSameDrawTime() {
        val form = GeneratedAvatarExamples.dragon()
        val closed = render(form, LumenFrame(wingBeat = 0f), 1f)
        val open = render(form, LumenFrame(wingBeat = 1f), 1f)
        try { assertTrue("Wing output uses the live rig", changed(closed, open) > 200) }
        finally { closed.recycle(); open.recycle() }
    }

    @Test fun extremeExpressionsFitShortAndTallStagesAtDefaultScale() {
        for ((w, h) in listOf(360 to 800, 840 to 360)) for (form in forms) {
            for (side in listOf(-1f, 1f)) {
                val image = render(form, LumenFrame(headX = side, headY = -side, perspective = side,
                    roll = 24f * side, screenRoll = 14f * side, squash = side, breath = .05f, mouthOpen = 1f), .8f, w, h)
                try {
                    for (x in 0 until w) {
                        assertEquals(0, android.graphics.Color.alpha(image.getPixel(x, 0)))
                        assertEquals(0, android.graphics.Color.alpha(image.getPixel(x, h - 1)))
                    }
                    for (y in 0 until h) {
                        assertEquals(0, android.graphics.Color.alpha(image.getPixel(0, y)))
                        assertEquals(0, android.graphics.Color.alpha(image.getPixel(w - 1, y)))
                    }
                } finally { image.recycle() }
            }
        }
    }

    @Test fun nativeContactSheetShowsThreeOriginalFormsAndExpressions() {
        val sheet = Bitmap.createBitmap(1440, 1500, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet).apply { drawColor(0xFF202D37.toInt()) }
        val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE6BD.toInt(); textSize = 19f }
        val states = listOf("Attentive" to LumenFrame(), "Looking at you" to LumenFrame(gazeX = .8f, headX = .65f, roll = 7f),
            "Blink" to LumenFrame(leftOpen = 0f, rightOpen = 0f), "Talking" to LumenFrame(mouthOpen = .85f, smile = .5f, warmth = .6f))
        try {
            forms.forEachIndexed { row, form ->
                states.forEachIndexed { col, (label, frame) ->
                    val image = render(form, frame, .31f, 360, 450)
                    try { canvas.drawBitmap(image, col * 360f, row * 500f + 36f, null) }
                    finally { image.recycle() }
                    canvas.drawText("${form.name} · $label", col * 360f + 10f, row * 500f + 27f, pen)
                }
                val landscape = render(form, LumenFrame(gazeX = .35f, mouthOpen = .4f), .31f, 840, 360)
                try { save(landscape, "${row + 1}-landscape") } finally { landscape.recycle() }
            }
            save(sheet, "generated-forms-contact")
        } finally { sheet.recycle() }
    }

    private fun render(form: GeneratedAvatarForm, frame: LumenFrame, time: Float, width: Int = 440, height: Int = 560): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { image ->
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image.asImageBitmap()), Size(width.toFloat(), height.toFloat())) {
                painter.draw(this, form, frame, time, Offset(width * .5f, height * .45f), minOf(width, height) * .84f)
            }
        }

    private fun changed(a: Bitmap, b: Bitmap): Int {
        var total = 0
        for (x in 0 until a.width) for (y in 0 until a.height) if (a.getPixel(x, y) != b.getPixel(x, y)) total++
        return total
    }
    private fun save(bitmap: Bitmap, name: String) {
        File("build/reports/generated-avatars/$name.png").apply { parentFile.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
