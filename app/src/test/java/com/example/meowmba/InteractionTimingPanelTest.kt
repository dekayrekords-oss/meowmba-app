package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.meowmba.core.diagnostics.InteractionTimings
import com.example.meowmba.ui.companion.InteractionTimingPanel
import com.example.meowmba.ui.companion.interactionFrameComparison
import com.example.meowmba.ui.companion.interactionTimingSummary
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InteractionTimingPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `panel is off initially and starts nothing without a caller applying the switch`() {
        val recorder = InteractionTimings()
        var requested: Boolean? = null
        var resets = 0
        compose.setContent { MaterialTheme {
            InteractionTimingPanel(recorder.snapshot(), { requested = it }, { resets++ })
        } }
        compose.onNodeWithTag("interaction-timing-enabled").assertIsOff().performClick()
        compose.runOnIdle {
            assertEquals(true, requested)
            assertFalse(recorder.isEnabled)
            assertNull(recorder.snapshot().activeTurnId)
            assertEquals(0, resets)
        }
        compose.onNodeWithTag("interaction-timing-enabled").assertIsOff()
        compose.onNodeWithTag("interaction-timing-refresh").assertDoesNotExist()
    }

    @Test fun `refresh and reset are manual and missing callbacks have no numeric latency`() {
        var now = 0L
        val recorder = InteractionTimings({ now }).also { it.setEnabled(true) }
        val id = recorder.beginTurn()
        recorder.turnSubmitted(id)
        val view = mutableStateOf(recorder.snapshot())
        var refreshes = 0
        compose.setContent { MaterialTheme {
            InteractionTimingPanel(view.value,
                onEnabledChange = { recorder.setEnabled(it); view.value = recorder.snapshot() },
                onReset = { recorder.reset(); view.value = recorder.snapshot() },
                onRefresh = { refreshes++; view.value = recorder.snapshot() })
        } }
        compose.runOnIdle { now = 45_000_000L; recorder.firstSpeechOutput(id) }
        compose.onNodeWithText("In progress · 0.0 ms elapsed").assertExists()
        compose.onNodeWithTag("interaction-timing-refresh").performScrollTo().performClick()
        compose.onNodeWithText("1 sample · median 45.0 ms · p95 45.0 ms").assertExists()
        compose.onNodeWithText("Vsync unavailable · relative comparison unavailable").assertExists()
        compose.runOnIdle {
            assertEquals(1, refreshes)
            assertNull(view.value.summary(InteractionTimings.Metric.INTERRUPT_TO_TTS_STOP_ACK).medianMs)
        }
        compose.onNodeWithTag("interaction-timing-reset").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(recorder.isEnabled)
            assertTrue(view.value.metrics.all { it.summary.sampleCount == 0 })
            assertTrue(view.value.pending.isEmpty())
        }
        compose.onNodeWithTag("interaction-timing-enabled").performScrollTo().assertIsOn()
    }

    @Test fun `summary distinguishes absent measurements from an observed zero`() {
        assertEquals("No completed samples", interactionTimingSummary(InteractionTimings.Summary(0, 0, null, null)))
        assertEquals("1 sample · median 0.0 ms · p95 0.0 ms",
            interactionTimingSummary(InteractionTimings.Summary(1, 1, 0.0, 0.0)))
        assertEquals("No completed samples", interactionTimingSummary(InteractionTimings.Summary(1, 1, null, null)))
    }

    @Test fun `frame text labels known cadence without calling frame gaps GPU jank`() {
        val empty = InteractionTimings.Summary(0, 0, null, null)
        val frames = InteractionTimings.FrameSummary(empty, 10.0, InteractionTimings.CadenceSource.OBSERVED, 4, 1)
        assertEquals("100.0 Hz observed · 1/4 gaps above 1.5× interval", interactionFrameComparison(frames))
        assertEquals("100.0 Hz configured · no compared frames",
            interactionFrameComparison(frames.copy(cadenceSource = InteractionTimings.CadenceSource.CONFIGURED,
                comparedSamples = 0, gapsAboveCadence = null)))
        assertEquals("Vsync unavailable · relative comparison unavailable",
            interactionFrameComparison(frames.copy(expectedIntervalMs = null, cadenceSource = null)))
    }
}
