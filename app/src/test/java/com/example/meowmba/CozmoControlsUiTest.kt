package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.core.robot.cozmo.CozmoController
import com.example.meowmba.ui.companion.RobotScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CozmoControlsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `Cozmo is selectable while disconnected and LOOI remains quarantined`() {
        var closed = false
        compose.setContent { MaterialTheme { RobotScreen(cozmoController = CozmoController(), onClose = { closed = true }) } }
        compose.onNodeWithText("Cozmo").assertIsDisplayed()
        compose.onNodeWithText("Connect").assertIsEnabled()
        compose.onNodeWithText("STOP").assertIsNotEnabled()
        compose.onNodeWithText("Forward").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Look up").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("LOOI · Bluetooth").performScrollTo().performClick()
        compose.onNodeWithText("Unavailable").assertIsNotEnabled()
        compose.onNodeWithText("Cozmo · Wi-Fi").performScrollTo().performClick()
        compose.onNodeWithText("Not connected").assertExists()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        assertTrue(closed)
    }
    @Test fun `connection failures are visible without enabling controls`() {
        val controller = CozmoController()
        compose.setContent { MaterialTheme { RobotScreen(cozmoController = controller) } }
        compose.onNodeWithText("Connect").performClick()
        compose.waitUntil(5000) { controller.state.value.message.contains("Join Cozmo") }
        compose.onNodeWithText("Join Cozmo's Wi-Fi", substring = true).assertExists()
        compose.onNodeWithText("STOP").assertIsNotEnabled()
        compose.onNodeWithText("Forward").performScrollTo().assertIsNotEnabled()
    }
}
