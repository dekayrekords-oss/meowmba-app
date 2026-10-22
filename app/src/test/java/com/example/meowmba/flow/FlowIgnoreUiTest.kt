package com.example.meowmba.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.ui.companion.SwipeAway
import com.example.meowmba.ui.flow.FlowSamples
import com.example.meowmba.ui.flow.MeowFlowHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h960dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlowIgnoreUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun everyFlowItemOffersIgnoreAndReportsWhichOne() {
        val ignored = mutableListOf<String>()
        rule.setContent {
            MeowFlowHome(FlowSamples.deepWork, FlowSettings(), "Tue 22 Sept · 9:40 AM", onIgnore = { ignored += it.intentionId })
        }
        rule.mainClock.advanceTimeBy(4_000); rule.waitForIdle()
        val links = rule.onAllNodesWithText("Ignore")
        val count = links.fetchSemanticsNodes().size
        assertTrue("main thought and each 'also for you' row get an Ignore link, saw $count", count >= 2)
        links[0].performClick()
        assertEquals(listOf("handoff"), ignored)
    }

    @Test fun ignoreIsHiddenWhenNothingHandlesIt() {
        rule.setContent { MeowFlowHome(FlowSamples.deepWork, FlowSettings(), "Tue 22 Sept · 9:40 AM") }
        rule.mainClock.advanceTimeBy(4_000); rule.waitForIdle()
        rule.onAllNodesWithText("Ignore").assertCountEquals(0)
    }

    @Test fun laterStillReportsTheTappedItem() {
        val later = mutableListOf<String>()
        rule.setContent {
            MeowFlowHome(FlowSamples.deepWork, FlowSettings(), "Tue 22 Sept · 9:40 AM", onLater = { later += it.intentionId })
        }
        rule.mainClock.advanceTimeBy(4_000); rule.waitForIdle()
        rule.onAllNodesWithText("Later")[0].performClick()
        assertEquals(1, later.size)
    }

    @Test fun aNoticeSwipesAwayInEitherDirectionButNotOnATap() {
        var dismissed = 0
        rule.setContent {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                SwipeAway(onDismiss = { dismissed++ }, modifier = Modifier.testTag("notice")) {
                    Box(Modifier.width(300.dp).height(60.dp)) { Text("Cooling animations") }
                }
            }
        }
        rule.onNodeWithTag("notice").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals("a tap does not dismiss", 0, dismissed)
        rule.onNodeWithTag("notice").performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test fun aNoticeDraggedRightAlsoGoes() {
        var dismissed = 0
        rule.setContent {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                SwipeAway(onDismiss = { dismissed++ }, modifier = Modifier.testTag("notice")) {
                    Box(Modifier.width(300.dp).height(60.dp)) { Text("Cooling animations") }
                }
            }
        }
        rule.onNodeWithTag("notice").performTouchInput { swipeRight() }
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        assertEquals(1, dismissed)
    }
}
