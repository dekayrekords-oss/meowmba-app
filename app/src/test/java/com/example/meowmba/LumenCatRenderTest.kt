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
class LumenCatRenderTest {
    private val painter = LumenPainter()
    private val palettes = listOf(
        Triple(Color(0xFF58B9AE), Color(0xFF58B9AE), Color(0xFFE8D5B9)),
        Triple(Color(0xFFBA9CFF), Color(0xFF9674E1), Color(0xFFFFA6C9)),
        Triple(Color(0xFFFFBA6B), Color(0xFFD99755), Color(0xFF9C6758)),
        Triple(Color(0xFF74CCF5), Color(0xFF6AAFE8), Color(0xFFE6EDF3)),
    )

    private fun input(performance: AvatarPerformance?, progress: Float, gaze: Float = 0f): LumenInput {
        val frame = performance?.let { CustomAnimations.sample(it.spec, (progress * it.spec.durationMs).toLong()) }
        val base = LumenInput(gaze, -0.08f, true, animal = AvatarAnimal.CAT,
            performing = frame != null, openness = frame?.eyeOpen ?: 1f,
            pupil = frame?.pupilSize ?: 0.5f, tilt = frame?.headTiltDeg ?: 0f,
            bounce = frame?.bodyBounce ?: 0f, gazeUp = frame?.pupilGazeUp ?: 0f,
            earPerk = frame?.earPerk ?: 1f, earDroop = frame?.earDroopDeg ?: 0f,
            happy = frame?.mouth == MouthMode.HAPPY, whiskerFlutter = frame?.whiskerFlutter ?: false,
            speaking = if (frame?.mouth == MouthMode.SPEAKING) 0.65f else 0f)
        return lumenPerformanceInput(base, performance, progress)
    }

    private fun render(frame: LumenFrame, label: String, performance: AvatarPerformance? = null,
        progress: Float = 0.45f, palette: Int = 0, light: Boolean = false, w: Int = 960, h: Int = 720): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val (eye, glow, feature) = palettes[palette]
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            drawRect(if (light) Color(0xFFF0EEEA) else Color(0xFF0B131B))
            painter.draw(this, frame, eye, glow, catFeatures = true, featureColor = feature,
                performance = performance, progress = progress)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (light) 0xFF344B50.toInt() else 0xFFB6C7C8.toInt()
            textSize = w * 0.022f; typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        }
        android.graphics.Canvas(bitmap).drawText(label, w * 0.042f, h * 0.93f, text)
        return bitmap
    }

    private fun save(bitmap: Bitmap, path: String) {
        val file = File("build/reports/lumen-cat/$path"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun catColorwaysAndExpressionsRenderInBothThemes() {
        val scenes = listOf(null, "heart_flutter", "playful_wink", "snow_wonder", "puzzled_tilt", "pocket_groove")
        val sheet = Bitmap.createBitmap(1440, 720, Bitmap.Config.ARGB_8888)
        scenes.forEachIndexed { index, id ->
            val perf = id?.let { AvatarPerformanceCatalog.resolve(it) }
            val rig = LumenRig()
            rig.step(0f, input(perf, 0.45f))
            var f = LumenFrame()
            repeat(90) { f = rig.step((it + 1f) / 60, input(perf, 0.45f, if (index == 4) 0.4f else 0f)) }
            val b = render(f, "LUMEN CAT / ${perf?.title ?: "attentive"}", perf,
                palette = if (index == 2) 2 else index % 4, light = index == 2)
            save(b, "cat-$index.png")
            android.graphics.Canvas(sheet).drawBitmap(b, null,
                android.graphics.Rect(index % 3 * 480, index / 3 * 360, index % 3 * 480 + 480, index / 3 * 360 + 360), null)
            // Rendering color controls must alter pixels, not merely persisted labels.
            val recolored = render(f, "", perf, palette = (index + 1) % 4)
            // The deepest pupil is deliberately neutral black in every palette.
            // Compare the whole face region, not a single potentially pupil pixel.
            var changedPixels = 0
            for (y in 100..510 step 5) for (x in 180..780 step 5) {
                if (b.getPixel(x, y) != recolored.getPixel(x, y)) changedPixels++
            }
            assertTrue("palette $index did not recolor the face", changedPixels > 1000)
            recolored.recycle(); b.recycle()
        }
        save(sheet, "contact.png"); sheet.recycle()
    }

    @Test fun allPerformancesHaveAVisibleNativeCapture() {
        val sheet = Bitmap.createBitmap(1920, ((AvatarPerformanceCatalog.entries.size + 4) / 5) * 288, Bitmap.Config.ARGB_8888)
        AvatarPerformanceCatalog.entries.forEachIndexed { index, perf ->
            val rig = LumenRig()
            rig.step(0f, input(perf, 0f))
            var f = LumenFrame()
            val end = (perf.spec.durationMs * 0.45f / 1000f * 60f).toInt()
            repeat(end) { i ->
                val time = (i + 1f) / 60
                f = rig.step(time, input(perf, time * 1000f / perf.spec.durationMs))
            }
            val b = render(f, perf.title, perf, w = 384, h = 288)
            android.graphics.Canvas(sheet).drawBitmap(b, (index % 5 * 384).toFloat(), (index / 5 * 288).toFloat(), null)
            b.recycle()
        }
        save(sheet, "all-performances.png"); sheet.recycle()
    }

    @Test fun muzzleAndEveryAccentStayInsidePortraitCoverAndLandscape() {
        for ((w, h) in listOf(360 to 800, 720 to 520, 1600 to 500, 360 to 360)) {
            for (roll in listOf(-18f, 18f)) for (accent in PerformanceAccent.entries) {
                val f = LumenFrame(headX = 0.65f, headY = -0.3f, roll = roll, cat = 1f,
                    leftOpen = 1.31f, rightOpen = 1.31f, earLift = 15f, mouthOpen = 1f)
                val perf = AvatarPerformanceCatalog.entries.firstOrNull { it.accent == accent }
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
                    painter.draw(this, f, Color(0xFF58B9AE), Color(0xFF58B9AE), catFeatures = true,
                        featureColor = Color(0xFFE8D5B9), performance = perf, progress = 0.45f)
                }
                for (x in 0 until w) {
                    assertTrue("top $accent $w x $h", android.graphics.Color.alpha(b.getPixel(x, 0)) < 110)
                    assertTrue("bottom $accent", android.graphics.Color.alpha(b.getPixel(x, h - 1)) < 110)
                }
                for (y in 0 until h) {
                    assertTrue("left $accent", android.graphics.Color.alpha(b.getPixel(0, y)) < 110)
                    assertTrue("right $accent", android.graphics.Color.alpha(b.getPixel(w - 1, y)) < 110)
                }
                b.recycle()
            }
        }
    }

    @Test fun optionalMotionPreviewUsesTheActualRigSamplerAndPainter() {
        if (System.getenv("MEOW_CAT_RENDER_FRAMES") != "1") return
        val rig = LumenRig()
        val scenes = listOf("playful_wink", "heart_flutter", "snow_wonder", "pocket_groove", "peekaboo")
        var frameIndex = 0
        fun write(perf: AvatarPerformance?, localT: Float, light: Boolean = false) {
            val progress = if (perf == null) 0f else localT * 1000f / perf.spec.durationMs
            val target = input(perf, progress, if (perf == null) kotlin.math.sin(localT * 0.8f) * 0.6f else 0f)
            val f = rig.step(frameIndex / 30f, target)
            val b = render(f, "LUMEN CAT / ${perf?.title ?: if (light) "light theme & amber color" else "attention preview"}", perf,
                progress, palette = if (light) 2 else 0, light = light)
            save(b, "frames/${frameIndex.toString().padStart(4, '0')}.png")
            frameIndex++; b.recycle()
        }
        repeat(120) { write(null, it / 30f) }
        scenes.forEach { id ->
            val perf = AvatarPerformanceCatalog.resolve(id)!!
            repeat((perf.spec.durationMs / 1000f * 30).toInt()) { write(perf, it / 30f) }
            repeat(21) { write(null, it / 30f) }
        }
        repeat(120) { write(null, it / 30f, light = true) }
    }

    @Test fun hideNeverHidesThePrivacyIndicators() {
        val b = Bitmap.createBitmap(720, 520, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(720f, 520f)) {
            painter.draw(this, LumenFrame(cat = 1f, presence = 0f), Color.Cyan, Color.Cyan,
                catFeatures = true, cameraOff = true, micOff = true, speakerOff = true, statusColor = Color.White)
        }
        var visible = 0
        for (y in 0 until b.height) for (x in 0 until b.width) {
            if (android.graphics.Color.alpha(b.getPixel(x, y)) > 200) visible++
        }
        assertTrue("privacy symbols should remain outside the transparent face layer", visible > 100)
        b.recycle()
    }
}
