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
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.meowmba.ui.companion.RingModeSettings
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
    @Test fun modePickerAndSavedMeowRequestWorkOnSmallPhone() {
        val driver = AndroidSmartRing.get(rule.activity)
        driver.setCustomCommand("Single tap", "Show my next task")
        rule.setContent { MeowmbaTheme {
            val state by driver.state.collectAsState()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                RingModeSettings(state, driver)
            }
        } }
        rule.onNodeWithText("Edit Meow request for Single tap").performScrollTo().performClick()
        rule.onNodeWithText("Saved request").performTextReplacement("Open my project planner")
        rule.onNodeWithText("Save and pause").performClick()
        rule.runOnIdle { org.junit.Assert.assertEquals("Open my project planner", driver.state.value.customCommands["Single tap"]); org.junit.Assert.assertTrue(driver.state.value.paused) }
        rule.onNodeWithText("Mode: Custom commands").performScrollTo().performClick()
        rule.onNodeWithText("Media control").assertIsDisplayed()
        rule.onNodeWithText("Scrolling and navigation").assertIsDisplayed()
        rule.onNodeWithText("Photo taking").assertIsDisplayed()
        rule.waitForIdle()
        rule.onNodeWithText("Photo taking").performClick()
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val file = java.io.File("build/reports/smart-ring/modes-320.png"); file.parentFile?.mkdirs()
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        }
        rule.onNodeWithText("Mode: Photo taking").performClick()
        rule.onNodeWithText("Custom commands", useUnmergedTree = true).performClick()
        rule.runOnIdle { org.junit.Assert.assertEquals(RingAction.MEOW_COMMAND, driver.state.value.mappings["Single tap"]) }
    }
    @Test fun disclosureRequiresAgreementAndWithdrawalStopsControls() {
        val driver = AndroidSmartRing.get(rule.activity)
        driver.setControlsConsent(false)
        rule.setContent { MeowmbaTheme { SmartRingScene(onBack = {}) } }
        rule.onNodeWithContentDescription("Pause ring controls").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Open Accessibility settings").performScrollTo().performClick()
        rule.onNodeWithText("Enable ring controls").assertIsDisplayed()
        rule.waitForIdle()
        rule.runOnIdle {
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
            val disclosureImage = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(disclosureImage))
            val file = java.io.File("build/reports/smart-ring/disclosure-320.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { disclosureImage.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            disclosureImage.recycle()
        }
        rule.onNodeWithText("Not now").performClick()
        org.junit.Assert.assertFalse(driver.state.value.controlsConsent)
        driver.setPaused(false); driver.setTesting(true)
        org.junit.Assert.assertTrue(driver.state.value.paused)
        org.junit.Assert.assertFalse(driver.state.value.testing)
        rule.onNodeWithText("Open Accessibility settings").performScrollTo().performClick()
        rule.onNodeWithText("Agree and continue").performClick()
        org.junit.Assert.assertTrue(driver.state.value.controlsConsent)
        org.junit.Assert.assertEquals(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
            org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication()).nextStartedActivity.action)
        rule.onNodeWithText("Withdraw ring controls consent").performScrollTo().performClick()
        org.junit.Assert.assertFalse(driver.state.value.controlsConsent)
        org.junit.Assert.assertTrue(driver.state.value.paused)
    }
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
        rule.onNodeWithText("Open Accessibility settings").performScrollTo().performClick()
        rule.onNodeWithText("Agree and continue").performClick()
        rule.onNodeWithText("Pause ring controls").performScrollTo().assertIsDisplayed()
        rule.onAllNodes(isToggleable())[0].assertIsOn()
        rule.onNodeWithText("Sleep after: 30 seconds").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Sleep after inactivity").assertIsOn()
        rule.onNodeWithContentDescription("Idle sleep timeout").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(60f) }
        rule.onNodeWithText("Sleep after: 60 seconds").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Idle sleep timeout").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(30f) }
        rule.onNodeWithText("Sleep now").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Sleep mode").performScrollTo()
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/reports/smart-ring/sleep-controls-320.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        rule.onNodeWithText("Reset gesture verification").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Allow all apps").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Allow all apps").performScrollTo().assertIsOff().performClick().assertIsOn()
        org.junit.Assert.assertTrue(AndroidSmartRing.get(rule.activity).state.value.allApps)
        rule.onNodeWithText("Use scroll & click controls").performScrollTo().performClick()
        rule.onNodeWithText("Single tap: Click target").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("1 tap + Next: Move target right").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("1 tap + Previous: Move target left").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("2 taps + Next: Move target down").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Show click target").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Center click target").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Allowed foreground apps").performScrollTo()
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/reports/smart-ring/click-controls-320.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        rule.onNodeWithText("Allow all apps").performScrollTo()
        rule.onNodeWithContentDescription("Allow all apps").performScrollTo().performClick().assertIsOff()
        rule.onNodeWithText("Choose allowed apps").performScrollTo().performClick()
        rule.onNodeWithText("Allowed apps").assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Allowed app package IDs").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Share diagnostic log").performScrollTo().assertIsDisplayed()
    }
}
