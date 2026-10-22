package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.workflows.*
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real configured planner + production tool/policy engine, synthetic shop data only. */
class BusinessScenario129DeviceTest {
    @Test fun realPlannerPreparesShopMorningBrief() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = WorkflowRuntime.get(context)
        val storage = SecureStorageManager(context)
        val path = storage.getEndpoint("local")
        val localReady = path != null && File(path).isFile
        val provider = if (localReady) "local" else "openrouter"
        val model = if (localReady) File(path!!).name else storage.getModelName("openrouter") ?: "openrouter/free"
        if (!localReady) {
            assertTrue("No configured conversation provider", !storage.getApiKey("openrouter").isNullOrBlank())
            require(model == "openrouter/free" || model.endsWith(":free")) { "Acceptance cannot silently select a paid planner" }
        }
        assertTrue("Phone must cool before running the local business planner", runtime.coolEnough())
        val root = File(context.getExternalFilesDir(null), "business-scenario129-${UUID.randomUUID()}").also { it.mkdirs() }
        val workspace = File(root, "workspace").also { it.mkdirs() }
        File(workspace, "orders.csv").writeText("order,customer,amount,status,due\nA101,Alex Sample,21.50,unpaid,2026-09-10\nA102,Blair Sample,40.00,paid,2026-09-12\nA103,Casey Sample,35.25,unpaid,2026-09-13\nA104,Drew Sample,9.99,refunded,2026-09-11\n")
        val store = WorkflowStore(File(root, "state.json"))
        val agent = BusinessAgent(id = "shop-acceptance", name = "Shop operations assistant", provider = provider, model = model, cloudConsent = !localReady,
            instructions = "Use the original job's input/output paths. Inspect input files. Use files.csv_summary for exact totals. Create the requested briefing and draft follow-ups in the output file. Only finish after files.write and files.read verify that output. Never send messages.",
            tools = setOf("files.read", "files.csv_summary", "files.write"), completionTools = setOf("files.csv_summary", "files.write", "files.read"))
        store.saveAgent(agent)
        val id = store.enqueue(agent.id, "It is 14 September 2026. Review orders.csv for my small shop. Identify unpaid orders and total the money owed. Save a morning briefing to reports/morning.txt with the unpaid order IDs, customer names, exact total, and a short friendly draft follow-up for each overdue unpaid customer. Do not contact anyone. Read the saved report to verify it before finishing.")
        var approvals = 0
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            runtime.mutex.withLock {
                withTimeout(240_000) {
                    val engine = WorkflowEngine(store, WorkflowTools.local(workspace), runtime.brain, runtime::coolEnough)
                    repeat(24) {
                        val before = store.run(id)!!
                        if (before.status in setOf("COMPLETE", "BLOCKED", "CANCELLED")) return@withTimeout
                        engine.tick(id)
                        val run = store.run(id)!!
                        if (run.status == "APPROVAL") {
                            val step = run.steps.last()
                            assertEquals("files.write", step.tool)
                            assertEquals("reports/morning.txt", step.args["path"])
                            assertFalse("Write happened before approval", File(workspace, "reports/morning.txt").exists())
                            approvals++
                            store.approve(id, step.id, true)
                        }
                    }
                }
            }
            val result = store.run(id)!!
            assertEquals("Real planner stopped; inspect state.json evidence", "COMPLETE", result.status)
            assertEquals(1, approvals)
            val report = File(workspace, "reports/morning.txt").readText()
            for (expected in listOf("56.75", "A101", "A103", "Alex", "Casey")) assertTrue("Report missing $expected", report.contains(expected))
            assertTrue(result.steps.any { it.tool == "files.read" && it.args["path"] == "reports/morning.txt" && it.status == "SUCCEEDED" })
        } finally {
            runtime.releaseModel()
            File(root, "result.json").writeText(Json.encodeToString(mapOf("model" to model, "provider" to provider,
                "status" to store.run(id)!!.status, "approvals" to approvals.toString(),
                "elapsedMs" to (android.os.SystemClock.elapsedRealtime() - started).toString())))
            println("BUSINESS129 evidence=${root.name} status=${store.run(id)!!.status}")
        }
    }
}
