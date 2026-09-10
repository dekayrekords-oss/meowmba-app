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
class LumenScenesRenderTest {
    private val painter = LumenPainter()
    private val scenes = AvatarPerformanceCatalog.entries.filter { it.category == "Scenes" }

    private fun input(perf: AvatarPerformance, progress: Float): LumenInput {
        val pose = CustomAnimations.sample(perf.spec, (progress * perf.spec.durationMs).toLong()) ?: AnimFrame(atMs = 0)
        return lumenPerformanceInput(LumenInput(cameraEnabled = false, performing = true,
            animal = AvatarAnimal.performanceFor(perf.title) ?: if (perf.accent == PerformanceAccent.NEST) AvatarAnimal.SQUIRREL else AvatarAnimal.NONE,
            openness = pose.eyeOpen ?: 1f, pupil = pose.pupilSize ?: .5f,
            tilt = pose.headTiltDeg ?: 0f, bounce = pose.bodyBounce ?: 0f,
            happy = pose.happyEyes ?: false, gazeUp = pose.pupilGazeUp ?: 0f,
            earPerk = pose.earPerk ?: 1f, earDroop = pose.earDroopDeg ?: 0f,
            tailWag = pose.tailWag ?: 0f, whiskerFlutter = pose.whiskerFlutter ?: false), perf, progress)
    }

    private fun render(frame: LumenFrame, perf: AvatarPerformance, p: Float, w: Int = 720, h: Int = 540,
        transparent: Boolean = false, light: Boolean = false): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            if (!transparent) drawRect(if (light) Color(0xFFF0EEEA) else Color(0xFF0B131B))
            painter.draw(this, frame, Color(0xFF79BCC5), Color(0xFF69B0BF), featureColor = Color(0xFFE8CFAC),
                performance = perf, progress = p, cameraOff = true, micOff = true)
        }
        return b
    }
    private fun save(b: Bitmap, name: String) {
        val file = File("build/reports/lumen-scenes/$name"); file.parentFile!!.mkdirs()
        file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun label(b: Bitmap, title: String) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9EBAC1.toInt(); textSize = b.width * .030f }
        android.graphics.Canvas(b).drawText(title, b.width * .04f, b.height * .94f, p)
    }

    @Test fun allScenesRenderFromTheProductionRigAndProps() {
        val sheet = Bitmap.createBitmap(1440, ((scenes.size + 3) / 4) * 270, Bitmap.Config.ARGB_8888)
        scenes.forEachIndexed { index, perf ->
            val rig = LumenRig(); var f = LumenFrame()
            val p = when (perf.accent) { PerformanceAccent.SNACK -> .37f; PerformanceAccent.RAIN -> .4f; else -> .5f }
            repeat((p * perf.spec.durationMs / 1000f * 30).toInt() + 1) { i ->
                f = rig.step(i / 30f, input(perf, i / 30f * 1000 / perf.spec.durationMs))
            }
            val b = render(f, perf, p)
            label(b, perf.title); save(b, "${perf.id}.png")
            android.graphics.Canvas(sheet).drawBitmap(b, null,
                android.graphics.Rect(index % 4 * 360, index / 4 * 270, index % 4 * 360 + 360, index / 4 * 270 + 270), null)
            b.recycle()
            val light = render(f, perf, p, light = true); save(light, "${perf.id}-light.png"); light.recycle()
        }
        save(sheet, "contact.png"); sheet.recycle()
    }

    @Test fun completeSceneTimelinesFitCoverPortraitAndLandscape() {
        for ((w, h) in listOf(360 to 800, 360 to 360, 720 to 520, 1600 to 500)) {
            for (perf in scenes) {
                val rig = LumenRig()
                for (i in 0..180) {
                    val p = i / 180f
                    val f = rig.step(i / 30f, input(perf, p))
                    if (i % 18 != 0) continue
                    val b = render(f, perf, p, w, h, transparent = true)
                    for (x in 0 until w) {
                        assertTrue("top ${perf.id} $p $w/$h", android.graphics.Color.alpha(b.getPixel(x, 0)) < 110)
                        assertTrue("bottom ${perf.id} $p $w/$h", android.graphics.Color.alpha(b.getPixel(x, h - 1)) < 110)
                    }
                    for (y in 0 until h) {
                        assertTrue("left ${perf.id} $p $w/$h", android.graphics.Color.alpha(b.getPixel(0, y)) < 110)
                        assertTrue("right ${perf.id} $p $w/$h", android.graphics.Color.alpha(b.getPixel(w - 1, y)) < 110)
                    }
                    b.recycle()
                }
            }
        }
    }

    @Test fun optionalPreviewIsTheCompleteNativeSceneTimeline() {
        val export = System.getenv("MEOW_SCENE_RENDER_FRAMES")
        if (export != "1" && export != "snack") return
        val rig = LumenRig(); var index = 0
        for (perf in scenes) {
            val count = (perf.spec.durationMs / 1000f * 30).toInt()
            repeat(count + 1) { local ->
                val p = local / count.toFloat()
                val f = rig.step(index / 30f, input(perf, p))
                if (export == "1" || perf.accent == PerformanceAccent.SNACK) {
                    val b = render(f, perf, p)
                    label(b, perf.title + " / native preview")
                    save(b, "frames/${index.toString().padStart(4, '0')}.png")
                    b.recycle()
                }
                index++
            }
        }
    }
}
