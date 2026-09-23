package com.example.meowmba.flow

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowPolicy
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.core.flow.Intention
import com.example.meowmba.ui.companion.AppTheme
import com.example.meowmba.ui.companion.AvatarStyle
import com.example.meowmba.ui.companion.ProjectUi
import com.example.meowmba.ui.desk.DeskState
import com.example.meowmba.ui.flow.DeskWorkspace
import com.example.meowmba.ui.flow.deskFacts
import org.junit.Assert.assertEquals
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
class DeskModeUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val now = Fx.at(15, 12)
    private val tasks = listOf(
        Intention("cal-1", "Tester check-in at 3:30 PM", FlowKind.PEOPLE, "", now, dueAt = Fx.at(15, 30)),
        Intention("msg-1", "Reply to Dana", FlowKind.PEOPLE, "", now, facts = mapOf(FlowPolicy.FACT_BECAUSE to "Messaged you in WhatsApp.")),
        Intention("project-7", "Continue Meowmba site", FlowKind.WORK, "", now),
        Intention("cap-1", "Fix the rear light", FlowKind.ERRAND, "", now),
        Intention("m-1", "Card payment, \$412", FlowKind.MONEY, "", now),
    )

    private fun shoot(name: String) {
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888); v.draw(android.graphics.Canvas(bmp))
        File("build/flow-shots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun deskIsAModeNotAnAvatar() {
        assertEquals(AppTheme.DESK, AppTheme.fromKey("desk"))
        assertTrue(AvatarStyle.entries.none { it.key == "desk" })
        assertEquals(AvatarStyle.DEFAULT, AvatarStyle.fromKey("desk"))
    }

    @Test fun deskShowsRealItemsAndOpensPanelsOnTap() {
        val facts = deskFacts(now, tasks, ProjectUi(7, "Meowmba site", "web", "Website", "Today", 1), "82° cloudy", false, Fx.zone)
        val state = DeskState.from(FlowSnapshot.EMPTY, facts, tasks)
        assertEquals(2, state.trayCount)
        assertEquals("\$412", state.jarNote)
        assertEquals("3:30", state.sticky.first())
        var done: String? = null
        rule.mainClock.autoAdvance = false
        rule.setContent { DeskWorkspace(state, tasks, thermalStatus = 0, onTaskDone = { done = it }) }
        rule.mainClock.advanceTimeBy(500)
        shoot("desk-mode-01")
        rule.onNodeWithTag("desk_inbox").performClick()
        // ~0.7 s of frames: the panel is up before the cat could finish walking (~1.1 s).
        repeat(40) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        rule.onNodeWithTag("desk_detail").assertIsDisplayed()
        rule.onNodeWithText("Reply to Dana").assertIsDisplayed()
        shoot("desk-mode-02-tray")
        rule.onNodeWithTag("desk_task_msg-1").assertIsDisplayed()
        rule.onNodeWithText("Messaged you in WhatsApp.").assertIsDisplayed()
    }
}
