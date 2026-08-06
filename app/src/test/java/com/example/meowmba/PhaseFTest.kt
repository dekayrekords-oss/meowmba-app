package com.example.meowmba

import com.example.meowmba.core.agent.tools.UiAutomationTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.agent.tools.RiskLevel
import com.example.meowmba.core.agent.tools.ConfirmationPolicy
import com.example.meowmba.core.access.MeowAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Phase F: UI automation is off by default and fails honestly. */
class PhaseFTest {

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `ui automation refuses everything while the service is disabled`() = runBlocking {
        assertFalse(MeowAccessibilityService.isEnabled())
        val tool = UiAutomationTool()
        for (action in listOf("tap", "type", "scroll", "read")) {
            val result = tool.execute(mapOf("action" to action, "text" to "ok"))
            assertTrue("expected failure for $action", result is ToolResult.Failure)
            assertTrue((result as ToolResult.Failure).error.contains("off", ignoreCase = true))
        }
    }

    @Test
    fun `ui automation uses loop-level approval (NONE per action)`() {
        val def = UiAutomationTool().definition
        assertEquals(RiskLevel.MODERATE, def.riskLevel)
        assertEquals(ConfirmationPolicy.NONE, def.confirmationPolicy)
    }
}
