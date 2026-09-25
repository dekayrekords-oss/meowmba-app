package com.example.meowmba.flow

import android.graphics.Bitmap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.ui.desk.DeskCatActivity
import com.example.meowmba.ui.desk.DeskCatReply
import com.example.meowmba.ui.desk.DeskFacts
import com.example.meowmba.ui.desk.DeskHabitat
import com.example.meowmba.ui.desk.DeskState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h960dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskCatUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val desk = DeskState.from(FlowSnapshot.EMPTY, DeskFacts())

    private fun shoot(name: String) {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File("build/flow-shots").apply { mkdirs() }.resolve("$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun pausedThinkingCatUsesTheComputerWithoutAdvancingFrames() {
        rule.mainClock.autoAdvance = false
        rule.setContent { DeskHabitat(desk, reduceMotion = true, activity = DeskCatActivity.THINKING, wanderEveryMs = 0) }
        rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        rule.onNodeWithTag("desk_cat").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "using the computer"))
        fun capture(): Bitmap {
            val view = rule.activity.window.decorView
            return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        val before = capture()
        rule.mainClock.advanceTimeBy(750); rule.waitForIdle()
        val after = capture()
        assertTrue("Reduced motion must freeze the drawing, not just its label", before.sameAs(after))
        before.recycle(); after.recycle()
        rule.onNodeWithTag("desk_cat").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "using the computer"))
    }

    @Test fun longMultilineReplyIsScrollableDismissibleAndKeepsDeskVisible() {
        val reply = (1..40).joinToString("\n") { "Answer line $it is real response detail that remains available to read." }
        rule.mainClock.autoAdvance = false
        rule.setContent { DeskHabitat(desk, reduceMotion = true, reply = DeskCatReply("7:12", reply), wanderEveryMs = 0) }
        rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        rule.onNodeWithText(reply, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("desk_reply_text").assertIsDisplayed().assert(hasScrollAction()).performTouchInput { swipeUp() }
        rule.onNodeWithTag("desk_habitat").assertIsDisplayed()
        shoot("desk-cat-reply-portrait")
        rule.onNodeWithTag("desk_reply_close").performClick()
        rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        rule.onNodeWithTag("desk_reply_text").assertDoesNotExist()
        rule.onNodeWithTag("desk_habitat").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w840dp-h460dp-xxhdpi")
    fun longReplyFitsLandscapeDesk() {
        val reply = "Landscape reply.\n".repeat(40)
        rule.mainClock.autoAdvance = false
        rule.setContent { DeskHabitat(desk, reduceMotion = true, reply = DeskCatReply("7:13", reply), wanderEveryMs = 0) }
        rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        rule.onNodeWithTag("desk_reply_text").assertIsDisplayed().assert(hasScrollAction())
        shoot("desk-cat-reply-landscape")
    }

}
