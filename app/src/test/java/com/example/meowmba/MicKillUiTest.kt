package com.example.meowmba

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.ui.companion.MicKillButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MicKillUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun buttonClearlyTogglesCaptureAvailability() {
        compose.setContent {
            var enabled by remember { mutableStateOf(true) }
            MicKillButton(enabled, { enabled = it })
        }
        compose.onNodeWithText("MIC ON").assertIsDisplayed()
        compose.onNodeWithContentDescription("Turn microphone off").performClick()
        compose.onNodeWithText("MIC OFF").assertIsDisplayed()
        compose.onNodeWithContentDescription("Turn microphone on").performClick()
        compose.onNodeWithText("MIC ON").assertIsDisplayed()
    }
}
