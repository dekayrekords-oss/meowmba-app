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
    private fun tap(text: String) = rule.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    @Test fun recoveryAndOptionalModesAreReachableWithoutEnablingMonitoring() {
        lateinit var vm: CompanionViewModel
        rule.activityRule.scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            vm = ViewModelProvider(it)[CompanionViewModel::class.java]
        }
        val before = vm.uiState.value
        rule.runOnIdle { vm.openComputerTools() }
        tap("HUB mode")
        rule.onNodeWithText("HUB is off. No screen is being read.").assertExists()
        rule.onNodeWithContentDescription("Back in Computer").performClick()
        tap("Personal companion")
        rule.onAllNodes(isToggleable()).assertCountEquals(2)
        rule.onAllNodes(isToggleable())[0].assertIsOff()
        rule.onAllNodes(isToggleable())[1].assertIsOff()
        assertTrue(rule.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
        rule.onNodeWithContentDescription("Back in Computer").performClick()
        tap("Set up Computer"); tap("This phone"); tap("Continue")
        tap("Forgot password? Create a separate Meow connection")
        rule.onNodeWithText("Create a separate private connection?").assertExists()
        rule.onNodeWithText("Choose new password").performClick()
        rule.onNodeWithText("Choose a NEW connection password").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("http://127.0.0.1:4097 · username opencode").assertExists()
        assertEquals(before.isCameraEnabled, vm.uiState.value.isCameraEnabled)
        assertEquals(before.isMicEnabled, vm.uiState.value.isMicEnabled)
    }
}
