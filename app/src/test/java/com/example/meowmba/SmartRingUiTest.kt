package com.example.meowmba

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.ui.companion.SmartRingScene
import com.example.meowmba.ui.companion.RingTestPanel
import com.example.meowmba.core.ring.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h720dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmartRingUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun testerPreviewsDoNotSendCommandsAndSensorsShowRealAvailability() {
        var requests = 0
        val state = RingState(testing = true, lastInput = "Double tap", gestureCounts = mapOf("Double tap" to 2),
            sensors = RingSensor.entries.associateWith { if (it == RingSensor.HEART_RATE)
                RingReading("74 bpm", "Received from ring", 1234) else RingReading() })
        rule.setContent { MeowmbaTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                RingTestPanel(state, { requests++ }, {}, {})
            }
        } }
        rule.onNodeWithText("2 seen").assertExists()
        rule.onNodeWithText("↓ Next").performClick()
        rule.onNodeWithText("Preview: Next").assertIsDisplayed()
        rule.onNodeWithText("74 bpm").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Test Heart rate").assertIsNotEnabled()
        rule.onNodeWithText("Connect the ring to enable sensor tests.").performScrollTo().assertIsDisplayed()
        assert(requests == 0)
        rule.onNodeWithText("Live ring tester").performScrollTo()
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/reports/smart-ring/tester-320.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun setupAndSafetyControlsRemainReachableOnSmallPhone() {
        rule.setContent { MeowmbaTheme { SmartRingScene(onBack = {}) } }
        rule.onNodeWithText("Scan / reconnect").assertIsDisplayed()
        rule.onNodeWithText("Disconnect").assertIsDisplayed()
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/reports/smart-ring/setup-320.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        rule.onNodeWithText("Pause ring controls").performScrollTo().assertIsDisplayed()
        rule.onAllNodes(isToggleable())[0].assertIsOn()
        rule.onNodeWithText("Reset gesture verification").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Choose allowed apps").performScrollTo().performClick()
        rule.onNodeWithText("Allowed apps").assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Allowed app package IDs").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Share diagnostic log").performScrollTo().assertIsDisplayed()
    }
}
