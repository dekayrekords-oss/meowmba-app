package com.example.meowmba

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.vision.FaceTrackingStatus
import com.example.meowmba.ui.companion.CameraTrackingControl
import com.example.meowmba.ui.theme.MeowmbaTheme
import com.example.meowmba.ui.theme.Surface
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Display and explicit actions are separate from the user's saved camera preference. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraTrackingControlUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Composable private fun Frame(fontScale: Float? = null, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
            MeowmbaTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    Box(Modifier.fillMaxSize().testTag("camera-control-viewport"), contentAlignment = Alignment.BottomEnd) { content() }
                }
            }
        }
    }

    @Test fun cameraOffOverridesStaleTrackingOrErrorsAndNeverOffersRetry() {
        var status by mutableStateOf(FaceTrackingStatus.TRACKING)
        var retries = 0
        rule.setContent { Frame { CameraTrackingControl(false, status, {}, { retries++ }) } }
        for (observed in FaceTrackingStatus.entries) {
            rule.runOnIdle { status = observed }
            rule.onNodeWithText("Camera off").assertIsDisplayed()
            rule.onNodeWithContentDescription("Turn camera on").assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Camera off"))
            rule.onNodeWithText("Retry").assertDoesNotExist()
            rule.onNodeWithContentDescription("Turn camera off").assertDoesNotExist()
        }
        assertEquals(0, retries)
    }

    @Test fun enabledCameraReportsObservedTrackingFaceSearchAndCooling() {
        var status by mutableStateOf(FaceTrackingStatus.TRACKING)
        rule.setContent { Frame { CameraTrackingControl(true, status, {}, {}) } }
        for (observed in listOf(FaceTrackingStatus.TRACKING, FaceTrackingStatus.NO_FACE,
            FaceTrackingStatus.COOLING, FaceTrackingStatus.STARTING, FaceTrackingStatus.WAITING_FOR_FRAMES,
            FaceTrackingStatus.PERMISSION_REQUIRED, FaceTrackingStatus.PAUSED)) {
            rule.runOnIdle { status = observed }
            rule.onNodeWithTag("camera-tracking-status").assertTextEquals(observed.label).assertIsDisplayed()
            rule.onNodeWithContentDescription("Turn camera off")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, observed.label))
            rule.onNodeWithText("Retry").assertDoesNotExist()
        }
    }

    @Test fun retryOnlyCallsRecoveryAndDoesNotToggleTheSavedCameraChoice() {
        var retries = 0
        var toggles = 0
        var enabled by mutableStateOf(true)
        rule.setContent { Frame {
            CameraTrackingControl(enabled, FaceTrackingStatus.ERROR,
                { toggles++; enabled = !enabled }, { retries++ })
        } }
        assertEquals(0, retries)
        rule.onNodeWithText("Retry").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, retries)
        assertEquals(0, toggles)
        assertTrue(enabled)
        rule.onNodeWithContentDescription("Turn camera off").assertIsDisplayed()
    }

    @Test fun cameraButtonChangesOnlyTheExplicitToggleCallback() {
        var enabled by mutableStateOf(false)
        var toggles = 0
        var retries = 0
        rule.setContent { Frame {
            CameraTrackingControl(enabled, FaceTrackingStatus.TRACKING,
                { toggles++; enabled = !enabled }, { retries++ })
        } }
        rule.onNodeWithContentDescription("Turn camera on").performClick()
        assertEquals(1, toggles)
        assertTrue(enabled)
        rule.onNodeWithText("Following your face").assertIsDisplayed()
        rule.onNodeWithContentDescription("Turn camera off").performClick()
        assertEquals(2, toggles)
        assertFalse(enabled)
        rule.onNodeWithText("Camera off").assertIsDisplayed()
        assertEquals(0, retries)
    }

    @Test @Config(qualifiers = "w320dp-h720dp")
    fun compactErrorWithLargeTextKeepsStatusRetryAndToggleVisible() {
        rule.setContent { Frame(fontScale = 1.4f) {
            CameraTrackingControl(true, FaceTrackingStatus.ERROR, {}, {},
                Modifier.padding(bottom = 72.dp, end = 16.dp))
        } }
        val viewport = rule.onNodeWithTag("camera-control-viewport").fetchSemanticsNode().boundsInRoot
        val status = rule.onNodeWithTag("camera-tracking-status").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val retry = rule.onNodeWithText("Retry").assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
        val toggle = rule.onNodeWithContentDescription("Turn camera off").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText("Tracking unavailable").assertIsDisplayed()
        assertTrue("Status remains inside the compact screen", status.left >= viewport.left && status.right <= viewport.right)
        assertTrue("Retry does not overlap the status", retry.left >= status.right)
        assertTrue("Camera action does not overlap Retry", toggle.left >= retry.right)
        assertTrue("Camera action remains inside the compact screen", toggle.right <= viewport.right)
        render("camera-error-320-large-text")
    }

    private fun render(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/reports/server118/$name.png").apply { parentFile.mkdirs() }.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
