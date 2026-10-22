package com.example.meowmba

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LumenStoryArcsRenderTest {
    private val painter = LumenPainter()
    private val props = LumenScenePainter()
    private val animals = mapOf("lost_mouse" to AvatarAnimal.AUSTRALIAN_SHEPHERD,
        "reading_realization" to AvatarAnimal.OWL, "hat_mishap" to AvatarAnimal.DUCK)
    private val stories = animals.keys.map { AvatarPerformanceCatalog.resolve(it)!! }

    private fun input(story: AvatarPerformance, p: Float): LumenInput {
        val pose = CustomAnimations.sample(story.spec, (p * story.spec.durationMs).toLong()) ?: AnimFrame(atMs = 0)
        return lumenPerformanceInput(LumenInput(cameraEnabled = false, performing = true,
            animal = animals.getValue(story.id), openness = pose.eyeOpen ?: 1f,
            pupil = pose.pupilSize ?: .5f, tilt = pose.headTiltDeg ?: 0f,
            bounce = pose.bodyBounce ?: 0f, happy = pose.happyEyes ?: false,
            gazeUp = pose.pupilGazeUp ?: 0f, earPerk = pose.earPerk ?: 1f,
            earDroop = pose.earDroopDeg ?: 0f, tailWag = pose.tailWag ?: 0f,
            whiskerFlutter = pose.whiskerFlutter ?: false), story, p)
    }

    private fun frameAt(story: AvatarPerformance, p: Float): LumenFrame {
        val rig = LumenRig()
        repeat(45) { rig.step(it / 60f, LumenInput(animal = animals.getValue(story.id))) }
        var frame = LumenFrame()
        val steps = (p * story.spec.durationMs / 1000f * 60f).roundToInt()
        for (i in 0..steps) frame = rig.step(.75f + i / 60f,
            input(story, i / 60f * 1000f / story.spec.durationMs))
        return frame
    }

    private fun render(frame: LumenFrame, story: AvatarPerformance?, p: Float,
        w: Int = 720, h: Int = 540, background: Boolean = false, light: Boolean = false): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            if (background) drawRect(if (light) Color(0xFFF0EEEA) else Color(0xFF0B131B))
            painter.draw(this, frame, Color(0xFF79BCC5), Color(0xFF69B0BF), featureColor = Color(0xFFE8CFAC),
                performance = story, progress = p, cameraOff = true, micOff = true, speakerOff = true)
        }
        return b
    }

    private fun prop(story: AvatarPerformance, p: Float): Bitmap {
        val b = Bitmap.createBitmap(720, 540, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(720f, 540f)) {
            props.draw(this, story.accent, p, Offset(360f, 270f), 400f,
                Color(0xFF69B0BF), Color(0xFFE8CFAC), LumenFrame(), story.id)
        }
        return b
    }

    private fun save(b: Bitmap, name: String) {
        val file = File("build/reports/story-arcs/$name"); file.parentFile!!.mkdirs()
        file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun label(b: Bitmap, title: String) {
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9EBAC1.toInt(); textSize = 19f }
        android.graphics.Canvas(b).drawText(title, 22f, b.height - 16f, ink)
    }

    @Test fun nativeStoryboardShowsEveryNarrativeBeatAndExportsOptionalActiveClockPreview() {
        val beats = mapOf("lost_mouse" to listOf(.16f, .31f, .45f, .59f, .75f, .84f),
            "reading_realization" to listOf(.24f, .41f, .50f, .60f, .75f, .85f),
            "hat_mishap" to listOf(.18f, .30f, .40f, .54f, .66f, .76f))
        val labels = mapOf("lost_mouse" to listOf("Follow", "Listen left", "Listen right", "Found", "Small pounce", "Let it go"),
            "reading_realization" to listOf("Read", "Reread", "Consider", "Aha", "Satisfied", "Settle"),
            "hat_mishap" to listOf("Proud", "Slipping", "Oh", "Recover", "Peek", "Sheepish"))
        val sheet = Bitmap.createBitmap(2160, 810, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        stories.forEachIndexed { row, story ->
            beats.getValue(story.id).forEachIndexed { col, p ->
                val frame = frameAt(story, p)
                val b = render(frame, story, p, background = true)
                label(b, "${story.title} / ${labels.getValue(story.id)[col]}")
                save(b, "${story.id}-$col.png")
                canvas.drawBitmap(b, null, android.graphics.Rect(col * 360, row * 270, col * 360 + 360, row * 270 + 270), null)
                b.recycle()
                val light = render(frame, story, p, background = true, light = true)
                save(light, "${story.id}-$col-light.png"); light.recycle()
            }
        }
        save(sheet, "contact.png"); sheet.recycle()
        if (System.getenv("MEOW_STORY_ARC_FRAMES") == "1") for (story in stories) {
            val rig = LumenRig()
            repeat(45) { rig.step(it / 60f, LumenInput(animal = animals.getValue(story.id))) }
            val count = (story.spec.durationMs / 1000f * 30).roundToInt()
            for (i in 0..count) {
                val p = i / count.toFloat()
                val frame = rig.step(.75f + i / 30f, input(story, p))
                val b = render(frame, story, p, background = true)
                label(b, "${story.title} / native active-clock preview")
                save(b, "frames/${story.id}/${i.toString().padStart(4, '0')}.png"); b.recycle()
            }
        }
    }

    @Test fun nativePropsActuallyHideTheMouseRepeatThePageAndCoverThenReleaseTheEyes() {
        fun countAlpha(b: Bitmap, x0: Int = 0, y0: Int = 0, x1: Int = b.width, y1: Int = b.height): Int {
            var count = 0
            for (y in y0 until y1) for (x in x0 until x1)
                if (android.graphics.Color.alpha(b.getPixel(x, y)) > 80) count++
            return count
        }
        val mouse = stories.first { it.id == "lost_mouse" }
        val seen = prop(mouse, .16f); val hidden = prop(mouse, .31f); val found = prop(mouse, .59f)
        assertTrue(countAlpha(seen) > 250)
        assertEquals(0, countAlpha(hidden))
        assertTrue(countAlpha(found) > 250)
        listOf(seen, hidden, found).forEach(Bitmap::recycle)
        val book = stories.first { it.id == "reading_realization" }
        val firstRead = prop(book, .30f); val reread = prop(book, .46f)
        assertTrue("Rereading retains the same physical page", firstRead.sameAs(reread))
        assertTrue(countAlpha(reread) > 1500)
        firstRead.recycle(); reread.recycle()
        val hat = stories.first { it.id == "hat_mishap" }
        val raised = prop(hat, .19f); val slipped = prop(hat, .40f); val recovered = prop(hat, .67f)
        assertEquals(0, countAlpha(raised, 230, 246, 490, 302))
        assertTrue("Brim must really pass over the eye line", countAlpha(slipped, 230, 246, 490, 302) > 2000)
        assertEquals(0, countAlpha(recovered, 230, 246, 490, 302))
        listOf(raised, slipped, recovered).forEach(Bitmap::recycle)
    }

    @Test fun newArcsFitExtremeDriveStagesAndKeepPrivacyMarksInTheirFixedPosition() {
        for ((w, h) in listOf(360 to 800, 720 to 520, 1600 to 500)) {
            val privacy = render(LumenFrame(presence = 0f), null, 0f, w, h)
            val marks = mutableListOf<Pair<Int, Int>>()
            for (y in 0 until h) for (x in 0 until w)
                if (android.graphics.Color.alpha(privacy.getPixel(x, y)) == 255) marks += x to y
            assertTrue(marks.size > 10)
            for (story in stories) for (p in listOf(.0f, .30f, .40f, .60f, .75f, .90f)) {
                val normal = frameAt(story, p)
                for (side in listOf(-1f, 1f)) {
                    val frame = normal.copy(driveWeight = 1f, depth = side, driftX = side, driftY = side,
                        headX = side, headY = side, roll = side * 18f, screenRoll = side * 14f,
                        earAsymmetry = side * 8f)
                    val b = render(frame, story, p, w, h)
                    for (x in 0 until w) {
                        assertTrue("top ${story.id} $p $w/$h", android.graphics.Color.alpha(b.getPixel(x, 0)) < 110)
                        assertTrue("bottom ${story.id} $p $w/$h", android.graphics.Color.alpha(b.getPixel(x, h - 1)) < 110)
                    }
                    for (y in 0 until h) {
                        assertTrue("left ${story.id} $p $w/$h", android.graphics.Color.alpha(b.getPixel(0, y)) < 110)
                        assertTrue("right ${story.id} $p $w/$h", android.graphics.Color.alpha(b.getPixel(w - 1, y)) < 110)
                    }
                    marks.forEach { (x, y) -> assertEquals("privacy ${story.id} $p $w/$h", privacy.getPixel(x, y), b.getPixel(x, y)) }
                    b.recycle()
                }
            }
            privacy.recycle()
        }
    }
}
