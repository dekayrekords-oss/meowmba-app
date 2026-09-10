package com.example.meowmba

import com.example.meowmba.core.agent.tools.*
import com.example.meowmba.core.workflows.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BusinessRepeatGuardTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun repeatedSuccessfulWriteStopsBeforeAnotherApprovalOrVersion() = runTest {
        val root = File(temp.root, "workspace")
        val store = WorkflowStore(File(temp.root, "state.json"))
        store.saveAgent(BusinessAgent(id = "writer", name = "Writer", instructions = "Write once", tools = setOf("files.write")))
        val runId = store.enqueue("writer", "Write a report")
        val engine = WorkflowEngine(store, WorkflowTools.local(root), WorkflowBrain { _, _, _, _, _ ->
            WorkDecision("tool", "files.write", mapOf("path" to "report.md", "content" to "Observed report"))
        })
        engine.tick(runId)
        store.approve(runId, store.run(runId)!!.steps.last().id, true)
        engine.tick(runId); engine.tick(runId)
        val result = store.run(runId)!!
        assertEquals("BLOCKED", result.status)
        assertTrue(result.note.contains("repeated"))
        assertEquals(1, result.steps.size)
        assertEquals("Observed report", File(root, "report.md").readText())
    }

    @Test fun repeatedReadStopsUntilAnInterveningObservedActionChangesContext() = runTest {
        val store = WorkflowStore(File(temp.root, "state.json"))
        store.saveAgent(BusinessAgent(id = "reader", name = "Reader", instructions = "Read once", tools = setOf("files.list")))
        val runId = store.enqueue("reader", "Read files")
        val engine = WorkflowEngine(store, WorkflowTools.local(File(temp.root, "workspace")), WorkflowBrain { _, _, _, _, _ ->
            WorkDecision("tool", "files.list")
        })
        engine.tick(runId); engine.tick(runId); engine.tick(runId)
        assertEquals("BLOCKED", store.run(runId)!!.status)
        assertEquals(1, store.run(runId)!!.steps.size)
    }

    @Test fun readWriteReadVerificationRemainsAllowed() = runTest {
        val root = File(temp.root, "workspace").also { it.mkdirs() }
        File(root, "report.md").writeText("Original")
        val store = WorkflowStore(File(temp.root, "state.json"))
        store.saveAgent(BusinessAgent(id = "editor", name = "Editor", instructions = "Update and verify", tools = setOf("files.read", "files.write")))
        val runId = store.enqueue("editor", "Update report")
        val engine = WorkflowEngine(store, WorkflowTools.local(root), WorkflowBrain { _, _, run, _, _ ->
            when (run.steps.count { it.status == "SUCCEEDED" }) {
                0, 2 -> WorkDecision("tool", "files.read", mapOf("path" to "report.md"))
                1 -> WorkDecision("tool", "files.write", mapOf("path" to "report.md", "content" to "Changed"))
                else -> WorkDecision("finish", summary = "Read updated report", evidence = listOf(run.steps.last().id))
            }
        })
        repeat(8) {
            engine.tick(runId)
            if (store.run(runId)!!.status == "APPROVAL") store.approve(runId, store.run(runId)!!.steps.last().id, true)
        }
        assertEquals("COMPLETE", store.run(runId)!!.status)
        assertEquals(2, store.run(runId)!!.steps.count { it.tool == "files.read" })
    }
}
