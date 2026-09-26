package com.example.meowmba

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.agent.runtime.*
import com.example.meowmba.core.agent.tools.ToolOutcome
import com.example.meowmba.ui.companion.TaskHistorySheet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskHistoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView ?: compose.activity.window.decorView
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val dir = java.io.File("build/task-history-shots").apply { mkdirs() }
            java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun recoveredOutcomeIsVisibleWithoutRetryOrApprovalControls() {
        val task = TaskHistory.recover(AgentTask(id = "task", agentId = "phone", toolId = "phone.compose_sms", title = "Message Jim", status = TaskStatus.RUNNING, executionStarted = true), 9)
        compose.setContent { MaterialTheme { TaskHistorySheet(listOf(task), "task", emptyList(), null, null, {}, {}, {}, { _, _ -> }, {}, {}) } }
        compose.onNodeWithText("Check outcome").assertExists()
        compose.onNodeWithText("Meow was interrupted", substring = true).assertExists()
        compose.onNodeWithText("Stop task").assertDoesNotExist()
        compose.onNodeWithText("Review approval").assertDoesNotExist()
        capture("recovered-result")
    }
    @Test @Config(qualifiers = "w320dp-h640dp") fun closeRemainsReachableWithLargeTextOnSmallPhone() {
        var closed = false
        RuntimeEnvironment.setFontScale(2f)
        try {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 2f)) {
                MaterialTheme { TaskHistorySheet(emptyList(), null, emptyList(), null, null, { closed = true }, {}, {}, { _, _ -> }, {}, {}) }
            }
        }
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        assertTrue(closed)
        capture("large-text")
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }

    @Test fun stoppedTaskDoesNotPromiseAFutureResult() {
        val task = AgentTask(id = "stopped", agentId = "phone", toolId = "phone.compose_sms", title = "Message", status = TaskStatus.CANCELLED, executionStarted = true)
        compose.setContent { MaterialTheme { TaskHistorySheet(listOf(task), "stopped", emptyList(), null, null, {}, {}, {}, { _, _ -> }, {}, {}) } }
        compose.onNodeWithText("No result was recorded.").assertExists()
        compose.onNodeWithText("The result will appear here", substring = true).assertDoesNotExist()
    }

    @Test fun taskListOpensTheExactResult() {
        val task = AgentTask(id = "task", agentId = "phone", toolId = "phone.take_photo", title = "Take a photo", status = TaskStatus.COMPLETED, outcome = ToolOutcome.HANDOFF)
        var opened: String? = null
        compose.setContent { MaterialTheme { TaskHistorySheet(listOf(task), null, emptyList(), null, null, {}, { opened = it }, {}, { _, _ -> }, {}, {}) } }
        compose.onNodeWithText("Continue in the opened app").assertExists()
        compose.onNodeWithTag("work_task").performClick()
        assertEquals("task", opened)
        capture("saved-work")
    }
}
