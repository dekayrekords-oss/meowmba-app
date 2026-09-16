package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentOrchestrator
import com.example.meowmba.core.agent.runtime.AgentRegistry
import com.example.meowmba.core.agent.runtime.AuditSink
import com.example.meowmba.core.agent.runtime.SubmitResult
import com.example.meowmba.core.agent.runtime.TaskStatus
import com.example.meowmba.core.agent.tools.ConfirmationPolicy
import com.example.meowmba.core.agent.tools.RiskLevel
import com.example.meowmba.core.agent.tools.Tool
import com.example.meowmba.core.agent.tools.ToolDefinition
import com.example.meowmba.core.agent.tools.ToolRegistry
import com.example.meowmba.core.agent.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentCancellationLifecycleTest {
    private class Audit : AuditSink {
        val events = mutableListOf<Pair<String, String?>>()
        override suspend fun log(action: String, agentId: String, taskId: String?, detail: String) {
            events += action to taskId
        }
        fun count(action: String, taskId: String) = events.count { it == (action to taskId) }
    }

    private fun tool(
        approval: Boolean = false,
        timeoutMs: Long = 30_000,
        block: suspend (Map<String, String>) -> ToolResult,
    ) = object : Tool {
        override val definition = ToolDefinition("avatar.form", "Change avatar", "test", riskLevel = RiskLevel.SAFE,
            confirmationPolicy = if (approval) ConfirmationPolicy.ALWAYS else ConfirmationPolicy.NONE,
            timeoutMs = timeoutMs)
        override suspend fun execute(args: Map<String, String>) = block(args)
    }

    private fun AgentOrchestrator.submit(name: String) =
        submit(AgentRegistry.MAIN, "avatar.form", mapOf("name" to name), name) as SubmitResult.Started

    @Test fun selfCancelledToolBecomesTerminalAndFreesItsSlotForQueuedWork() = runTest {
        val cancelGate = CompletableDeferred<Unit>()
        val holdGate = CompletableDeferred<Unit>()
        val executions = mutableListOf<String>()
        val registry = ToolRegistry().apply { register(tool { args ->
            val name = args.getValue("name")
            executions += name
            when (name) {
                "cancel" -> { cancelGate.await(); throw CancellationException("New form superseded this one") }
                "hold" -> holdGate.await()
            }
            ToolResult.Success(name)
        }) }
        val audit = Audit()
        val orchestrator = AgentOrchestrator(this, registry, audit)
        val completions = mutableListOf<String>()
        orchestrator.onTaskCompleted = { completions += it.id }
        val cancelled = orchestrator.submit("cancel")
        val holding = orchestrator.submit("hold")
        val queued = orchestrator.submit("next")
        assertTrue(queued.queued)
        runCurrent()
        assertEquals(listOf("cancel", "hold"), executions)
        cancelGate.complete(Unit)
        runCurrent()
        assertEquals(TaskStatus.CANCELLED, orchestrator.tasks.value.first { it.id == cancelled.taskId }.status)
        assertEquals(TaskStatus.RUNNING, orchestrator.tasks.value.first { it.id == holding.taskId }.status)
        assertEquals(TaskStatus.COMPLETED, orchestrator.tasks.value.first { it.id == queued.taskId }.status)
        assertEquals(listOf("cancel", "hold", "next"), executions)
        assertEquals(1, audit.count("task.cancelled", cancelled.taskId))
        assertFalse(cancelled.taskId in completions)
        holdGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test fun explicitCancelIsAuditedOnceAndLateToolResultCannotResurrectTask() = runTest {
        val lateResult = CompletableDeferred<Unit>()
        val holdGate = CompletableDeferred<Unit>()
        val registry = ToolRegistry().apply { register(tool { args ->
            when (args.getValue("name")) {
                "cancel" -> try { delay(10_000) } catch (_: CancellationException) {
                    // Models/native calls can finish after a stop request. The
                    // task must keep its terminal cancellation when that occurs.
                    withContext(NonCancellable) { lateResult.await() }
                }
                "hold" -> holdGate.await()
            }
            ToolResult.Success("late result")
        }) }
        val audit = Audit()
        val orchestrator = AgentOrchestrator(this, registry, audit)
        val completions = mutableListOf<String>()
        orchestrator.onTaskCompleted = { completions += it.id }
        val cancelled = orchestrator.submit("cancel")
        orchestrator.submit("hold")
        val queued = orchestrator.submit("next")
        runCurrent()
        orchestrator.cancel(cancelled.taskId)
        orchestrator.cancel(cancelled.taskId)
        runCurrent()
        assertEquals(TaskStatus.CANCELLED, orchestrator.tasks.value.first { it.id == cancelled.taskId }.status)
        assertEquals(TaskStatus.COMPLETED, orchestrator.tasks.value.first { it.id == queued.taskId }.status)
        lateResult.complete(Unit)
        holdGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(TaskStatus.CANCELLED, orchestrator.tasks.value.first { it.id == cancelled.taskId }.status)
        assertEquals(1, audit.count("task.cancelled", cancelled.taskId))
        assertEquals(0, audit.count("task.complete", cancelled.taskId))
        assertEquals(0, audit.count("task.fail", cancelled.taskId))
        assertFalse(cancelled.taskId in completions)
    }

    @Test fun ordinaryToolTimeoutStillFailsInsteadOfBecomingCancelled() = runTest {
        val registry = ToolRegistry().apply { register(tool(timeoutMs = 50) {
            delay(100)
            ToolResult.Success("too late")
        }) }
        val audit = Audit()
        val orchestrator = AgentOrchestrator(this, registry, audit)
        val task = orchestrator.submit("timeout")
        advanceUntilIdle()
        val final = orchestrator.tasks.value.single()
        assertEquals(TaskStatus.FAILED, final.status)
        assertEquals("Tool timed out after 50ms", final.result)
        assertEquals(1, audit.count("task.fail", task.taskId))
        assertEquals(0, audit.count("task.cancelled", task.taskId))
    }

    @Test fun explicitCancellationRemovesApprovalAndAdvancesTheNextRequest() = runTest {
        val executions = mutableListOf<String>()
        val registry = ToolRegistry().apply { register(tool(approval = true) { args ->
            executions += args.getValue("name")
            ToolResult.Success("approved")
        }) }
        val audit = Audit()
        val orchestrator = AgentOrchestrator(this, registry, audit)
        val first = orchestrator.submit("first")
        val second = orchestrator.submit("second")
        val third = orchestrator.submit("third")
        runCurrent()
        assertEquals(first.taskId, orchestrator.pendingApproval.value?.taskId)
        orchestrator.cancel(first.taskId)
        runCurrent()
        assertEquals(second.taskId, orchestrator.pendingApproval.value?.taskId)
        assertEquals(TaskStatus.WAITING_APPROVAL, orchestrator.tasks.value.first { it.id == third.taskId }.status)
        orchestrator.resolveApproval(true)
        runCurrent()
        assertEquals(third.taskId, orchestrator.pendingApproval.value?.taskId)
        orchestrator.resolveApproval(true)
        advanceUntilIdle()
        assertNull(orchestrator.pendingApproval.value)
        assertEquals(listOf("second", "third"), executions)
        assertEquals(TaskStatus.CANCELLED, orchestrator.tasks.value.first { it.id == first.taskId }.status)
        assertEquals(1, audit.count("task.cancelled", first.taskId))
        assertEquals(0, audit.count("task.fail", first.taskId))
    }

    @Test fun parentShutdownClearsActiveApprovalsWithoutLaunchingQueuedWork() = runTest {
        val owner = SupervisorJob()
        val taskScope = CoroutineScope(StandardTestDispatcher(testScheduler) + owner)
        var executions = 0
        val registry = ToolRegistry().apply { register(tool(approval = true) {
            executions++
            ToolResult.Success("approved")
        }) }
        val orchestrator = AgentOrchestrator(taskScope, registry, Audit())
        val first = orchestrator.submit("first")
        val second = orchestrator.submit("second")
        val queued = orchestrator.submit("queued")
        runCurrent()
        assertNotNull(orchestrator.pendingApproval.value)
        owner.cancel()
        runCurrent()
        assertNull(orchestrator.pendingApproval.value)
        assertEquals(TaskStatus.CANCELLED, orchestrator.tasks.value.first { it.id == first.taskId }.status)
        assertEquals(TaskStatus.CANCELLED, orchestrator.tasks.value.first { it.id == second.taskId }.status)
        assertEquals(TaskStatus.QUEUED, orchestrator.tasks.value.first { it.id == queued.taskId }.status)
        assertEquals(0, executions)
    }

    @Test fun cancellationBeforeDispatchDoesNotLeaveReservedSlotRunning() = runTest {
        val owner = SupervisorJob()
        val taskScope = CoroutineScope(StandardTestDispatcher(testScheduler) + owner)
        var executions = 0
        val registry = ToolRegistry().apply { register(tool { executions++; ToolResult.Success("run") }) }
        val orchestrator = AgentOrchestrator(taskScope, registry, Audit())
        orchestrator.submit("first")
        owner.cancel()
        runCurrent()
        assertEquals(TaskStatus.CANCELLED, orchestrator.tasks.value.single().status)
        assertEquals(0, executions)
    }
}
