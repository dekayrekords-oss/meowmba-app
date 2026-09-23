package com.example.meowmba.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.ui.desk.DeskFacts
import com.example.meowmba.ui.desk.DeskState
import com.example.meowmba.ui.flow.DeskWorkspace
import com.example.meowmba.ui.flow.MeowFlowHome
import com.example.meowmba.ui.flow.homeSwipes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Flow and the Desk keep the avatar screen's home swipes, and taps underneath still work. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h900dp")
class HomeSwipesUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val log = mutableListOf<String>()
    private val swipes = Modifier.fillMaxSize().homeSwipes(onUp = { log += "apps" }, onDown = { log += "menu" },
        onLeft = { log += "browser" }, onRight = { log += "chat" })

    private fun allFour() {
        rule.onRoot().performTouchInput { swipeUp() }
        rule.onRoot().performTouchInput { swipeDown() }
        rule.onRoot().performTouchInput { swipeRight() }
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf("apps", "menu", "chat", "browser"), log.filter { it != "mic" && it != "tray" })
    }

    @Test fun flowSwipesAndTapsBothWork() {
        rule.setContent { Box(swipes) { MeowFlowHome(FlowSnapshot.EMPTY, FlowSettings(), "Wed 23 Sep", onMic = { log += "mic" }) } }
        allFour()
        rule.onNodeWithTag("flow_mic").performClick()
        assertEquals("mic", log.last())
    }

    @Test fun deskSwipesAndTapsBothWork() {
        val state = DeskState.from(FlowSnapshot.EMPTY, DeskFacts())
        rule.setContent { Box(swipes) { DeskWorkspace(state, emptyList(), thermalStatus = 0) } }
        allFour()
        rule.onNodeWithTag("desk_inbox").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("desk_detail").assertExists()
    }
}
