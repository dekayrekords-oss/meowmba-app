package com.example.meowmba.flow

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.meowmba.core.flow.Certainty
import com.example.meowmba.core.flow.FlowAction
import com.example.meowmba.core.flow.FlowItem
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.ui.desk.DeskFacts
import com.example.meowmba.ui.desk.DeskHabitat
import com.example.meowmba.ui.desk.DeskObject
import com.example.meowmba.ui.desk.DeskState
import com.example.meowmba.ui.desk.MonitorLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

class DeskStateTest {
    private fun item(id: String, kind: FlowKind, headline: String) =
        FlowItem(id, kind, headline, "", FlowAction("p", "Open"), null, Urgency.USEFUL, Certainty.CONFIRMED, emptyList())

    @Test fun bindsFlowItemsToObjects() {
        val snap = FlowSnapshot(1, emptyList(), item("sam", FlowKind.PEOPLE, "Reply to Sam"),
            listOf(item("bill", FlowKind.MONEY, "Card payment, \$412"), item("dana", FlowKind.PEOPLE, "Call Dana")), null, false, emptyList())
        val s = DeskState.from(snap, DeskFacts(nextEventTime = "3:30", nextEventWords = listOf("tester", "call!", "extra")))
        assertEquals(2, s.trayCount)
        assertEquals("\$412", s.jarNote)
        assertEquals(listOf("3:30", "tester", "call!"), s.sticky)
    }

    @Test fun emptyStatesStayEmpty() {
        val s = DeskState.from(FlowSnapshot.EMPTY, DeskFacts())
        assertEquals(0, s.trayCount); assertEquals(null, s.jarNote); assertTrue(s.sticky.isEmpty()); assertEquals(false, s.lampOn)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h960dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeskHabitatUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val demoState = DeskState(
        clockHour = 3, clockMinute = 40, night = true, weatherLine = "71° clear tonight",
        projectTitle = "MEOWMBA → PLAY", tallyDone = 9, tallyTotal = 14, projectStatus = "9 of 14 · in review",
        pinnedNote = listOf("build 155", "2730 ✓"),
        monitorLines = listOf(MonitorLine("$ ./gradlew test"), MonitorLine("2742 passed", true), MonitorLine("0 failed")),
        sticky = listOf("3:30", "tester", "call!"), trayCount = 3, jarNote = "\$412 tmrw", lampOn = true,
    )

    private fun shoot(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File("build/flow-shots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun rendersDemoDeskAndWalksToTappedObject() {
        var arrived: DeskObject? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DeskHabitat(demoState, wanderEveryMs = 0, lineFor = { "Three need you. I already drafted two replies." }, onArrive = { arrived = it })
        }
        rule.mainClock.advanceTimeBy(500)
        shoot("desk-01-idle")
        rule.onNodeWithContentDescription("Letter tray: 3 people need you").assertIsDisplayed()
        rule.onNodeWithTag("desk_inbox").performClick()
        rule.waitForIdle() // apply the tap's state change before driving frames
        // Step frame by frame: one big clock jump skips the walk's per-frame work.
        repeat(200) { rule.mainClock.advanceTimeByFrame() }
        rule.onNodeWithTag("desk_cat").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "resting at 250"))
        assertEquals(DeskObject.INBOX, arrived)
        rule.onNodeWithTag("desk_bubble").assertIsDisplayed()
        shoot("desk-02-at-tray")
    }
}
