package com.example.meowmba

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.car.TripStats
import com.example.meowmba.ui.companion.CompanionStatusHeader
import com.example.meowmba.ui.companion.CompanionTopOverlays
import com.example.meowmba.ui.companion.DriveCard
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DriveCardUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val unavailable = "Waze reports (unofficial): unavailable (HTTP 403). OSM mapped cameras: reports available. Coverage is incomplete."

    @Composable private fun Frame(fontScale: Float = 1f, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            MeowmbaTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().testTag("drive-viewport").padding(8.dp), contentAlignment = Alignment.TopCenter) { content() }
            }
        }
    }

    @Composable private fun Card(onDismiss: () -> Unit = {}, warning: String? = null) {
        DriveCard(55f, 1f, "GPS speed", false, TripStats(1L, 12.5, 600_000L, 45f, 60f),
            onDismiss, roadStatus = unavailable, obdLine = warning,
            driverCamera = { Text("Driver camera preview") })
    }

    @Test fun compactDefaultKeepsDiagnosticsAndPreviewBehindDetails() {
        var dismissals = 0
        rule.setContent { Frame { Card(onDismiss = { dismissals++ }) } }
        rule.onNodeWithText("55 mph").assertIsDisplayed()
        rule.onNodeWithText("Road data limited").assertIsDisplayed()
        rule.onNodeWithText(unavailable).assertDoesNotExist()
        rule.onNodeWithText("G-force").assertDoesNotExist()
        rule.onNodeWithText("Driver camera preview").assertDoesNotExist()
        rule.onNodeWithContentDescription("Dismiss driving display").performClick()
        assertEquals(1, dismissals)
        assertHudHeightAtMost(100)
    }

    @Test fun detailsRetainExactSourceTextAndReturnToCompactView() {
        var dismissals = 0
        rule.setContent { Frame { Card(onDismiss = { dismissals++ }) } }
        rule.onNodeWithTag("drive-details-action").performClick()
        rule.onNodeWithText(unavailable).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Driver camera preview").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Close drive details").performClick()
        rule.onNodeWithTag("drive-details-panel").assertDoesNotExist()
        rule.onNodeWithText("55 mph").assertIsDisplayed()
        assertEquals("Closing details must not dismiss drive mode", 0, dismissals)
    }

    @Test @Config(qualifiers = "w800dp-h360dp-land")
    fun landscapeHeaderAndSingleRowHudLeaveMostOfTheAvatarViewport() {
        rule.setContent { Frame {
            CompanionTopOverlays(header = {
                CompanionStatusHeader(true, "Reflex: idle hold · c37 s16")
            }) { Card() }
        } }
        val viewport = rule.onNodeWithTag("drive-viewport").fetchSemanticsNode().boundsInRoot
        val hud = rule.onNodeWithTag("drive-hud").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertHudHeightAtMost(80)
        assertTrue("Header and HUD leave over half the landscape viewport", hud.bottom - viewport.top < viewport.height * 0.5f)
        assertActionsDoNotOverlap()
    }

    @Test @Config(qualifiers = "w320dp-h720dp")
    fun largeTextOnNarrowPhoneKeepsAllActionsAndSpeedReadable() {
        rule.setContent { Frame(fontScale = 2f) {
            DriveCard(115f, 1f, "GPS speed", false, null, {}, roadStatus = unavailable)
        } }
        rule.onNodeWithText("115 mph").assertIsDisplayed()
        assertActionsDoNotOverlap()
        assertHudHeightAtMost(145)
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("drive-speed").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Actual speed digits and units must fit", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
    }

    @Test @Config(qualifiers = "w800dp-h360dp-land")
    fun landscapeLargeTextUsesOneRowAndDetailsCanScroll() {
        rule.setContent { Frame(fontScale = 2f) { Card() } }
        assertHudHeightAtMost(90)
        assertActionsDoNotOverlap()
        rule.onNodeWithTag("drive-details-action").performClick()
        rule.onNodeWithText(unavailable).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Close drive details").assertIsDisplayed().performClick()
    }

    @Test fun actualVehicleWarningRemainsVisibleWithoutOpeningDetails() {
        rule.setContent { Frame { Card(warning = "RPM 1500 · 115°C · ⚠ Engine coolant is hot") } }
        rule.onNodeWithText("⚠ Engine coolant is hot").assertIsDisplayed()
        rule.onNodeWithTag("drive-details-panel").assertDoesNotExist()
    }

    private fun assertHudHeightAtMost(dp: Int) {
        val height = rule.onNodeWithTag("drive-hud").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Collapsed HUD must stay compact", height <= with(rule.density) { dp.dp.toPx() })
    }

    private fun assertActionsDoNotOverlap() {
        val speed = rule.onNodeWithTag("drive-speed").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val details = rule.onNodeWithTag("drive-details-action").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dismiss = rule.onNodeWithTag("drive-dismiss-action").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val hud = rule.onNodeWithTag("drive-hud").fetchSemanticsNode().boundsInRoot
        assertTrue("Speed clears the details action", speed.right <= details.left)
        assertTrue("Actions do not overlap", details.right <= dismiss.left)
        assertTrue("Dismiss stays within the HUD", dismiss.right <= hud.right)
    }
}
