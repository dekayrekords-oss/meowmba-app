package com.example.meowmba

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.ui.companion.BusinessAgentsScreen
import com.example.meowmba.ui.theme.MeowmbaTheme
import com.example.meowmba.core.workflows.WorkflowRuntime
import com.example.meowmba.core.workflows.BusinessAgent
import com.example.meowmba.core.workflows.WorkflowEngine
import com.example.meowmba.core.workflows.WorkflowBrain
import com.example.meowmba.core.workflows.WorkDecision
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class BusinessAgentUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun failedSaveKeepsAgentEntriesVisibleAndAllowsRetry() {
        var closed = false
        var attempts = 0
        rule.setContent { MeowmbaTheme(darkTheme = false) {
            com.example.meowmba.ui.companion.AgentEditor(null, emptyList(), listOf("files.read"), onClose = { closed = true }) {
                attempts++
                if (attempts == 1) error("Synthetic storage failure")
            }
        } }
        rule.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Saved role")
        rule.onNode(hasSetTextAction() and hasText("Role and instructions")).performTextInput("Read assigned files")
        rule.onNodeWithText("Save agent").performClick()
        rule.onNodeWithText("Could not save: Synthetic storage failure. Your entries are still here.").assertIsDisplayed()
        rule.onNodeWithText("Saved role").assertExists()
        assertFalse(closed)
        rule.onNodeWithText("Save agent").performClick()
        rule.waitUntil { closed }
        assertEquals(2, attempts)
    }
    @Test fun lightThemeShowsExactApprovalAndDenialDoesNotWrite(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = WorkflowRuntime.get(context)
        val suffix = java.util.UUID.randomUUID().toString()
        val agent = BusinessAgent(name = "UI approval $suffix", instructions = "Write only after approval", tools = setOf("files.write"))
        runtime.store.saveAgent(agent)
        val id = runtime.store.enqueue(agent.id, "UI approval example $suffix")
        val path = "ui-test-$suffix/never-written.txt"
        try {
            WorkflowEngine(runtime.store, runtime.tools, WorkflowBrain { _, _, _, _, _ ->
                WorkDecision("tool", "files.write", mapOf("path" to path, "content" to "Synthetic approval example only"))
            }).tick(id)
            rule.setContent { MeowmbaTheme(darkTheme = false) { BusinessAgentsScreen {} } }
            rule.onNodeWithText("content: Synthetic approval example only").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("path: $path").assertExists()
            rule.onNodeWithText("Deny").performScrollTo().performClick()
            rule.waitUntil(10_000) { runtime.store.run(id)?.status == "BLOCKED" }
            assertFalse(File(runtime.workspace, path).exists())
            val out = File(context.getExternalFilesDir(null), "business-agent-ui-light.png")
            out.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            runtime.store.cancel(id)
            runtime.exportAndRemove(id)
            runtime.store.change { it.copy(agents = it.agents.filterNot { old -> old.id == agent.id }) }
        }
    }
    @Test fun userCanCreateLocalAgentAndReadItsCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = WorkflowRuntime.get(context)
        val name = "UI test ${java.util.UUID.randomUUID().toString().take(6)}"
        try {
            // Test-only activity, synthetic debug-app data. This displays its
            // own window above the lock screen; it does NOT unlock the phone,
            // change its credentials, or grant access to another application.
            rule.activity.runOnUiThread {
                rule.activity.setShowWhenLocked(true)
                rule.activity.setTurnScreenOn(true)
                rule.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            rule.setContent { MeowmbaTheme(darkTheme = true) { BusinessAgentsScreen {} } }
            rule.onNodeWithText("Create agent").performClick()
            rule.onNode(hasSetTextAction() and hasText("Name")).performTextInput(name)
            rule.onNode(hasSetTextAction() and hasText("Role and instructions")).performTextInput("Read business files and give an evidence-backed summary.")
            rule.onNodeWithText("Save agent").performClick()
            rule.waitUntil(10_000) { runtime.store.state.value.agents.any { it.name == name } }
            rule.onNodeWithText(name).performScrollTo().assertIsDisplayed()
            val agent = runtime.store.state.value.agents.single { it.name == name }
            assertEquals("local", agent.provider)
            assertFalse(agent.cloudConsent)
            assertEquals(setOf("files.list", "files.read"), agent.tools)
            val out = File(context.getExternalFilesDir(null), "business-agent-ui-dark.png")
            out.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            runtime.store.change { it.copy(agents = it.agents.filterNot { agent -> agent.name == name }) }
        }
    }
}
