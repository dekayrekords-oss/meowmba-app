package com.example.meowmba.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.example.meowmba.core.flow.FlowAction
import com.example.meowmba.core.flow.FlowItem
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.ui.companion.AvatarStyle
import com.example.meowmba.ui.companion.AppTheme
import com.example.meowmba.ui.companion.IdleAvatar
import com.example.meowmba.ui.companion.IdleAvatarLayer
import com.example.meowmba.ui.companion.IdleHomeTimer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Idle defaults to Flow; opted-in avatars retain ribbons and navigation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h900dp")
class IdleAvatarTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    /** Held clocks need composition and layout frames after an external change or touch. */
    private fun settleFrames() {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
    }

    private fun item(id: String, text: String, kind: FlowKind = FlowKind.PEOPLE) = FlowItem(
        intentionId = id, kind = kind, headline = text, because = "Messaged you in WhatsApp.",
        primary = FlowAction("primary", "Reply"), secondary = FlowAction("later", "Later"),
        urgency = Urgency.USEFUL, certainty = com.example.meowmba.core.flow.Certainty.CONFIRMED, sources = emptyList(), score = 1.0)

    @Test fun defaultsAndArrivals() {
        assertEquals("flow", IdleAvatar.keyOf(null))
        assertEquals("flow", IdleAvatar.keyOf("unknown"))
        listOf("lumen", "orb", "off").forEach { assertEquals(it, IdleAvatar.keyOf(it)) }
        assertEquals(AvatarStyle.SIGNAL, IdleAvatar.styleOf("orb"))
        assertEquals(AvatarStyle.LUMEN, IdleAvatar.styleOf("lumen"))
        val snap = FlowSnapshot.EMPTY.copy(main = item("a", "Reply to Dana"), more = listOf(item("setup-usage", "Allow"), item("b", "Buy milk", FlowKind.ERRAND)))
        assertEquals(listOf("b"), IdleAvatar.arrivals(snap, setOf("a")).map { it.intentionId })
    }

    @Test fun idleDeskOpensFlowWithoutShowingAnAvatar() {
        var theme by mutableStateOf(AppTheme.DESK)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IdleHomeTimer(theme = theme, onFlow = { theme = AppTheme.FLOW }) { idle, _ ->
                Text(if (idle) "AVATAR" else theme.name)
            }
        }
        rule.onNodeWithText("DESK").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(120_100)
        rule.waitForIdle()
        rule.onNodeWithText("FLOW").assertIsDisplayed()
        rule.onNodeWithText("AVATAR").assertDoesNotExist()
        // Back to the selected Desk gets a fresh inactivity window.
        rule.runOnIdle { theme = AppTheme.DESK }
        settleFrames()
        rule.onNodeWithText("DESK").assertIsDisplayed()
    }

    @Test fun optionalAvatarWaitsForInactivityAndPreservesItsTapAction() {
        var flows = 0
        var taps = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IdleHomeTimer(theme = AppTheme.DESK, mode = "orb", minutes = 1,
                onFlow = { flows++ }) { idle, wake ->
                Box(Modifier.fillMaxSize()) {
                    Text(if (idle) "AVATAR" else "DESK", Modifier.clickable { if (idle) { taps++; wake() } })
                }
            }
        }
        rule.mainClock.advanceTimeBy(30_000)
        rule.onNodeWithText("DESK").performTouchInput { click() }
        rule.mainClock.advanceTimeBy(45_000)
        rule.onNodeWithText("AVATAR").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(15_100)
        rule.onNodeWithText("AVATAR").assertIsDisplayed()
        rule.onNodeWithText("AVATAR").performTouchInput { click() }
        settleFrames()
        rule.onNodeWithText("DESK").assertIsDisplayed()
        assertEquals(1, taps)
        assertEquals(0, flows)
    }

    @Test fun idleDoesNotInterruptToolsPausedScreensOrAnExplicitAvatarHome() {
        var blocked by mutableStateOf(true)
        var paused by mutableStateOf(false)
        var theme by mutableStateOf(AppTheme.DESK)
        var mode by mutableStateOf("flow")
        var flows = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IdleHomeTimer(theme = theme, mode = mode, minutes = 1, blocked = blocked, paused = paused,
                onFlow = { flows++ }) { idle, _ -> Text(if (idle) "AVATAR" else "PAGE") }
        }
        rule.mainClock.advanceTimeBy(120_000)
        assertEquals(0, flows)
        rule.runOnIdle { blocked = false }
        settleFrames()
        rule.mainClock.advanceTimeBy(30_000)
        rule.runOnIdle { paused = true }
        settleFrames()
        rule.mainClock.advanceTimeBy(120_000)
        assertEquals(0, flows)
        rule.runOnIdle { paused = false; mode = "off" }
        settleFrames()
        rule.mainClock.advanceTimeBy(120_000)
        assertEquals(0, flows)
        rule.runOnIdle { mode = "flow"; theme = AppTheme.COMPANION }
        settleFrames()
        rule.mainClock.advanceTimeBy(120_000)
        assertEquals(0, flows)
        rule.runOnIdle { theme = AppTheme.DESK }
        settleFrames()
        rule.mainClock.advanceTimeBy(60_100)
        rule.waitForIdle()
        assertEquals(1, flows)
    }

    @Test fun newItemCrossesTheScreenAndSwipesOpenFlowOrChat() {
        var snap by mutableStateOf(FlowSnapshot.EMPTY.copy(main = item("a", "Already here")))
        var flows = 0; var chats = 0; var wakes = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IdleAvatarLayer(avatar = { Box { Text("AVATAR") } }, snapshot = snap,
                onWake = { wakes++ }, onFlow = { flows++ }, onChat = { chats++ }, onApps = {}, onMenu = {})
        }
        repeat(5) { rule.mainClock.advanceTimeByFrame() }
        rule.onNodeWithText("AVATAR").assertIsDisplayed()
        rule.onNodeWithTag("flow_ribbon", useUnmergedTree = true).assertDoesNotExist() // what was there before is not news
        // The clock is held: the ribbon takes several seconds to cross, then leaves.
        snap = snap.copy(more = listOf(item("b", "Reply to Sam")))
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        repeat(10) { rule.mainClock.advanceTimeByFrame() }
        rule.onNodeWithTag("flow_ribbon", useUnmergedTree = true).assertExists()
        rule.mainClock.advanceTimeBy(30_000)
        rule.onNodeWithTag("flow_ribbon", useUnmergedTree = true).assertDoesNotExist() // it crossed and left
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("idle_avatar").performTouchInput { swipeRight() }
        rule.onNodeWithTag("idle_avatar").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(1, flows); assertEquals(1, chats)
    }
}
