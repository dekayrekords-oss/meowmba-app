package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.workflows.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real Android storage and policy execution with deterministic planner decisions.
 * This validates tool execution, not a cloud model's planning intelligence. */
class AgentWorkflow129DeviceTest {
    @Test fun agentReportRequiresApprovalAndPersistsVerifiedEvidence() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "workflow129-${UUID.randomUUID()}").also { it.mkdirs() }
        try {
            val workspace = File(folder, "workspace").also { it.mkdirs() }
            File(workspace, "orders.csv").writeText("id,amount,status\nA,10.10,paid\nB,20.20,unpaid\nC,5.05,unpaid\n")
            val state = File(folder, "state.json")
            var store = WorkflowStore(state)
            store.saveAgent(BusinessAgent(id = "report", name = "Report acceptance", instructions = "Analyze synthetic orders", tools = setOf("files.csv_summary", "files.write")))
            val id = store.enqueue("report", "Write unpaid-order report")
            val brain = WorkflowBrain { _, _, run, _, _ ->
                val evidence = run.steps.filter { it.status == "SUCCEEDED" }
                when {
                    evidence.isEmpty() -> WorkDecision("tool", "files.csv_summary", mapOf("path" to "orders.csv", "amount_column" to "amount", "filter_column" to "status", "filter_value" to "unpaid"))
                    evidence.none { it.tool == "files.write" } -> WorkDecision("tool", "files.write", mapOf("path" to "reports/unpaid.txt", "content" to evidence.last().output))
                    else -> WorkDecision("finish", summary = "Report verified", evidence = listOf(evidence.last().id))
                }
            }
            var approvals = 0
            repeat(10) {
                WorkflowEngine(store, WorkflowTools.local(workspace), brain).tick(id)
                store = WorkflowStore(state)
                if (store.run(id)!!.status == "APPROVAL") {
                    assertFalse(File(workspace, "reports/unpaid.txt").exists())
                    approvals++
                    store.approve(id, store.run(id)!!.steps.last().id, true)
                }
            }
            assertEquals(1, approvals)
            assertEquals("COMPLETE", store.run(id)!!.status)
            assertTrue(File(workspace, "reports/unpaid.txt").readText().contains("25.25"))
            assertEquals(1, store.run(id)!!.steps.count { it.tool == "files.write" })
            assertTrue(store.run(id)!!.steps.any { it.output.contains("SHA-256:") })
            val denied = store.enqueue("report", "Denied draft")
            val denyBrain = WorkflowBrain { _, _, _, _, _ -> WorkDecision("tool", "files.write", mapOf("path" to "denied.txt", "content" to "never write")) }
            val engine = WorkflowEngine(store, WorkflowTools.local(workspace), denyBrain)
            engine.tick(denied); engine.tick(denied)
            assertEquals("APPROVAL", store.run(denied)!!.status)
            store.approve(denied, store.run(denied)!!.steps.last().id, false)
            engine.tick(denied)
            assertEquals("BLOCKED", WorkflowStore(state).run(denied)!!.status)
            assertFalse(File(workspace, "denied.txt").exists())
        } finally { folder.deleteRecursively() }
    }
}
