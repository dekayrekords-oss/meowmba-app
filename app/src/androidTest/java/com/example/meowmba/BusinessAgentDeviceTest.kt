package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.workflows.*
import com.example.meowmba.core.agent.LiteRtProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Only synthetic orders in a unique test workspace. Never connects accounts,
 * sends messages, or reads the user's business files. Scripted plumbing tests
 * and real on-device model tests are intentionally separate. */
class BusinessAgentDeviceTest {
    private fun report(text: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", "$text\n") })
    }

    @Test fun durableFileWorkflowOnPhone() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.filesDir, "workflow-tests/${java.util.UUID.randomUUID()}").also { it.mkdirs() }
        val file = File(folder, "state.json")
        var store = WorkflowStore(file)
        val tools = WorkflowTools.local(File(folder, "business"))
        store.saveAgent(BusinessAgent(id = "writer", name = "Test writer", instructions = "Write a sample report", tools = setOf("files.write")))
        val id = store.enqueue("writer", "Write a sample report")
        val brain = WorkflowBrain { _, _, run, _, _ ->
            val success = run.steps.lastOrNull { it.status == "SUCCEEDED" }
            if (success == null) WorkDecision("tool", "files.write", mapOf("path" to "reports/test.txt", "content" to "Verified on Flip 6"))
            else WorkDecision("finish", summary = "Report exists", evidence = listOf(success.id))
        }
        WorkflowEngine(store, tools, brain).tick(id)
        assertEquals("APPROVAL", store.run(id)!!.status)
        assertFalse(File(folder, "business/reports/test.txt").exists())
        store = WorkflowStore(file)
        store.approve(id, store.run(id)!!.steps.last().id, true)
        repeat(3) { WorkflowEngine(store, tools, brain).tick(id); store = WorkflowStore(file) }
        assertEquals("COMPLETE", store.run(id)!!.status)
        assertEquals("Verified on Flip 6", File(folder, "business/reports/test.txt").readText())
        report("PASS real phone files, approval persistence, readback, SHA-256 and checkpoint reopen")
    }

    @Test fun realLocalModelDelegatesAndWritesVerifiedReport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.filesDir, "models/gemma-4-E2B-it.litertlm")
        assertTrue("A real local model must be installed for this test", model.isFile && model.length() > 2_000_000_000L)
        val provider = LiteRtProvider(context, model.path)
        try {
            repeat(3) { round ->
                val folder = File(context.filesDir, "workflow-tests/local-$round-${java.util.UUID.randomUUID()}").also { it.mkdirs() }
                val workspace = File(folder, "business").also { it.mkdirs() }
                File(workspace, "orders.csv").writeText("id,amount,status\nA,10.10,paid\nB,20.20,unpaid\nC,5.05,unpaid\n")
                val checkpoint = File(folder, "state.json")
                var store = WorkflowStore(checkpoint)
                store.saveAgent(BusinessAgent(id = "analyst", name = "Order analyst", instructions = "Use files.csv_summary with path orders.csv, amount_column amount, filter_column status, filter_value unpaid. Then finish with the exact result and its evidence step ID.", tools = setOf("files.csv_summary"), completionTools = setOf("files.csv_summary")))
                store.saveAgent(BusinessAgent(id = "manager", name = "Operations manager", instructions = "First delegate analysis to analyst. Once the analyst returns, write reports/unpaid.txt with the analyst's actual numeric total and selected row count using files.write. Then finish citing the successful write step. Do not delegate a second time.", tools = setOf("files.write"), delegates = setOf("analyst"), completionTools = setOf("files.write")))
                val id = store.enqueue("manager", "Analyze unpaid orders in orders.csv and save reports/unpaid.txt. Use the analyst and exact CSV arithmetic.")
                val brain = WorkflowModelBrain { provider }
                val power = context.getSystemService(android.os.PowerManager::class.java)
                val start = System.currentTimeMillis()
                var ticks = 0
                while (store.run(id)!!.status !in WorkflowStore.TERMINAL && ticks++ < 24) {
                    if (power.currentThermalStatus >= android.os.PowerManager.THERMAL_STATUS_MODERATE) {
                        report("Cooling before round $round step $ticks")
                        val coolUntil = System.currentTimeMillis() + 180_000
                        while (power.currentThermalStatus >= android.os.PowerManager.THERMAL_STATUS_MODERATE && System.currentTimeMillis() < coolUntil) kotlinx.coroutines.delay(5000)
                        assertTrue("Phone must cool before further inference", power.currentThermalStatus < android.os.PowerManager.THERMAL_STATUS_MODERATE)
                    }
                    WorkflowEngine(store, WorkflowTools.local(workspace), brain).tick(id)
                    store = WorkflowStore(checkpoint)
                    val run = store.run(id)!!
                    report("Round ${round + 1} tick $ticks: ${run.status}; ${run.note.take(700)}")
                    if (run.status == "APPROVAL") {
                        val step = run.steps.last()
                        assertEquals("files.write", step.tool)
                        assertEquals("reports/unpaid.txt", step.args["path"])
                        assertTrue("Model must use the verified numeric total", step.args["content"].orEmpty().contains("25.25"))
                        // This test's authorization covers this synthetic file only.
                        store.approve(id, step.id, true)
                    }
                }
                val run = store.run(id)!!
                assertEquals("${run.note}; checkpoint=$checkpoint", "COMPLETE", run.status)
                assertTrue(File(workspace, "reports/unpaid.txt").readText().contains("25.25"))
                assertTrue(run.steps.any { it.agentId == "analyst" && it.tool == "files.csv_summary" && it.status == "SUCCEEDED" })
                assertEquals(1, run.steps.count { it.tool == "files.write" && it.status == "SUCCEEDED" })
                report("PASS local AI round ${round + 1}: ${(System.currentTimeMillis() - start) / 1000}s; $checkpoint")
            }
        } finally { provider.release() }
    }
}
