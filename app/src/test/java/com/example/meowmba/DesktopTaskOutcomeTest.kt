package com.example.meowmba

import com.example.meowmba.core.agent.tools.*
import com.example.meowmba.core.bridge.DesktopBridgeManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class DesktopTaskOutcomeTest {
    @Test fun uncertainAndHandoffReceiptsStopTheOuterWorkflowWithoutLosingOutcome() = runTest {
        for (receipt in listOf(ToolResult.Success("Check destination", ToolOutcome.UNCONFIRMED),
            ToolResult.Success("Continue in the app", ToolOutcome.HANDOFF),
            ToolResult.Failure("Timed out", ToolOutcome.UNCONFIRMED))) {
            val bridge = mock(DesktopBridgeManager::class.java)
            `when`(bridge.isPaired()).thenReturn(true)
            `when`(bridge.readUi()).thenReturn(Result.success(Json.parseToJsonElement(
                """{"app":"Notes","snapshot_id":"current","elements":[]}""").jsonObject))
            var plans = 0
            var actions = 0
            val tool = DesktopNavigationTool(bridge,
                { plans++; """{"action":"launch_app","text":"Notes"}""" },
                { _, _ -> actions++; receipt })
            val result = tool.execute(mapOf("goal" to "Open Notes"))
            val outcome = when (result) { is ToolResult.Success -> result.outcome; is ToolResult.Failure -> result.outcome }
            val expected = when (receipt) { is ToolResult.Success -> receipt.outcome; is ToolResult.Failure -> receipt.outcome }
            assertEquals(expected, outcome)
            assertEquals(1, plans); assertEquals(1, actions); assertFalse(tool.isActive)
        }
    }
}
