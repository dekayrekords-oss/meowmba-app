package com.example.meowmba

import com.example.meowmba.core.agent.PhoneAgentLoop
import com.example.meowmba.core.agent.runtime.*
import com.example.meowmba.core.agent.tools.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AgentReliabilityTest {
    @Test fun `back to back submissions reserve concurrency slots before dispatch`() = runTest {
        val registry = ToolRegistry()
        registry.register(object : Tool {
            override val definition = ToolDefinition("file.read", "Read", "test", riskLevel = RiskLevel.SAFE)
            override suspend fun execute(args: Map<String, String>): ToolResult {
                delay(100)
                return ToolResult.Success("read")
            }
        })
        val orchestrator = AgentOrchestrator(this, registry, object : AuditSink {
            override suspend fun log(action: String, agentId: String, taskId: String?, detail: String) {}
        })
        repeat(3) { orchestrator.submit("file", "file.read", emptyMap(), "Read $it") }
        assertEquals(2, orchestrator.tasks.value.count { it.status == TaskStatus.RUNNING })
        assertEquals(1, orchestrator.tasks.value.count { it.status == TaskStatus.QUEUED })
        advanceUntilIdle()
        assertEquals(3, orchestrator.tasks.value.count { it.status == TaskStatus.COMPLETED })
    }

    @Test fun `desktop control has an authorized owner`() {
        assertEquals("computer", AgentRegistry.ownerOf("desktop.control")?.id)
    }

    @Test fun `unavailable accessibility clears both phone control flags`() = runTest {
        val loop = PhoneAgentLoop(this, { null }, { null }, {}, {})
        loop.setGoal("read screen")
        advanceUntilIdle()
        assertFalse(loop.isActive)
        assertNull(PhoneAgentLoop.activeGoal)
    }
}
