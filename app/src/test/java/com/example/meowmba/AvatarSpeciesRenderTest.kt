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
class AvatarSpeciesRenderTest {
    private val species = AvatarAnimal.entries.filter { it != AvatarAnimal.NONE }
    private val painter = LumenPainter()
    private fun settled(input: LumenInput): LumenFrame {
        val rig = LumenRig(); var f = LumenFrame()
        repeat(120) { f = rig.step(it / 60f, input) }
        return f
    }

    private fun render(f: LumenFrame, w: Int, h: Int, privacy: Boolean = false): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            painter.draw(this, f, Color(AvatarColors.DEFAULT_EYE), Color(0xFF419A9C), featureColor = Color(0xFFDCCAA8),
                cameraOff = privacy, micOff = privacy, speakerOff = privacy)
        }
        return b
    }

    @Test fun everyCharacterKeepsItsAnimatedPupilsVisible() {
        val frames = species.map { settled(LumenInput(0f, 0f, true, animal = it)) }
        val images = frames.map { render(it, 600, 640) }
        for (i in images.indices) {
            val frame = frames[i]
            val center = lumenCenter(600f, 640f, frame)
            val unit = lumenUnit(600f, 640f, frame)
            for (side in listOf(-1, 1)) {
                val x = (center.x + side * unit * .213f).toInt()
                val y = center.y.toInt()
                for (dx in -5..5) for (dy in -5..5)
                    assertTrue("unobscured pupil ${species[i]}", images[i].getPixel(x + dx, y + dy).let {
                        android.graphics.Color.alpha(it) > 230 && android.graphics.Color.red(it) < 120
                    })
            }
            for (other in 0 until i) {
                var differences = 0
                for (x in 0 until 600 step 2) for (y in 0 until 640 step 2)
                    if (images[i].getPixel(x, y) != images[other].getPixel(x, y)) differences++
                assertTrue("distinct visible species anatomy", differences > 1_000)
            }
        }
        images.forEach(Bitmap::recycle)
    }

    @Test fun strongestSpeciesDrivePosesStayInsideTheExistingStageAndPrivacyNeverMoves() {
        for ((w, h) in listOf(360 to 800, 360 to 360, 720 to 520, 1600 to 500)) {
            val privacy = render(LumenFrame(presence = 0f), w, h, privacy = true)
            for (animal in species) for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) {
                val frame = settled(LumenInput(x, y, true, animal = animal, lean = x, lurch = y,
                    driveWeight = 1f, brace = 1f, screenRoll = x * 14f, tilt = x * 18f))
                val b = render(frame, w, h)
                for (px in 0 until w) {
                    assertTrue("top $animal $w/$h", android.graphics.Color.alpha(b.getPixel(px, 0)) < 110)
                    assertTrue("bottom $animal $w/$h", android.graphics.Color.alpha(b.getPixel(px, h - 1)) < 110)
                }
                for (py in 0 until h) {
                    assertTrue("left $animal $w/$h", android.graphics.Color.alpha(b.getPixel(0, py)) < 110)
                    assertTrue("right $animal $w/$h", android.graphics.Color.alpha(b.getPixel(w - 1, py)) < 110)
                }
                val hidden = render(frame.copy(presence = 0f), w, h, privacy = true)
                assertTrue("fixed privacy $animal", privacy.sameAs(hidden))
                b.recycle(); hidden.recycle()
            }
            privacy.recycle()
        }
    }

    @Test fun nativeSpeciesContactSheetForVisualReview() {
        val sheet = Bitmap.createBitmap(1920, ((species.size + 3) / 4) * 690 + 60, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        canvas.drawColor(0xFF0B131B.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDBCDB6.toInt(); textSize = 25f }
        for ((index, animal) in species.withIndex()) {
            val frame = settled(LumenInput(0f, -.03f, true, animal = animal))
            val b = render(frame, 480, 620)
            canvas.drawBitmap(b, (index % 4) * 480f, (index / 4) * 690f + 45f, null)
            canvas.drawText(animal.label, (index % 4) * 480f + 24f, (index / 4) * 690f + 40f, text)
            val output = File("build/reports/avatar-species/${animal.key}.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle()
        }
        canvas.drawText("Native character designs / species eyes / scripted renderer preview", 24f, sheet.height - 20f, text)
        File("build/reports/avatar-species/contact.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sheet.recycle()
    }

    @Test fun newFormsRemainFloatingFeaturesWithoutOpaqueHeads() {
        for (animal in listOf(AvatarAnimal.RABBIT, AvatarAnimal.PIG, AvatarAnimal.ALIEN, AvatarAnimal.ROBOT, AvatarAnimal.PANDA, AvatarAnimal.FRUIT_FLY)) {
            val f = settled(LumenInput(0f, 0f, true, animal = animal))
            val b = render(f, 480, 760)
            val center = lumenCenter(480f, 760f, f); val unit = lumenUnit(480f, 760f, f)
            for (dy in listOf(-.12f, -.07f, 0f)) {
                val pixel = b.getPixel(center.x.toInt(), (center.y + dy * unit).toInt())
                assertTrue("$animal has an open gap between its eyes", android.graphics.Color.alpha(pixel) < 100)
            }
            b.recycle()
        }
    }

    @Test fun partialEarMorphsFitAnAlreadyRoamingCharacter() {
        val parts = LumenCharacterParts()
        for (weight in listOf(.03f, .1f, .25f, .5f, .75f, 1f)) {
            for (form in listOf(AvatarFormTraits(rabbitFur = weight), AvatarFormTraits(pigSkin = weight),
                AvatarFormTraits(robotShell = weight), AvatarFormTraits(pandaFur = weight))) {
                for (x in listOf(-1f, 1f)) {
                    val frame = LumenFrame(form = form, driftX = x, driftY = -1f)
                    val b = Bitmap.createBitmap(840, 360, Bitmap.Config.ARGB_8888)
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(840f, 360f)) {
                        parts.backdrop(this, lumenCenter(840f, 360f, frame), lumenUnit(840f, 360f, frame), frame, Color(0xFFE8D5B9))
                    }
                    for (px in 0 until 840) assertEquals("ear clipped at weight $weight", 0, android.graphics.Color.alpha(b.getPixel(px, 0)))
                    for (py in 0 until 360) {
                        assertEquals("left anatomy clipped at $weight", 0, android.graphics.Color.alpha(b.getPixel(0, py)))
                        assertEquals("right anatomy clipped at $weight", 0, android.graphics.Color.alpha(b.getPixel(839, py)))
                    }
                    b.recycle()
                }
            }
        }
    }

    @Test fun newFloatingEyesStillBlinkAndExpressConcernAndJoy() {
        val expressions = listOf("Attentive", "Joy", "Concern", "Blink", "Curious")
        val sheet = Bitmap.createBitmap(1600, species.size * 260, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(sheet).apply { drawColor(0xFF0B131B.toInt()) }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD2CEBFL.toInt(); textSize = 14f }
        for ((row, animal) in species.withIndex()) {
            val base = settled(LumenInput(0f, 0f, true, animal = animal))
            val frames = listOf(base, base.copy(smile = .8f, warmth = .7f, lowerLidLeft = .20f, lowerLidRight = .20f),
                base.copy(innerLift = .4f, concern = .8f, leftOpen = .85f, rightOpen = .85f),
                base.copy(leftOpen = 0f, rightOpen = 0f), base.copy(gazeX = .65f, roll = 9f, rightOpen = .78f))
            val images = frames.map { render(it, 320, 240) }
            for (i in images.indices) {
                for (j in 0 until i) assertFalse("$animal ${expressions[i]} visibly changes", images[i].sameAs(images[j]))
                c.drawBitmap(images[i], i * 320f, row * 260f + 20f, null)
                c.drawText("${animal.label} · ${expressions[i]}", i * 320f + 12f, row * 260f + 18f, text)
            }
            images.forEach(Bitmap::recycle)
        }
        File("build/reports/avatar-species/emotions.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sheet.recycle()
    }
}
