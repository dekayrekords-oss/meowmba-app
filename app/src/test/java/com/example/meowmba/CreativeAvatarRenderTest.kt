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
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Review artifact drawn by the production painter, including shared scene/eye timing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CreativeAvatarRenderTest {
    private data class Card(val animalKey: String, val sceneId: String?, val label: String)
    private val cards = listOf(
        Card("australian_shepherd", null, "Australian shepherd"),
        Card("owl", null, "Owl"), Card("duck", null, "Duck"),
        Card("owl", "book_reading", "One more page"),
        Card("australian_shepherd", "mouse_chase", "Catch me if you can"),
        Card("duck", "cowboy_hat", "Howdy, duck"),
        Card("owl", "wizard_hat", "Midnight magic"),
        Card("australian_shepherd", "painter_beret", "The little artist"),
    )
    private fun input(card: Card, p: Float): LumenInput {
        val scene = card.sceneId?.let { AvatarPerformanceCatalog.resolve(it)!! }
        val pose = scene?.let { CustomAnimations.sample(it.spec, (p * it.spec.durationMs).toLong()) }
        val input = LumenInput(cameraEnabled = false, animal = AvatarAnimal.fromKey(card.animalKey),
            performing = scene != null, openness = pose?.eyeOpen ?: 1f, pupil = pose?.pupilSize ?: .4f,
            tilt = pose?.headTiltDeg ?: 0f, bounce = pose?.bodyBounce ?: 0f,
            happy = pose?.happyEyes ?: false, gazeUp = pose?.pupilGazeUp ?: 0f,
            earPerk = pose?.earPerk ?: 1f, earDroop = pose?.earDroopDeg ?: 0f,
            tailWag = pose?.tailWag ?: 0f, whiskerFlutter = pose?.whiskerFlutter ?: false)
        return if (scene == null) input else lumenPerformanceInput(input, scene, p)
    }
    private fun sheet(frames: List<LumenFrame>, progress: List<Float>, light: Boolean = false): Bitmap {
        val result = Bitmap.createBitmap(1440, 660, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(if (light) 0xFFF0EEE9.toInt() else 0xFF0B131B.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (light) 0xFF354E5A.toInt() else 0xFFB5CCD3.toInt(); textSize = 17f
        }
        cards.forEachIndexed { i, card ->
            val tile = Bitmap.createBitmap(360, 300, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(tile.asImageBitmap()), Size(360f, 300f)) {
                LumenPainter().draw(this, frames[i], Color(0xFF79BCC5), Color(0xFF69B0BF),
                    featureColor = Color(0xFFE8CFAC),
                    performance = card.sceneId?.let(AvatarPerformanceCatalog::resolve), progress = progress[i])
            }
            val x = (i % 4 * 360).toFloat(); val y = (i / 4 * 330).toFloat()
            canvas.drawBitmap(tile, x, y, null)
            canvas.drawText(card.label, x + 24, y + 315, paint)
            tile.recycle()
        }
        return result
    }
    private fun save(bitmap: Bitmap, name: String) {
        val out = File("build/reports/creative-avatars/$name"); out.parentFile!!.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun nativeContactSheetShowsNewFormsWithWearableAndStoryProps() {
        val progress = cards.map { if (it.sceneId == "book_reading") .58f else .40f }
        val frames = cards.mapIndexed { i, card ->
            val rig = LumenRig(); var frame = LumenFrame()
            repeat(121) { step -> frame = rig.step(step / 60f, input(card, progress[i] * step / 120f)) }
            frame
        }
        save(sheet(frames, progress), "contact.png")
        save(sheet(frames, progress, light = true), "contact-light.png")
    }
    @Test fun hatsEnteringFromHighRestingPositionsRemainInsideTheStage() {
        for ((w, h) in listOf(1600 to 500, 360 to 360, 360 to 800)) {
            for (id in listOf("cowboy_hat", "wizard_hat", "painter_beret")) {
                val card = Card("none", id, "High idle handoff")
                val rig = LumenRig()
                repeat(120) { i -> rig.step(i / 60f, LumenInput(0f, -1f, true)) }
                val duration = AvatarPerformanceCatalog.resolve(id)!!.spec.durationMs
                repeat(108) { i ->
                    val p = i / 60f * 1000 / duration
                    val f = rig.step(2f + i / 60f, input(card, p))
                    if (i % 3 != 0) return@repeat
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
                        LumenPainter().draw(this, f, Color.Cyan, Color.Cyan,
                            performance = AvatarPerformanceCatalog.resolve(id), progress = p)
                    }
                    for (x in 0 until w) assertTrue("$id high idle $w/$h time=${i / 60f}",
                        android.graphics.Color.alpha(b.getPixel(x, 0)) < 110)
                    b.recycle()
                }
            }
        }
    }
    @Test fun optionalNativeStoryPreview() {
        if (System.getenv("MEOW_CREATIVE_PREVIEW") != "1") return
        val rigs = cards.map { LumenRig() }
        repeat(121) { i -> cards.forEachIndexed { j, card -> rigs[j].step(i / 60f, input(card, 0f)) } }
        repeat(169) { index ->
            val seconds = index / 24f
            val progress = cards.map { card -> card.sceneId?.let { id ->
                (seconds * 1000 / AvatarPerformanceCatalog.resolve(id)!!.spec.durationMs).coerceIn(0f, 1f)
            } ?: 0f }
            val frames = cards.mapIndexed { i, card -> rigs[i].step(2f + seconds, input(card, progress[i])) }
            save(sheet(frames, progress), "frames/${index.toString().padStart(4, '0')}.png")
        }
    }
}
