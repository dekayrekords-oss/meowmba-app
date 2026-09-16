package com.example.meowmba

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.workflows.*
import com.example.meowmba.ui.companion.BusinessModeOverview
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BusinessModeOverviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun emptySetupExplainsMissingAccessAndBriefingOnlyInvokesConfiguration() {
        var configured = 0; var scheduleChanges = 0
        val snapshot = WorkflowSnapshot()
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            BusinessModeOverview(BusinessReadiness.from(snapshot), { configured++ }, { scheduleChanges++ })
        } } }
        compose.onNodeWithText("Service access: connect a service and grant its tools to an agent.").assertExists()
        compose.onNodeWithText("Proactive work: no schedules configured.").assertExists()
        compose.onNodeWithText("Configure daily briefing").performScrollTo().performClick()
        assertEquals(1, configured); assertEquals(0, scheduleChanges)
        assertTrue(snapshot.agents.isEmpty()); assertTrue(snapshot.runs.isEmpty())
        compose.onNodeWithText("Open business files").assertDoesNotExist()
    }

    @Test fun pauseAndReviewLinksUseTheirExplicitCallbacksOnly() {
        var paused: Boolean? = null; var files = 0; var bugs = 0
        val readiness = BusinessReadiness(2, 1, 2, 1, 1, false, 1, 1)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            BusinessModeOverview(readiness, {}, { paused = it }, { files++ }, { bugs++ })
        } } }
        compose.onNodeWithText("2 enabled agents · 1 active jobs · 1 awaiting approval").assertExists()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        assertEquals(true, paused)
        compose.onNodeWithText("Open business files").performScrollTo().performClick()
        compose.onNodeWithText("Review possible bug reports").performScrollTo().performClick()
        assertEquals(1, files); assertEquals(1, bugs)
    }
}
