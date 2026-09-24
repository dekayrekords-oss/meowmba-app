package com.example.meowmba.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.ui.desk.DeskFacts
import com.example.meowmba.ui.desk.DeskObject
import com.example.meowmba.ui.desk.DeskState
import com.example.meowmba.ui.flow.DeskWorkspace
import com.example.meowmba.ui.flow.HomeShelves
import com.example.meowmba.ui.flow.homeSwipes
import androidx.compose.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Owner 2026-09-23: clock opens Android Clock, drawers are app/tool folders, a movable 6-app dock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h900dp")
class HomeShelvesTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun defaultsSaveAndReload() {
        val prefs = HashMap<String, String>()
        val s = HomeShelves(prefs::get, { k, v -> prefs[k] = v }) { listOf("com.a", "com.b", "com.a") }
        val st = s.state.value
        assertEquals(listOf("app:com.a", "app:com.b", null, null, null, null), st.dock)
        assertEquals(HomeShelves.DEFAULT_TOOLS.map(HomeShelves::tool), st.topDrawer)
        assertEquals(listOf("app:com.a", "app:com.b"), st.bottomDrawer)
        s.setDockSlot(4, "tool:Notes"); s.moveDock(right = false, y = 0.2f); s.addToDrawer(top = false, key = "tool:Weather")
        s.removeFromDrawer(top = true, key = "tool:Files")
        val again = HomeShelves(prefs::get, { k, v -> prefs[k] = v }) { emptyList() }.state.value
        assertEquals("tool:Notes", again.dock[4]); assertEquals(false, again.dockRight); assertEquals(0.2f, again.dockY, 0.001f)
        assertTrue("tool:Weather" in again.bottomDrawer); assertTrue("tool:Files" !in again.topDrawer)
    }

    @Test fun clockOpensClockAndDrawersOpenTheirFolder() {
        var clock = 0
        rule.setContent {
            DeskWorkspace(DeskState.from(FlowSnapshot.EMPTY, DeskFacts()), emptyList(), thermalStatus = 0,
                onClock = { clock++ }, drawerContent = { top, _ -> Text(if (top) "TOP FOLDER" else "BOTTOM FOLDER") })
        }
        rule.onNodeWithTag("desk_" + DeskObject.CLOCK.name.lowercase()).performClick()
        assertEquals(1, clock)
        rule.onNodeWithTag("desk_" + DeskObject.DRAWER_BOTTOM.name.lowercase()).performClick()
        repeat(40) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        rule.onNodeWithText("BOTTOM FOLDER").assertIsDisplayed()
        rule.onNodeWithText("Bottom drawer").assertIsDisplayed()
    }

    @Test fun draggingSomethingThatHandlesTheDragIsNotAHomeSwipe() {
        var apps = 0
        rule.setContent {
            Box(Modifier.fillMaxSize().homeSwipes(onUp = { apps++ }, onDown = {}, onLeft = {}, onRight = {})) {
                // A draggable child (like the dock handle) consumes its drag.
                Box(Modifier.fillMaxSize().pointerDragConsumer())
            }
        }
        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertEquals(0, apps)
    }

    private fun Modifier.pointerDragConsumer() = this.then(Modifier.pointerInput(Unit) {
        detectDragGestures { change, _ -> change.consume() }
    })
}
