package com.example.meowmba.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
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
import com.example.meowmba.ui.companion.IdleAvatar
import com.example.meowmba.ui.companion.IdleAvatarLayer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Owner 2026-09-23: idle → Lumen/Orb; new things cross the screen; L→R Flow, R→L chat. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h900dp")
class IdleAvatarTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun item(id: String, text: String, kind: FlowKind = FlowKind.PEOPLE) = FlowItem(
        intentionId = id, kind = kind, headline = text, because = "Messaged you in WhatsApp.",
        primary = FlowAction("primary", "Reply"), secondary = FlowAction("later", "Later"),
        urgency = Urgency.USEFUL, certainty = com.example.meowmba.core.flow.Certainty.CONFIRMED, sources = emptyList(), score = 1.0)

    @Test fun defaultsAndArrivals() {
        assertEquals("lumen", IdleAvatar.keyOf(null))
        assertEquals(AvatarStyle.SIGNAL, IdleAvatar.styleOf("orb"))
        assertEquals(AvatarStyle.LUMEN, IdleAvatar.styleOf("lumen"))
        val snap = FlowSnapshot.EMPTY.copy(main = item("a", "Reply to Dana"), more = listOf(item("setup-usage", "Allow"), item("b", "Buy milk", FlowKind.ERRAND)))
        assertEquals(listOf("b"), IdleAvatar.arrivals(snap, setOf("a")).map { it.intentionId })
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
