package com.example.meowmba

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.ui.companion.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IdleRendering136Test {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun retainedRoomMatchesOriginalPixelsAcrossAnimationAndViewportChanges() {
        val rooms = BackgroundAnimation.entries.filter { it.isPetEnvironment }
        val images = rooms.associateWith { room ->
            RuntimeEnvironment.getApplication().assets.open("backgrounds/painted/${room.id}.png")
                .use { requireNotNull(BitmapFactory.decodeStream(it)).asImageBitmap() }
        }
        var room by mutableStateOf(rooms.first())
        var phase by mutableFloatStateOf(.13f)
        var wide by mutableStateOf(false)
        var retainedMode by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val image = images.getValue(room)
                val bounds = Modifier.requiredSize(if (wide) 360.dp else 180.dp, if (wide) 180.dp else 360.dp)
                val original = remember { HabitatObjectAnimations() }
                val retained = remember { HabitatObjectAnimations() }
                if (retainedMode) IllustratedRoomLayers(image, room, retained, { phase }, bounds.testTag("room"))
                else Canvas(bounds.testTag("room")) { drawIllustratedRoom(image, room, original, phase) }
            }
        }
        for (next in rooms) for (landscape in listOf(false, true)) for (time in listOf(.13f, .39f)) {
            rule.runOnIdle { room = next; wide = landscape; phase = time }
            fun capture(retain: Boolean): Bitmap {
                rule.runOnIdle { retainedMode = retain }
                val bounds = rule.onNodeWithTag("room").fetchSemanticsNode().boundsInWindow
                return rule.runOnIdle {
                    val root = rule.activity.window.decorView
                    val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    root.draw(android.graphics.Canvas(bitmap))
                    Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(),
                        bounds.width.toInt(), bounds.height.toInt()).also { bitmap.recycle() }
                }
            }
            val original = capture(false)
            val retained = capture(true)
            if (!original.sameAs(retained)) {
                for ((name, frame) in listOf("original" to original, "retained" to retained)) {
                    val out = java.io.File("build/reports/idle136/$name.png")
                    out.parentFile?.mkdirs()
                    out.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            assertTrue("${next.id}, landscape=$landscape, phase=$time: pixels changed", original.sameAs(retained))
            original.recycle(); retained.recycle()
        }
    }
}
