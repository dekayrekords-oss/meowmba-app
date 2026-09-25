package com.example.meowmba

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.robot.cozmo.CozmoController
import com.example.meowmba.ui.companion.RobotScreen
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Emulator/device UI acceptance without opening a robot socket or requesting robot motion. */
class CozmoDeviceTest {
    @get:Rule val rule = createComposeRule()
    @Test fun cozmoControlsAreReachableAndDisconnectedMotionStaysDisabled() {
        val controller = CozmoController()
        rule.setContent { MeowmbaTheme { RobotScreen(cozmoController = controller) } }
        rule.onNodeWithText("Cozmo").assertIsDisplayed()
        rule.onNodeWithText("Connect").assertIsEnabled()
        rule.onNodeWithText("STOP").assertIsNotEnabled()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "cozmo-controls-174.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        rule.onNodeWithText("Forward").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Cozmo camera").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Cubes").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("LOOI · Bluetooth").performScrollTo().performClick()
        rule.onNodeWithText("Unavailable").assertIsNotEnabled()
        rule.onNodeWithText("Cozmo · Wi-Fi").performScrollTo().performClick()
        rule.onNodeWithText("Not connected").assertExists()
    }
}
