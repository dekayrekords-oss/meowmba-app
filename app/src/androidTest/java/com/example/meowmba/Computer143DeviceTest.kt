package com.example.meowmba

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.meowmba.ui.companion.CompanionViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Read-only UI acceptance: never starts a server, enables sensing or sends private data. */
class Computer143DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun tap(text: String) {
        // Use the accessibility action while the animation clock is paused.
        // Coordinate scrolling can wait forever for a frozen layout frame.
        rule.onNode(hasText(text) and hasClickAction()).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        rule.mainClock.advanceTimeBy(500)
    }
    private fun back() {
        rule.onNodeWithContentDescription("Back in Computer").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        rule.mainClock.advanceTimeBy(500)
    }
    @Test fun recoveryAndOptionalModesAreReachableWithoutEnablingMonitoring() {
        // The real avatar intentionally has a never-ending frame clock. Do not
        // make Espresso wait for that animation to finish before navigating.
        rule.mainClock.autoAdvance = false
        lateinit var vm: CompanionViewModel
        rule.activityRule.scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            vm = ViewModelProvider(it)[CompanionViewModel::class.java]
        }
        val before = vm.uiState.value
        rule.activityRule.scenario.onActivity { vm.openComputerTools() }
        rule.mainClock.advanceTimeBy(500)
        tap("HUB mode")
        rule.onNodeWithText("HUB is off. No screen is being read.").assertExists()
        back()
        tap("Personal companion")
        val switches = rule.onAllNodes(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Switch))
        switches.assertCountEquals(2)
        switches[0].assertIsOff()
        switches[1].assertIsOff()
        assertTrue(rule.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
        back()
        tap("Set up Computer"); tap("This phone")
        val guided = com.example.meowmba.core.computer.TermuxSetupSupport.detect(rule.activity) == com.example.meowmba.core.computer.TermuxSetupSupport.GUIDED
        if (guided) {
            rule.onNodeWithText("Termux detected · guided setup needed").assertExists()
            rule.onNodeWithText("Get Termux from F-Droid (recommended)").assertDoesNotExist()
        }
        tap("Continue")
        if (guided) {
            rule.onNodeWithText("Allow Meow to connect to Termux").assertDoesNotExist()
            rule.onNodeWithText("Copy setup & open Termux").assertExists()
            rule.onNodeWithText("Verify connection").assertExists()
        }
        tap("Forgot password? Create a separate Meow connection")
        rule.onNodeWithText("Create a separate private connection?").assertExists()
        tap("Choose new password")
        rule.onNodeWithText("Choose a NEW connection password").assertExists()
        rule.onNodeWithText("http://127.0.0.1:4097 · username opencode").assertExists()
        assertEquals(before.isCameraEnabled, vm.uiState.value.isCameraEnabled)
        assertEquals(before.isMicEnabled, vm.uiState.value.isMicEnabled)
    }
}
