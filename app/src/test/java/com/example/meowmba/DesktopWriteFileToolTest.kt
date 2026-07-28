package com.example.meowmba

import com.example.meowmba.core.agent.tools.ConfirmationPolicy
import com.example.meowmba.core.agent.tools.DesktopWriteFileTool
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
 * The desktop write tool itself: the daemon's write path is covered live by
 * the bridge smoke suite; here we pin the tool's policy contract and its
 * honest behavior when no desktop is paired.
 */
class DesktopWriteFileToolTest {

    private val mockSecureStorage = mock(SecureStorageManager::class.java)

    @Test
    fun `definition is approval-gated MODERATE and desktop-scoped`() {
        val def = DesktopWriteFileTool(DesktopBridgeManager(mockSecureStorage)).definition
        assertEquals("desktop.write_file", def.id)
        assertEquals(RiskLevel.MODERATE, def.riskLevel)
        assertEquals(ConfirmationPolicy.ALWAYS, def.confirmationPolicy)
        assertTrue(ToolEnvironment.DESKTOP in def.environments)
        assertTrue(def.auditLogged)
    }

    @Test
    fun `unpaired bridge fails honestly instead of writing`() = runTest {
        val tool = DesktopWriteFileTool(DesktopBridgeManager(mockSecureStorage))
        val result = tool.execute(mapOf("path" to "notes.txt", "content" to "hi"))
        assertTrue(result is ToolResult.Failure)
        assertTrue((result as ToolResult.Failure).error.contains("No paired desktop"))
    }

    @Test
    fun `missing args fail honestly`() = runTest {
        val tool = DesktopWriteFileTool(DesktopBridgeManager(mockSecureStorage))
        // Paired check runs first, so these only need to not crash and fail cleanly
        val noPath = tool.execute(mapOf("content" to "hi"))
        val noContent = tool.execute(mapOf("path" to "a.txt"))
        assertTrue(noPath is ToolResult.Failure)
        assertTrue(noContent is ToolResult.Failure)
    }
}
