package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.ui.companion.WorkflowStartersDialog
import com.example.meowmba.core.workflows.BusinessAgent
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkflowStartersUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun searchProducesAConfigurableDraftOnlyRecipe() {
        var chosen: BusinessAgent? = null
        compose.setContent { MaterialTheme { WorkflowStartersDialog(onClose = {}, onChoose = { chosen = it }) } }
        compose.onNodeWithText("30 workflow starters").assertExists()
        compose.onNodeWithText("Search workflows").performTextInput("new-user")
        compose.onNodeWithText("New-user welcome drafts").assertExists()
        compose.onNodeWithText("Configure this workflow").performScrollTo().performClick()
        assertNotNull(chosen)
        assertTrue(chosen!!.tools.contains("drafts.prepare"))
        assertFalse(chosen!!.tools.any { it.startsWith("mcp.") })
        assertEquals(0L, chosen!!.intervalMinutes)
    }
}
