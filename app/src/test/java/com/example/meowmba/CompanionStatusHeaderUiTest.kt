package com.example.meowmba

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.meowmba.ui.companion.CompanionStatusHeader
import com.example.meowmba.ui.companion.CompanionTopOverlays
import com.example.meowmba.ui.companion.CrashAlertCard
import com.example.meowmba.ui.companion.DriveCard
import com.example.meowmba.ui.companion.WeatherCard
import com.example.meowmba.core.weather.WeatherCondition
import com.example.meowmba.core.weather.WeatherNow
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompanionStatusHeaderUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Composable private fun Frame(fontScale: Float = 1f, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            MeowmbaTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
                    content()
                }
            }
        }
    }

    @Test fun reflexBadgeAndClockDoNotOverlap() {
        rule.setContent { Frame {
            CompanionStatusHeader(showClock = true, presenceHint = "Reflex: idle hold · c37 s16")
        } }
        assertClockBelowHint()
    }

    @Test @Config(qualifiers = "w320dp-h720dp")
    fun enlargedTwoLineHintKeepsClockAndStopActionSeparate() {
        var stops = 0
        rule.setContent { Frame(fontScale = 2f) {
            CompanionStatusHeader(
                showClock = true,
                presenceHint = "Reflex: orient social · curiosity 37 · social attention 16",
                onStopComputer = { stops++ },
            )
        } }
        assertClockBelowHint()
        val clock = rule.onNodeWithTag("companion-wall-clock").fetchSemanticsNode().boundsInRoot
        val stop = rule.onNodeWithText("Stop Computer").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Stop stays below the enlarged clock", stop.top >= clock.bottom)
        rule.onNodeWithText("Stop Computer").performClick()
        assertEquals(1, stops)
    }

    @Test fun hidingFeedbackReturnsClockToItsOriginalTopSlot() {
        var hint by mutableStateOf<String?>("Reflex: idle hold · c37 s16")
        rule.setContent { Frame { CompanionStatusHeader(showClock = true, presenceHint = hint) } }
        val withHint = rule.onNodeWithTag("companion-wall-clock").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { hint = null }
        val withoutHint = rule.onNodeWithTag("companion-wall-clock").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Hidden feedback leaves no reserved badge space", withoutHint.top < withHint.top)
        rule.runOnIdle { hint = "Reflex: explore · c83 s10" }
        assertClockBelowHint()
    }

    @Test fun agentReviewAndDismissRemainAvailableWithoutClock() {
        var reviews = 0
        var dismissals = 0
        rule.setContent { Frame {
            CompanionStatusHeader(
                showClock = false,
                presenceHint = "Task needs review",
                onReview = { reviews++ },
                onDismiss = { dismissals++ },
            )
        } }
        rule.onNodeWithTag("companion-wall-clock").assertDoesNotExist()
        rule.onNodeWithText("Review").assertIsDisplayed().performClick()
        rule.onNodeWithText("Dismiss").assertIsDisplayed().performClick()
        assertEquals(1, reviews)
        assertEquals(1, dismissals)
    }

    @Test @Config(qualifiers = "w360dp-h1000dp")
    fun weatherAndDriveFollowTheMeasuredEnlargedHeaderAndEachOther() {
        var hint by mutableStateOf("Reflex: idle hold · c37 s16")
        rule.setContent { Frame(fontScale = 1.5f) {
            CompanionTopOverlays(header = {
                CompanionStatusHeader(showClock = true, presenceHint = hint)
            }) {
                WeatherCard(
                    weather = WeatherNow(WeatherCondition.CLEAR, 80, 80, 85, 70, 30, 4, true, "Test location"),
                    onDismiss = {},
                    modifier = Modifier.testTag("weather-card"),
                )
                DriveCard(
                    speedMph = 0f, gForce = 1f, reason = "test", manual = true,
                    trip = null, onDismiss = {}, modifier = Modifier.testTag("drive-card"),
                )
            }
        } }
        for (label in listOf("Reflex: idle hold · c37 s16", "Reflex: orient social · curiosity 37 · social attention 16")) {
            rule.runOnIdle { hint = label }
            assertClockBelowHint()
            val clock = rule.onNodeWithTag("companion-wall-clock").fetchSemanticsNode().boundsInRoot
            val weather = rule.onNodeWithTag("weather-card").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val drive = rule.onNodeWithTag("drive-card").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Weather clears the current header height", weather.top > clock.bottom)
            assertTrue("Drive clears the current weather height", drive.top > weather.bottom)
        }
    }

    @Test @Config(qualifiers = "w320dp-h720dp")
    fun crashAlertClearsTheEnlargedHeaderAndKeepsItsDismissAction() {
        var dismissals = 0
        rule.setContent { Frame(fontScale = 1.5f) {
            CompanionTopOverlays(header = {
                CompanionStatusHeader(showClock = true, presenceHint = "Reflex: orient social · curiosity 37")
            }) {
                CrashAlertCard(
                    message = "A strong impact was detected.", onTextContact = {},
                    onDismiss = { dismissals++ }, modifier = Modifier.testTag("crash-card"),
                )
            }
        } }
        assertClockBelowHint()
        val clock = rule.onNodeWithTag("companion-wall-clock").fetchSemanticsNode().boundsInRoot
        val crash = rule.onNodeWithTag("crash-card").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Crash alert clears the current header height", crash.top > clock.bottom)
        rule.onNodeWithText("I'm OK").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, dismissals)
    }

    @Test fun incomingCrashAlertResetsOldCardScrollAndLeavesClockVisible() {
        var crash by mutableStateOf(false)
        rule.setContent { Frame {
            CompanionTopOverlays(priorityRevision = crash, header = {
                CompanionStatusHeader(showClock = true, presenceHint = "Reflex: idle hold · c37 s16")
            }) {
                if (crash) {
                    CrashAlertCard("A strong impact was detected.", {}, {})
                } else {
                    Spacer(Modifier.height(1000.dp))
                    Text("Old details")
                }
            }
        } }
        val clockTop = rule.onNodeWithTag("companion-wall-clock").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithText("Old details").performScrollTo().assertIsDisplayed()
        assertEquals(clockTop, rule.onNodeWithTag("companion-wall-clock")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top, 0.1f)
        rule.runOnIdle { crash = true }
        rule.onNodeWithText("Possible crash").assertIsDisplayed()
        rule.onNodeWithText("I'm OK").assertIsDisplayed()
        rule.onNodeWithText("Old details").assertDoesNotExist()
        assertClockBelowHint()
    }

    private fun assertClockBelowHint() {
        val hint = rule.onNodeWithTag("companion-presence-hint").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val clock = rule.onNodeWithTag("companion-wall-clock").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Clock stays below the measured presence badge", clock.top >= hint.bottom)
    }
}
