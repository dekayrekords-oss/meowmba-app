package com.example.meowmba.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.viewModelScope
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.flow.DockStyle
import com.example.meowmba.ui.flow.HomeDock
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Owner: the dock should act like the Lumen screen's edge tab — hidden on the edge until pulled out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h900dp")
class HomeDockEdgeTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val vm = CompanionViewModel()
    @After fun tearDown() = vm.viewModelScope.cancel()

    @Test fun restsHiddenOnTheEdgeOpensByTapAndTucksAwayBySwipe() {
        rule.setContent { HomeDock(vm, DockStyle.DESK) }
        rule.waitForIdle()
        rule.onNodeWithTag("home_dock_handle").assertIsDisplayed()
        rule.onNodeWithTag("home_dock_panel").assertDoesNotExist()
        assertTrue(vm.homeShelves.state.value.dockCollapsed)
        val closedGrip = rule.onNodeWithTag("home_dock_handle").getBoundsInRoot()

        rule.onNodeWithTag("home_dock_handle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home_dock_panel").assertIsDisplayed()
        (0 until 6).forEach { rule.onNodeWithTag("home_dock_slot_$it").assertIsDisplayed() }
        assertFalse(vm.homeShelves.state.value.dockCollapsed)
        // Owner: the three bars must not jump when the dock opens or closes.
        assertEquals(closedGrip, rule.onNodeWithTag("home_dock_handle").getBoundsInRoot())

        // Swipe outward (toward the right edge), starting on an icon, to tuck it away.
        rule.onNodeWithTag("home_dock_panel").performTouchInput { swipeRight(startX = centerX - 20f, endX = centerX + 140f) }
        rule.waitForIdle()
        assertTrue(vm.homeShelves.state.value.dockCollapsed)
        assertEquals(closedGrip, rule.onNodeWithTag("home_dock_handle").getBoundsInRoot())
    }
}
