package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.workflows.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Explicit two-stage test: force-stop the DEBUG package between these methods.
 * This exercises the production WorkManager dispatcher and encrypted local
 * model selection, not an injected worker or a scripted brain. */
class BusinessWorkerDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val marker get() = File(context.filesDir, "workflow-test-recovery.json")
    private fun report(text: String) = InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", "$text\n") })

    @Test fun prepareApprovalCheckpoint() = runBlocking {
        val runtime = WorkflowRuntime.get(context)
        if (marker.exists()) {
            val previous = JSONObject(marker.readText())
            runtime.store.run(previous.getString("run"))?.let { run ->
                check(run.goal.startsWith("Analyze unpaid orders in device-test-"))
                runtime.store.cancel(run.id)
                runtime.mutex.withLock { runtime.exportAndRemove(run.id) }
                val owned = setOf(previous.getString("manager"), previous.getString("analyst"))
                runtime.store.change { it.copy(agents = it.agents.filterNot { agent -> agent.id in owned }) }
            }
        }
        val model = File(context.filesDir, "models/gemma-4-E2B-it.litertlm")
        withTimeout(240_000) {
            while (!runtime.coolEnough()) { report("Waiting for the real phone to cool before the acceptance run"); delay(15_000) }
        }
        if (com.example.meowmba.core.security.SecureStorageManager(context).getEndpoint("local") != model.path)
            runtime.mutex.withLock { runtime.verifyAndSelectLocal(model.path) }
        val suffix = UUID.randomUUID().toString()
        val input = "device-test-$suffix/orders.csv"
        val output = "device-test-$suffix/report.txt"
        val options = InstrumentationRegistry.getArguments()
        val amountB = options.getString("amountB", "20.20")!!
        val amountC = options.getString("amountC", "5.05")!!
        val amountColumn = options.getString("amountColumn", "amount")!!
        val statusColumn = options.getString("statusColumn", "status")!!
        require(listOf(amountColumn, statusColumn).all { it.matches(Regex("[A-Za-z_]{1,40}")) } && amountColumn != statusColumn)
        require(listOf(amountB, amountC).all { it.matches(Regex("[0-9]{1,5}\\.[0-9]{2}")) })
        val expectedTotal = java.math.BigDecimal(amountB).add(java.math.BigDecimal(amountC)).toPlainString()
        File(runtime.workspace, input).also { it.parentFile!!.mkdirs() }.writeText("id,$amountColumn,$statusColumn\nA,10.10,paid\nB,$amountB,unpaid\nC,$amountC,Unpaid\n")
        val roles = WorkflowTemplates.operationsTeam()
        val analyst = roles[0].copy(name = "Device QA analyst")
        val manager = roles[1].copy(name = "Device QA manager")
        runtime.store.saveAgent(analyst); runtime.store.saveAgent(manager)
        val id = runtime.enqueue(manager.id, "Analyze unpaid orders in $input. Use the analyst, then save $output with the exact unpaid total and row count.")
        marker.writeText(JSONObject().put("run", id).put("output", output).put("manager", manager.id).put("analyst", analyst.id).put("expectedTotal", expectedTotal).toString())
        var previous = ""
        withTimeout(360_000) {
            while (true) {
                val run = runtime.store.run(id)!!
                if (run.status + run.note != previous) { report("Worker: ${run.status}; ${run.note.take(500)}"); previous = run.status + run.note }
                assertFalse("${run.status}: ${run.note}", run.status in WorkflowStore.TERMINAL)
                if (run.status == "APPROVAL") break
                delay(1000)
            }
        }
        assertFalse(File(runtime.workspace, output).exists())
        assertEquals(output, runtime.store.run(id)!!.steps.last().args["path"])
        report("READY FOR FORCE-STOP: $id; approval and selected local model are saved")
    }

    @Test fun resumeCheckpointAndFinishInProductionWorker() = runBlocking {
        val data = JSONObject(marker.readText())
        val runtime = WorkflowRuntime.get(context)
        val id = data.getString("run")
        val before = runtime.store.run(id)!!
        assertEquals("APPROVAL", before.status)
        assertEquals("files.write", before.steps.last().tool)
        assertEquals(data.getString("output"), before.steps.last().args["path"])
        val expectedTotal = data.optString("expectedTotal", "25.25")
        assertTrue(before.steps.last().args["content"].orEmpty().contains(expectedTotal))
        runtime.store.approve(id, before.steps.last().id, true)
        runtime.recover() // same recovery entry point as MainActivity.onCreate
        withTimeout(150_000) {
            while (runtime.store.run(id)!!.status !in WorkflowStore.TERMINAL) delay(1000)
        }
        val after = runtime.store.run(id)!!
        assertEquals(after.note, "COMPLETE", after.status)
        assertTrue(File(runtime.workspace, data.getString("output")).readText().contains(expectedTotal))
        assertEquals(1, after.steps.count { it.tool == "files.write" && it.status == "SUCCEEDED" })
        report("PASS: WorkManager resumed the persisted approval after process restart, wrote one verified report and completed using the local model")
        runtime.exportAndRemove(id)
        runtime.store.change { it.copy(agents = it.agents.filterNot { agent -> agent.id in setOf(data.getString("manager"), data.getString("analyst")) }) }
    }

    /** Remove only the explicitly recorded abandoned fixtures from this QA
     * session. Preserve their audit history and files; never clear app data. */
    @Test fun archiveAbandonedSyntheticFixtures(): Unit = runBlocking {
        val runtime = WorkflowRuntime.get(context)
        runtime.mutex.withLock {
            listOf("062d0b68-d210-493d-96ac-6c2c86663f2a", "908dcb1d-512c-499c-8fe6-946f11420ace").forEach { id ->
                runtime.store.run(id)?.let { run ->
                    check(run.goal.startsWith("Analyze unpaid orders in device-test-"))
                    runtime.store.cancel(id); runtime.exportAndRemove(id)
                }
            }
            val agents = setOf("f336fd41-d481-4400-bba4-da4eea2b5cde", "c709842d-f69b-47b6-9b78-4e4b3ef25f60", "8678f9bd-de4d-4228-b7ee-ca147f47bd35", "bd256888-1925-4dfd-ab29-c868bd724e8f")
            check(runtime.store.state.value.runs.none { it.status !in WorkflowStore.TERMINAL && it.frames.any { frame -> frame.agentId in agents } })
            runtime.store.change { it.copy(agents = it.agents.filterNot { agent -> agent.id in agents }) }
        }
    }
}
