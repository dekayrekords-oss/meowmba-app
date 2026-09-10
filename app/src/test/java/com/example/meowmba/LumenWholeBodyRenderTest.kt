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
class LumenWholeBodyRenderTest {
    private val painter = LumenPainter()

    private fun render(f: LumenFrame, w: Int, h: Int, privacy: Boolean = false): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            painter.draw(this, f, Color(0xFFF0B967), Color(0xFFDB9C50), featureColor = Color(0xFFDFBC8C),
                cameraOff = privacy, micOff = privacy, speakerOff = privacy)
        }
        return b
    }

    private fun assertContained(b: Bitmap, label: String) {
        for (x in 0 until b.width) {
            assertTrue("top $label", android.graphics.Color.alpha(b.getPixel(x, 0)) < 110)
            assertTrue("bottom $label", android.graphics.Color.alpha(b.getPixel(x, b.height - 1)) < 110)
        }
        for (y in 0 until b.height) {
            assertTrue("left $label", android.graphics.Color.alpha(b.getPixel(0, y)) < 110)
            assertTrue("right $label", android.graphics.Color.alpha(b.getPixel(b.width - 1, y)) < 110)
        }
    }

    @Test fun tiltedRoamingBodyAndMorphsStayInsideTheScreen() {
        for ((w, h) in listOf(360 to 800, 360 to 360, 720 to 520, 1600 to 500, 280 to 160)) {
            for (animal in AvatarAnimal.entries) for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) {
                for (phoneTilt in listOf(-14f, 14f)) {
                    val rig = LumenRig()
                    repeat(90) { i ->
                        val f = rig.step(i / 60f, LumenInput(x, y, true, animal = animal,
                            screenRoll = phoneTilt, tilt = x * 24f))
                        if (i !in listOf(12, 89)) return@repeat
                        val b = render(f, w, h)
                        assertContained(b, "$animal $w/$h $x/$y tilt=$phoneTilt frame=$i")
                        b.recycle()
                    }
                }
            }
        }
    }

    @Test fun driveEntryAndExitDoNotThrowTheRoamingCharacterOffscreen() {
        for ((w, h) in listOf(360 to 800, 360 to 360, 720 to 520, 1600 to 500)) {
            for (animal in AvatarAnimal.entries) for (side in listOf(-1f, 1f)) {
                val rig = LumenRig(); val drive = DriveVisualRig()
                repeat(360) { i ->
                    val t = i / 60f; val now = 1000L + i * 1000L / 60
                    val d = drive.step(t, now, now, t in 2f..4f, side, -side, side * 14f)
                    val f = rig.step(t, LumenInput(side, -side, true, animal = animal,
                        lean = d.lean, lurch = d.lurch, driveWeight = d.driving, brace = d.brace, screenRoll = d.tilt))
                    if (i % 20 != 0) return@repeat
                    val b = render(f, w, h)
                    assertContained(b, "drive $animal $w/$h side=$side time=$t")
                    b.recycle()
                }
            }
        }
    }

    @Test fun privacyPixelsStayFixedAcrossMovementCostumesDrivingAndHide() {
        for ((w, h) in listOf(360 to 800, 720 to 360, 280 to 160)) {
            val baseline = render(LumenFrame(presence = 0f), w, h, privacy = true)
            var visible = 0
            for (x in 0 until w) for (y in 0 until h) if (android.graphics.Color.alpha(baseline.getPixel(x, y)) > 100) visible++
            assertTrue("privacy really drawn", visible > 50)
            for (animal in AvatarAnimal.entries) {
                val traits = AvatarFormProfiles.traits(animal)
                val f = LumenFrame(presence = 0f, form = traits, cat = traits.pointedEars, dog = traits.floppyEars,
                    headX = .8f, headY = .7f, driftX = -1f, driftY = 1f, roll = 18f, screenRoll = 14f,
                    driveWeight = .5f, depth = 1f)
                val b = render(f, w, h, privacy = true)
                assertTrue("fixed privacy $animal $w/$h", baseline.sameAs(b))
                b.recycle()
            }
            baseline.recycle()
        }
    }

    @Test fun scenesEnterFromRoamingPositionsWithoutClippingTheirProps() {
        for ((w, h) in listOf(360 to 800, 360 to 360, 1200 to 500)) {
            for (performance in AvatarPerformanceCatalog.entries.filter { it.accent in LumenScenes.accents }) {
                for (side in listOf(-1f, 1f)) {
                    val rig = LumenRig()
                    repeat(120) { rig.step(it / 60f, LumenInput(side, -side, true, screenRoll = side * 14f)) }
                    repeat(90) { i ->
                        val local = i / 60f
                        val progress = local * 1000f / performance.spec.durationMs
                        val f = rig.step(2f + local, lumenPerformanceInput(LumenInput(side, -side, true,
                            performing = true, screenRoll = side * 14f,
                            animal = AvatarAnimal.performanceFor(performance.title) ?: AvatarAnimal.NONE),
                            performance, progress))
                        if (i % 12 != 0) return@repeat
                        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
                            painter.draw(this, f, Color.Cyan, Color.Cyan, performance = performance, progress = progress)
                        }
                        assertContained(b, "scene ${performance.id} $w/$h side=$side time=$local")
                        b.recycle()
                    }
                }
            }
        }
    }

    @Test fun actualOpaqueCharacterPixelsTranslateNotJustThePupil() {
        fun midpoint(b: Bitmap): Float {
            var left = b.width; var right = 0
            for (x in 0 until b.width) for (y in 0 until b.height) {
                if (android.graphics.Color.alpha(b.getPixel(x, y)) > 180) { left = minOf(left, x); right = maxOf(right, x) }
            }
            assertTrue(right > left)
            return (left + right) / 2f
        }
        for ((w, h) in listOf(360 to 800, 1200 to 500)) {
            val centers = listOf(-1f, 1f).map { side ->
                val rig = LumenRig(); var f = LumenFrame()
                repeat(120) { f = rig.step(it / 60f, LumenInput(side, 0f, true)) }
                val b = render(f, w, h); val center = midpoint(b); b.recycle(); center
            }
            assertTrue("painted silhouette span $w/$h", centers[1] - centers[0] > w * .24f)
        }
    }

    @Test fun optionalPreviewShowsPortraitAndLandscapeWithTheProductionRig() {
        if (System.getenv("MEOW_BODY_RENDER_FRAMES") != "1") return
        val folder = File("build/reports/lumen-whole-body/frames").apply { mkdirs() }
        val rig = LumenRig()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD7C6AF.toInt(); textSize = 22f }
        val rootLog = StringBuilder("seconds,portrait_x,portrait_y,landscape_x,landscape_y,form,tracking\n")
        repeat(960) { i ->
            val t = i / 30f
            val animal = when { t < 16f -> AvatarAnimal.NONE; t < 20f -> AvatarAnimal.CAT
                t < 24f -> AvatarAnimal.DOG; t < 28f -> AvatarAnimal.SQUIRREL; else -> AvatarAnimal.RACCOON }
            val tracking = t >= 10f
            val x = when { t < 12f -> .85f; t < 14f -> -.85f; t < 16f -> .6f
                else -> if ((t - 16f) % 4 < 2f) -.82f else .82f }
            val y = when { t < 12f -> -.65f; t < 14f -> .55f; t < 16f -> -.25f
                else -> if ((t - 16f) % 4 < 2f) -.5f else .5f }
            val f = rig.step(t, LumenInput(x, y, tracking, animal = animal, interaction = InteractionState.MUTED))
            val portrait = render(f, 360, 740, privacy = !tracking)
            val landscape = render(f, 720, 360, privacy = !tracking)
            val sheet = Bitmap.createBitmap(1104, 800, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(sheet)
            canvas.drawColor(0xFF0B131B.toInt())
            text.color = 0xFF243139.toInt(); text.strokeWidth = 1f
            canvas.drawLine(372f, 30f, 372f, 760f, text)
            canvas.drawLine(384f, 192f, 1104f, 192f, text)
            canvas.drawLine(384f, 552f, 1104f, 552f, text)
            canvas.drawBitmap(portrait, 0f, 30f, null)
            canvas.drawBitmap(landscape, 384f, 192f, null)
            text.color = 0xFFD7C6AF.toInt(); text.textSize = 21f
            canvas.drawText("PORTRAIT", 24f, 28f, text)
            canvas.drawText("LANDSCAPE", 408f, 166f, text)
            canvas.drawText(if (!tracking) "Quiet idle / microphone and camera off" else "Eyes lead / whole character follows", 408f, 610f, text)
            text.textSize = 17f
            canvas.drawText("${animal.label} / one shared moving rig", 408f, 644f, text)
            canvas.drawText("Native preview with scripted attention", 408f, 720f, text)
            canvas.drawText("Not a phone recording or live-camera test", 408f, 749f, text)
            val a = lumenCenter(360f, 740f, f); val b = lumenCenter(720f, 360f, f)
            rootLog.append("$t,${a.x},${a.y},${b.x},${b.y},${animal.key},$tracking\n")
            File(folder, "${i.toString().padStart(4, '0')}.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (i in listOf(240, 359, 419, 569, 689, 809, 929)) {
                File(folder.parentFile, "sample-$i.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            portrait.recycle(); landscape.recycle(); sheet.recycle()
        }
        File(folder.parentFile, "root-travel.csv").writeText(rootLog.toString())
    }
}
