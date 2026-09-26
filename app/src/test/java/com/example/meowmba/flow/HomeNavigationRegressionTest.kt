package com.example.meowmba.flow

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.ui.desk.*
import com.example.meowmba.ui.flow.*
import com.example.meowmba.ui.companion.BackgroundTiles
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h960dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeNavigationRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val desk = DeskState.from(FlowSnapshot.EMPTY, DeskFacts())

    private fun checkEveryObject(reducedMotion: Boolean, allRooms: Boolean = false) {
        rule.mainClock.autoAdvance = false
        var clocks = 0
        rule.setContent { MeowmbaTheme {
            DeskWorkspace(desk, emptyList(), paused = reducedMotion, onClock = { clocks++ })
        } }
        rule.mainClock.advanceTimeBy(300)
        // The clock hands over to Android's Clock app instead of opening a panel.
        rule.onNodeWithTag("desk_${DeskObject.CLOCK.name.lowercase()}").performTouchInput { click(center) }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(1, clocks)
        // Objects the cat uses on its own open no panel; the arcade launches MAME when installed.
        // Folded, only the office is on screen; the wide layout shows all three rooms.
        DeskObject.entries.filter { it != DeskObject.CLOCK && !it.catOnly && (allRooms || it.room == DeskRoom.OFFICE) }.forEach { obj ->
            // Coordinate dispatch catches overlays, offset/scale errors and tiny hit targets.
            rule.onNodeWithTag("desk_${obj.name.lowercase()}").performTouchInput { click(center) }
            rule.waitForIdle()
            repeat(180) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
            try { rule.onNodeWithTag("desk_detail").assertIsDisplayed() } catch (e: AssertionError) { throw AssertionError("$obj opened no panel", e) }
            rule.onNode(hasText(deskTitle(obj)) and hasAnyAncestor(hasTestTag("desk_detail")), useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithText("Close").performTouchInput { click() }
            rule.waitForIdle()
            repeat(40) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
            rule.onNodeWithTag("desk_detail").assertDoesNotExist()
        }
    }

    @Test fun everyDeskObjectOpensItsOwnSheetByTouch() = checkEveryObject(false)

    @Test @Config(qualifiers = "w840dp-h460dp-xxhdpi")
    fun centeredWideDeskTargetsWorkWithReducedMotion() = checkEveryObject(true, allRooms = true)

    @Test fun deskHasVisibleMenuSettingsAndFlowWithoutGestures() {
        var menus = 0; var settings = 0; var flows = 0
        rule.setContent { MeowmbaTheme {
            DeskWorkspace(desk, emptyList(), paused = true,
                onMenu = { menus++ }, onSettings = { settings++ }, onFlow = { flows++ })
        } }
        listOf("Menu", "Settings", "Flow").forEach {
            rule.onNodeWithText(it).assertIsDisplayed().performTouchInput { click() }
        }
        assertEquals(1, menus); assertEquals(1, settings); assertEquals(1, flows)
    }

    @Test fun flowHasMatchingNavigationAndMenuKeepsAllToolsReachable() {
        var menu by mutableStateOf(false)
        var selected = ""
        rule.setContent { MeowmbaTheme {
            MeowFlowHome(FlowSnapshot.EMPTY, FlowSettings(), "WEDNESDAY", reduceMotion = true,
                navigation = { FlowHomeNavigation({ menu = true }, { selected = "Add" }, { selected = "Desk" }, { selected = "Settings" }) })
            BackgroundTiles(menu, { menu = false }, { selected = it; menu = false })
        } }
        rule.onNodeWithText("Settings").performTouchInput { click() }; assertEquals("Settings", selected)
        rule.onNodeWithText("Add task").performTouchInput { click() }; assertEquals("Add", selected)
        rule.onNodeWithText("Menu").performTouchInput { click() }
        rule.onNodeWithText("Meow menu").assertIsDisplayed()
        rule.onNodeWithText("Computer").assertIsDisplayed().performTouchInput { click() }
        assertEquals("Computer", selected)
        rule.onNodeWithText("Meow menu").assertDoesNotExist()
    }
}
