package com.example.meowmba

import com.example.meowmba.core.agent.tools.ConfirmationPolicy
import com.example.meowmba.core.agent.tools.DesktopRunBuildTool
import com.example.meowmba.core.agent.tools.RiskLevel
import com.example.meowmba.core.agent.tools.ToolEnvironment
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.bridge.DesktopBridgeManager
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * desktop.run_build policy contract + honest unpaired behavior.
 * The daemon's execution path (fixed argv only, cwd confinement, timeouts)
 * is covered live by the bridge smoke suite.
 */
class DesktopRunBuildToolTest {

    private val mockSecureStorage = mock(SecureStorageManager::class.java)

    @Test
    fun `definition is approval-gated DESTRUCTIVE and desktop-scoped`() {
        val def = DesktopRunBuildTool(DesktopBridgeManager(mockSecureStorage)).definition
        assertEquals("desktop.run_build", def.id)
        assertEquals(RiskLevel.DESTRUCTIVE, def.riskLevel)
        assertEquals(ConfirmationPolicy.ALWAYS, def.confirmationPolicy)
        assertTrue(ToolEnvironment.DESKTOP in def.environments)
        assertTrue(def.auditLogged)
    }

    @Test
    fun `unpaired bridge fails honestly instead of running`() = runTest {
        val tool = DesktopRunBuildTool(DesktopBridgeManager(mockSecureStorage))
        val result = tool.execute(mapOf("name" to "assemble"))
        assertTrue(result is ToolResult.Failure)
        assertTrue((result as ToolResult.Failure).error.contains("No paired desktop"))
    }

    @Test
    fun `missing name fails honestly`() = runTest {
        val tool = DesktopRunBuildTool(DesktopBridgeManager(mockSecureStorage))
        assertTrue(tool.execute(emptyMap()) is ToolResult.Failure)
    }
}
