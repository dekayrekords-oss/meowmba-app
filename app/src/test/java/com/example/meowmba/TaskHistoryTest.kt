package com.example.meowmba

import com.example.meowmba.core.agent.runtime.*
import com.example.meowmba.core.agent.tools.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskHistoryTest {
    private class Journal : TaskJournal {
        val records = linkedMapOf<String, AgentTask>()
        override suspend fun remove(id: String) { records.remove(id) }
        var failRead = false
        var failWrite = false
        override suspend fun read(): List<AgentTask> { check(!failRead); return records.values.toList() }
        override suspend fun write(task: AgentTask) { check(!failWrite); records[task.id] = TaskHistory.forStorage(task) }
    }
    private val audit = object : AuditSink { override suspend fun log(action: String, agentId: String, taskId: String?, detail: String) {} }
    private fun registry(approval: Boolean = false, action: suspend () -> ToolResult) = ToolRegistry().apply {
        register(object : Tool {
            override val definition = ToolDefinition("file.write", "Write file", "test", riskLevel = RiskLevel.MODERATE,
                confirmationPolicy = if (approval) ConfirmationPolicy.ALWAYS else ConfirmationPolicy.NONE)
            override suspend fun execute(args: Map<String, String>) = action()
        })
    }
    private fun AgentOrchestrator.submit() = submit("file", "file.write", mapOf("content" to "private input"), "Save my document") as SubmitResult.Started

    @Test fun checkpointPrecedesExecutionAndResultSurvivesRecreationWithoutReplay() = runTest {
        val store = Journal(); var calls = 0
        val reg = registry { calls++; assertTrue(store.records.values.single().executionStarted); ToolResult.Success("Saved and read back", ToolOutcome.VERIFIED) }
        val first = AgentOrchestrator(this, reg, audit, journal = store)
        first.submit(); advanceUntilIdle()
        assertEquals(1, calls); assertEquals(TaskStatus.COMPLETED, store.records.values.single().status)
        val second = AgentOrchestrator(this, reg, audit, journal = store)
        var callbacks = 0; second.onTaskCompleted = { callbacks++ }
        advanceUntilIdle()
        assertEquals(1, calls); assertEquals(0, callbacks)
        assertEquals("Saved and read back", second.tasks.value.single().result)
        assertEquals(ToolOutcome.VERIFIED, second.tasks.value.single().outcome)
        assertTrue(second.tasks.value.single().recovered)
        assertTrue(store.records.values.single().args.isEmpty())
    }

    @Test fun interruptedWriteIsUnconfirmedAndNeverRepeated() = runTest {
        val store = Journal()
        store.records["old"] = AgentTask(id = "old", agentId = "file", toolId = "file.write", title = "Old write", status = TaskStatus.RUNNING, executionStarted = true)
        var calls = 0
        val runner = AgentOrchestrator(this, registry { calls++; ToolResult.Success("unexpected") }, audit, journal = store)
        advanceUntilIdle()
        assertEquals(0, calls); assertEquals(TaskStatus.FAILED, runner.tasks.value.single().status)
        assertEquals(ToolOutcome.UNCONFIRMED, runner.tasks.value.single().outcome)
        assertEquals("Check outcome", TaskHistory.label(runner.tasks.value.single()))
        assertNull(runner.pendingApproval.value)
    }

    @Test fun oldApprovalDoesNotAuthorizeANewCallOrReplay() = runTest {
        val store = Journal()
        store.records["old"] = AgentTask(id = "old", agentId = "file", toolId = "file.write", title = "Approval", status = TaskStatus.WAITING_APPROVAL)
        var calls = 0
        val runner = AgentOrchestrator(this, registry(approval = true) { calls++; ToolResult.Success("saved") }, audit, journal = store)
        advanceUntilIdle()
        assertEquals("Not run", TaskHistory.label(runner.tasks.value.single()))
        runner.submit(); runCurrent()
        assertNotNull(runner.pendingApproval.value); assertEquals(0, calls)
        runner.resolveApproval(true); advanceUntilIdle(); assertEquals(1, calls)
    }

    @Test fun unreadableHistoryBlocksNewExternalActions() = runTest {
        val store = Journal().apply { failRead = true }; var calls = 0
        val runner = AgentOrchestrator(this, registry { calls++; ToolResult.Success("bad") }, audit, journal = store)
        runner.submit(); advanceUntilIdle()
        assertEquals(0, calls); assertEquals(TaskStatus.FAILED, runner.tasks.value.single().status)
        assertNotNull(runner.historyProblem.value)
    }

    @Test fun failedWriteAheadBlocksExternalAction() = runTest {
        val store = Journal().apply { failWrite = true }; var calls = 0
        val runner = AgentOrchestrator(this, registry { calls++; ToolResult.Success("bad") }, audit, journal = store)
        runner.submit(); advanceUntilIdle()
        assertEquals(0, calls); assertFalse(runner.tasks.value.single().executionStarted)
        assertNotNull(runner.historyProblem.value)
    }

    @Test fun cancellationCannotBeOverwrittenByLateResultOrQueuedSave() = runTest {
        val store = Journal(); val release = CompletableDeferred<Unit>()
        val runner = AgentOrchestrator(this, registry {
            try { delay(10_000) } catch (_: CancellationException) { withContext(NonCancellable) { release.await() } }
            ToolResult.Success("late result", ToolOutcome.VERIFIED)
        }, audit, journal = store)
        val id = runner.submit().taskId; runCurrent(); runner.cancel(id); runCurrent()
        release.complete(Unit); advanceUntilIdle()
        assertEquals(TaskStatus.CANCELLED, store.records.getValue(id).status)
        assertEquals(ToolOutcome.UNCONFIRMED, store.records.getValue(id).outcome)
        assertNull(store.records.getValue(id).result)
    }

    @Test fun removedResultDoesNotReappearAfterRestart() = runTest {
        val store = Journal(); val reg = registry { ToolResult.Success("saved") }
        val runner = AgentOrchestrator(this, reg, audit, journal = store)
        val id = runner.submit().taskId; advanceUntilIdle()
        runner.removeSavedTask(id); advanceUntilIdle()
        assertTrue(store.records.isEmpty()); assertTrue(runner.tasks.value.isEmpty())
        val restored = AgentOrchestrator(this, reg, audit, journal = store); advanceUntilIdle()
        assertTrue(restored.tasks.value.isEmpty())
    }

    @Test fun timedOutCallRequiresOutcomeReview() = runTest {
        val runner = AgentOrchestrator(this, registry { delay(40_000); ToolResult.Success("late") }, audit)
        runner.submit(); advanceUntilIdle()
        assertEquals(ToolOutcome.UNCONFIRMED, runner.tasks.value.single().outcome)
    }

    @Test fun emptyToolReturnIsNotSuccessfulCompletion() = runTest {
        val runner = AgentOrchestrator(this, registry { ToolResult.Success(" ") }, audit)
        runner.submit(); advanceUntilIdle()
        assertEquals(TaskStatus.FAILED, runner.tasks.value.single().status)
    }

    @Test fun historyRemovesArgumentsAndCredentialsAndBoundsOutput() {
        val record = TaskHistory.forStorage(AgentTask(agentId = "file", toolId = "file.write", title = "Save",
            args = mapOf("api_key" to "secret"), result = "API_KEY=secret\n-----BEGIN PRIVATE KEY-----\nprivate\n-----END PRIVATE KEY-----\n" + "x".repeat(40_000)))
        assertTrue(record.args.isEmpty()); assertFalse(record.result!!.contains("secret")); assertFalse(record.result.contains("\nprivate\n"))
        assertTrue(record.result.length <= 24_000)
    }

    @Test fun writtenDocumentIsVerifiedWithUnicodeAndPreservedContent() = runTest {
        val root = java.nio.file.Files.createTempDirectory("task-readback").toFile()
        try {
            val text = "European festivals · 音楽 🐈\n".repeat(1_000)
            val result = FileWriteTool(Workspace(root)).execute(mapOf("path" to "research.md", "content" to text)) as ToolResult.Success
            assertEquals(ToolOutcome.VERIFIED, result.outcome)
            assertEquals(text, java.io.File(root, "research.md").readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun handoffIsNotVerifiedOrGoalCompletion() {
        val task = AgentTask(agentId = "phone", toolId = "phone.take_photo", title = "Photo", status = TaskStatus.COMPLETED, outcome = ToolOutcome.HANDOFF)
        assertEquals("Continue in the opened app", TaskHistory.label(task))
        assertEquals(ToolOutcome.HANDOFF, TaskHistory.recover(task, 8).outcome)
    }
}
