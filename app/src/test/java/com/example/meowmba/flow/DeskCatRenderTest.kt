package com.example.meowmba.flow

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.ui.desk.*
import com.example.meowmba.ui.desk.DeskPainter.drawCat
import com.example.meowmba.ui.desk.DeskPainter.drawObjects
import com.example.meowmba.ui.desk.DeskPainter.drawRoom
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h820dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskCatRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun activePosesActuallyMoveAndRenderInTheRoom() {
        var pose by mutableStateOf(DeskCatPose.GROOM)
        var phase by mutableFloatStateOf(0f)
        rule.setContent {
            val measurer = rememberTextMeasurer()
            val state = remember { DeskState.from(FlowSnapshot.EMPTY, DeskFacts(projectTitle = "Play in Europe")) }
            Canvas(Modifier.fillMaxSize()) {
                withTransform({ scale(size.width / 412f, size.width / 412f, Offset.Zero) }) {
                    drawRoom(); drawObjects(state, measurer)
                    val spot = when (pose) {
                        DeskCatPose.TYPE -> DeskCatBehavior.computer
                        DeskCatPose.WRITE -> DeskCatBehavior.board
                        DeskCatPose.LETTER -> DeskCatBehavior.letters
                        else -> DeskCatBehavior.home
                    }
                    drawCat(spot.x, spot.y, false, 0, 0f, 0f, 0f,
                        pose == DeskCatPose.TYPE || pose == DeskCatPose.WRITE, pose, phase)
                }
            }
        }
        fun bitmap(): Bitmap {
            rule.waitForIdle()
            val view = rule.activity.window.decorView
            return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        val out = File("build/flow-shots").apply { mkdirs() }
        for (p in listOf(DeskCatPose.GROOM, DeskCatPose.STRETCH, DeskCatPose.TYPE, DeskCatPose.LETTER, DeskCatPose.WRITE, DeskCatPose.TALK)) {
            rule.runOnIdle { pose = p; phase = 0f }
            val first = bitmap()
            rule.runOnIdle { phase = (Math.PI / 2).toFloat() }
            val next = bitmap()
            assertFalse("$p must visibly move with its phase", first.sameAs(next))
            File(out, "desk-cat-${p.name.lowercase()}.png").outputStream().use { next.compress(Bitmap.CompressFormat.PNG, 100, it) }
            first.recycle(); next.recycle()
        }
    }
}
