package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meowmba.core.robot.cozmo.CozmoController
import com.example.meowmba.ui.companion.CozmoScreen
import com.example.meowmba.ui.companion.RobotScreen
import com.example.meowmba.ui.companion.tileItems
import org.junit.Assert.assertEquals
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
    @Test fun `Cozmo has a standalone screen with no LOOI switch`() {
        var closed = false
        compose.setContent { MaterialTheme { CozmoScreen(controller = CozmoController(), onClose = { closed = true }) } }
        compose.onNodeWithText("Cozmo").assertIsDisplayed()
        compose.onNodeWithText("Connect").assertIsEnabled()
        compose.onNodeWithText("STOP").assertIsNotEnabled()
        compose.onNodeWithText("Forward").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Look up").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Calibrate head and lift").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Greet").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Curve left").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("LOOI", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Not connected").assertExists()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        assertTrue(closed)
    }
    @Test fun `connection failures are visible without enabling controls`() {
        val controller = CozmoController()
        compose.setContent { MaterialTheme { CozmoScreen(controller = controller, onClose = {}) } }
        compose.onNodeWithText("Connect").performClick()
        compose.waitUntil(5000) { controller.state.value.message.contains("Join Cozmo") }
        compose.onNodeWithText("Join Cozmo's Wi-Fi", substring = true).assertExists()
        compose.onNodeWithText("STOP").assertIsNotEnabled()
        compose.onNodeWithText("Forward").performScrollTo().assertIsNotEnabled()
    }
    @Test fun `LOOI has no Cozmo controls and retains its quarantine`() {
        compose.setContent { MaterialTheme { RobotScreen() } }
        compose.onNodeWithText("LOOI").assertIsDisplayed()
        compose.onNodeWithText("Cozmo", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Unavailable").assertIsNotEnabled()
        compose.onNodeWithText("Find base (scan)").performScrollTo().assertIsNotEnabled()
    }
    @Test fun `menu gives each robot its own entry`() {
        var names = emptyList<String>()
        compose.setContent { MaterialTheme { names = tileItems.map { it.name } } }
        compose.runOnIdle {
            assertEquals(1, names.count { it == "Cozmo" })
            assertEquals(1, names.count { it == "LOOI" })
            assertEquals(0, names.count { it == "Robot" })
        }
    }
}
