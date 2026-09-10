package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.ui.companion.RobotScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RobotControlsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `disable and disconnect remain actionable while connecting`() {
        var stops = 0
        var disabled = false
        compose.setContent { MaterialTheme {
            RobotScreen(robotStatus = "Connecting to robot base…", robotBodyEnabled = true,
                onStop = { stops++ }, onRobotBodyChange = { disabled = !it })
        } }
        compose.onNodeWithText("Disable control").performScrollTo().performClick()
        compose.onNodeWithText("Disconnect").performScrollTo().performClick()
        assertEquals(1, stops)
        assertTrue(disabled)
        compose.onNodeWithText("Disconnect sends no motor command", substring = true).assertExists()
    }

    @Test fun `a status containing the word body cannot fake a connected control`() {
        var connects = 0
        compose.setContent { MaterialTheme {
            RobotScreen(robotStatus = "Searching for body", robotConnected = false, onRefresh = { connects++ })
        } }
        compose.onNodeWithText("Unavailable").assertIsNotEnabled()
        assertEquals(0, connects)
        compose.onNodeWithText("Connected", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `saved enabled state cannot enable connection or scanning`() {
        compose.setContent { MaterialTheme {
            RobotScreen(robotBodyEnabled = true, robotConnected = true)
        } }
        compose.onNodeWithText("Unavailable").assertIsNotEnabled()
        compose.onNodeWithText("Find base (scan)").performScrollTo().assertIsNotEnabled()
        compose.onAllNodes(isToggleable()).assertCountEquals(2)
        compose.onAllNodes(isToggleable())[0].assertIsOff().assertIsNotEnabled()
        compose.onAllNodes(isToggleable())[1].assertIsNotEnabled()
        compose.onNodeWithText("Connected").assertDoesNotExist()
    }
}
