package com.example.meowmba

import kotlinx.serialization.json.jsonObject

import com.example.meowmba.core.workflows.*
import com.example.meowmba.core.agent.tools.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BusinessWorkflowTest {
    @Test fun thermalPauseWaitsForMeaningfulCoolingBeforeReloadingTheModel() {
        val gate = WorkflowThermalGate()
        assertTrue(gate.allowsRun(385, false, false))
        assertFalse(gate.allowsRun(390, false, false))
        assertFalse(gate.allowsRun(389, false, false))
        assertFalse(gate.allowsRun(375, false, false))
        assertTrue(gate.allowsRun(374, false, false))
        assertFalse(gate.allowsRun(360, true, false))
        assertFalse(gate.allowsRun(360, false, true))
        assertTrue(gate.allowsRun(360, false, false))
    }
    @get:Rule val temp = TemporaryFolder()
    private fun store() = WorkflowStore(File(temp.root, "state.json"))
    private fun agent(id: String = "manager", tools: Set<String> = setOf("files.list", "files.read", "files.write", "files.csv_summary")) =
        BusinessAgent(id = id, name = id, instructions = "Process orders safely", tools = tools)
    private fun brain(decision: WorkDecision) = WorkflowBrain { _, _, _, _, _ -> decision }
    private fun registry() = WorkflowTools.local(File(temp.root, "workspace"))
    private fun scripted(counter: () -> ToolResult, risk: RiskLevel = RiskLevel.SAFE, timeout: Long = 30_000, permissions: Set<String> = emptySet()) = ToolRegistry().apply {
        register(object : Tool {
            override val definition = ToolDefinition("test", "Test", "Test", requiredPermissions = permissions, riskLevel = risk, timeoutMs = timeout)
            override suspend fun execute(args: Map<String, String>) = counter()
        })
    }

    @Test fun teamDelegatesAnalyzesApprovesWritesAndSurvivesReload() = runBlocking {
        var store = store()
        val root = File(temp.root, "workspace").also { it.mkdirs() }
        File(root, "orders.csv").writeText("id,amount,status\nA,10.10,paid\nB,20.20,unpaid\nC,5.05,unpaid\n")
        val analyst = agent("analyst", setOf("files.csv_summary"))
        store.saveAgent(analyst); store.saveAgent(agent().copy(delegates = setOf(analyst.id)))
        val id = store.enqueue("manager", "Write an unpaid-order report")
        val brain = WorkflowBrain { agent, _, run, _, _ ->
            val evidence = run.steps.filter { it.status == "SUCCEEDED" }
            when {
                agent.id == "analyst" && evidence.isEmpty() -> WorkDecision("tool", "files.csv_summary", mapOf("path" to "orders.csv", "amount_column" to "amount", "filter_column" to "status", "filter_value" to "unpaid"))
                agent.id == "analyst" -> WorkDecision("finish", summary = evidence.last().output, evidence = listOf(evidence.last().id))
                evidence.isEmpty() -> WorkDecision("delegate", agentId = "analyst", goal = "Calculate unpaid total")
                evidence.none { it.tool == "files.write" } -> WorkDecision("tool", "files.write", mapOf("path" to "reports/unpaid.txt", "content" to evidence.last().output))
                else -> WorkDecision("finish", summary = "Report saved and verified", evidence = listOf(evidence.last().id))
            }
        }
        repeat(15) {
            val engine = WorkflowEngine(store, registry(), brain)
            engine.tick(id)
            // Reopen the actual file after every durable transition, not an in-memory fake.
            store = WorkflowStore(File(temp.root, "state.json"))
            if (store.run(id)!!.status == "APPROVAL") {
                assertFalse(File(root, "reports/unpaid.txt").exists())
                store.approve(id, store.run(id)!!.steps.last().id, true)
            }
        }
        assertEquals("COMPLETE", store.run(id)!!.status)
        assertTrue(File(root, "reports/unpaid.txt").readText().contains("25.25"))
        assertTrue(store.run(id)!!.steps.any { it.output.contains("SHA-256:") })
        assertEquals(1, store.run(id)!!.steps.count { it.tool == "files.write" })
    }
    @Test fun writeNeverRunsBeforeApprovalAndDenialPersists() = runBlocking {
        val s = store(); s.saveAgent(agent())
        val id = s.enqueue("manager", "Write draft")
        val engine = WorkflowEngine(s, registry(), brain(WorkDecision("tool", "files.write", mapOf("path" to "draft.txt", "content" to "hello"))))
        engine.tick(id); engine.tick(id)
        assertEquals("APPROVAL", s.run(id)!!.status)
        assertFalse(File(temp.root, "workspace/draft.txt").exists())
        s.approve(id, s.run(id)!!.steps.last().id, false); engine.tick(id)
        assertEquals("BLOCKED", store().run(id)!!.status)
        assertFalse(File(temp.root, "workspace/draft.txt").exists())
    }
    @Test fun forgedCompletionHasNoEvidenceAndIsBlocked() = runBlocking {
        val s = store(); s.saveAgent(agent()); val id = s.enqueue("manager", "Send an email")
        WorkflowEngine(s, registry(), brain(WorkDecision("finish", summary = "Sent!", evidence = listOf("invented")))).tick(id)
        assertEquals("BLOCKED", s.run(id)!!.status)
    }
    @Test fun unregisteredOrUnpermittedToolCannotRun() = runBlocking {
        for (tool in listOf("files.write", "shell.exec")) {
            val s = store(); s.saveAgent(agent(tools = emptySet())); val id = s.enqueue("manager", "Try")
            WorkflowEngine(s, registry(), brain(WorkDecision("tool", tool))).tick(id)
            assertEquals("BLOCKED", s.run(id)!!.status); assertTrue(s.run(id)!!.steps.isEmpty())
        }
    }
    @Test fun permissionRevokedWhileWaitingForApprovalIsEnforced() = runBlocking {
        val s = store(); s.saveAgent(agent()); val id = s.enqueue("manager", "Write")
        val e = WorkflowEngine(s, registry(), brain(WorkDecision("tool", "files.write", mapOf("path" to "x", "content" to "x"))))
        e.tick(id); s.approve(id, s.run(id)!!.steps.last().id, true)
        s.saveAgent(agent(tools = emptySet())); e.tick(id)
        assertEquals("BLOCKED", s.run(id)!!.status)
        assertFalse(File(temp.root, "workspace/x").exists())
    }
    @Test fun osPermissionIsCheckedBeforeDispatch() = runBlocking {
        var count = 0; val s = store(); s.saveAgent(agent(tools = setOf("test"))); val id = s.enqueue("manager", "Try")
        val tools = scripted({ count++; ToolResult.Success("done") }, permissions = setOf("protected"))
        WorkflowEngine(s, tools, brain(WorkDecision("tool", "test"))).tick(id)
        assertEquals(0, count); assertEquals("BLOCKED", s.run(id)!!.status)
    }
    @Test fun cancellationDuringPlanningCannotResurrectJob() = runBlocking {
        val s = store(); s.saveAgent(agent()); val id = s.enqueue("manager", "Try")
        val e = WorkflowEngine(s, registry(), WorkflowBrain { _, _, _, _, _ ->
            s.cancel(id); WorkDecision("tool", "files.list")
        })
        e.tick(id); assertEquals("CANCELLED", s.run(id)!!.status); assertTrue(s.run(id)!!.steps.isEmpty())
    }
    @Test fun configurationChangedDuringPlanningFailsClosed() = runBlocking {
        val s = store(); s.saveAgent(agent()); val id = s.enqueue("manager", "Try")
        WorkflowEngine(s, registry(), WorkflowBrain { _, _, _, _, _ ->
            s.saveAgent(agent(tools = emptySet())); WorkDecision("tool", "files.list")
        }).tick(id)
        assertEquals("BLOCKED", s.run(id)!!.status)
    }
    @Test fun cancellationDuringToolKeepsActualResultButStopsNextAction() = runBlocking {
        val s = store(); s.saveAgent(agent(tools = setOf("test"))); val id = s.enqueue("manager", "Try")
        val e = WorkflowEngine(s, scripted({ s.cancel(id); ToolResult.Success("Actually ran") }), brain(WorkDecision("tool", "test")))
        e.tick(id); e.tick(id); e.tick(id)
        assertEquals("CANCELLED", s.run(id)!!.status)
        assertEquals("Actually ran", s.run(id)!!.steps.single().output)
    }
    @Test fun interruptedActionIsNeverAutomaticallyReplayed() = runBlocking {
        var calls = 0; val s = store(); s.saveAgent(agent(tools = setOf("test"))); val id = s.enqueue("manager", "Try")
        s.updateRun(id) { it.copy(steps = listOf(WorkStep(agentId = "manager", tool = "test", args = emptyMap(), status = "EXECUTING"))) }
        WorkflowEngine(store(), scripted({ calls++; ToolResult.Success("done") }), brain(WorkDecision("finish"))).tick(id)
        assertEquals(0, calls); assertEquals("BLOCKED", store().run(id)!!.status)
    }
    @Test fun failureIsNotTreatedAsSuccess() = runBlocking {
        val s = store(); s.saveAgent(agent(tools = setOf("test"))); val id = s.enqueue("manager", "Try")
        val e = WorkflowEngine(s, scripted({ ToolResult.Failure("HTTP 401") }), brain(WorkDecision("tool", "test")))
        e.tick(id); e.tick(id)
        assertEquals("BLOCKED", s.run(id)!!.status); assertEquals("FAILED", s.run(id)!!.steps.single().status)
    }
    @Test fun delegationMustBeAllowedAndCyclesAreBlocked() = runBlocking {
        val s = store(); s.saveAgent(agent()); val id = s.enqueue("manager", "Try")
        WorkflowEngine(s, registry(), brain(WorkDecision("delegate", agentId = "manager", goal = "cycle"))).tick(id)
        assertEquals("BLOCKED", s.run(id)!!.status)
    }
    @Test fun disablingParentStopsRunningDelegate() = runBlocking {
        val s = store(); s.saveAgent(agent().copy(delegates = setOf("child"))); s.saveAgent(agent("child"))
        val id = s.enqueue("manager", "Try")
        val e = WorkflowEngine(s, registry(), brain(WorkDecision("delegate", agentId = "child", goal = "Try")))
        e.tick(id); s.saveAgent(agent().copy(enabled = false)); e.tick(id)
        assertEquals("BLOCKED", s.run(id)!!.status)
    }
    @Test fun heatPauseMakesNoModelOrToolCallsAndCanResume() = runBlocking {
        var cool = false; var modelCalls = 0
        val s = store(); s.saveAgent(agent()); val id = s.enqueue("manager", "Try")
        val e = WorkflowEngine(s, registry(), WorkflowBrain { _, _, _, _, _ -> modelCalls++; WorkDecision("tool", "files.list") }, { cool })
        e.tick(id); assertEquals("PAUSED", s.run(id)!!.status); assertEquals(0, modelCalls)
        cool = true; e.tick(id); assertEquals(1, modelCalls)
    }
    @Test fun modelBudgetIsDurable() = runBlocking {
        val s = store(); s.saveAgent(agent()); val id = s.enqueue("manager", "Try")
        s.updateRun(id) { it.copy(attempts = 40) }
        WorkflowEngine(s, registry(), brain(WorkDecision("tool", "files.list"))).tick(id)
        assertEquals("BLOCKED", s.run(id)!!.status)
    }
    @Test fun modelTimeoutBlocksWithoutHangingQueue() = runTest {
        val s = store(); s.saveAgent(agent()); val id = s.enqueue("manager", "Try")
        WorkflowEngine(s, registry(), WorkflowBrain { _, _, _, _, _ -> delay(100_000); WorkDecision("tool", "files.list") }).tick(id)
        assertEquals("BLOCKED", s.run(id)!!.status)
    }
    @Test fun schedulesDoNotDuplicateActiveJobsAndRespectIntervals() {
        val s = store(); s.saveAgent(agent().copy(intervalMinutes = 15, scheduledGoal = "Daily report"))
        s.scheduleDue(1_000_000); s.scheduleDue(2_000_000)
        assertEquals(1, s.state.value.runs.size)
        s.cancel(s.state.value.runs.single().id); s.scheduleDue(1_000_001)
        assertEquals(1, s.state.value.runs.size)
        s.scheduleDue(2_000_000); assertEquals(2, store().state.value.runs.size)
    }
    @Test fun invalidIntervalsAndCloudWithoutConsentRejected() {
        for (a in listOf(agent().copy(intervalMinutes = 1), agent().copy(intervalMinutes = Long.MAX_VALUE), agent().copy(provider = "openrouter"))) {
            assertTrue(runCatching { store().saveAgent(a) }.isFailure)
        }
    }
    @Test fun staleOrExpiredApprovalCannotAuthorizeAnotherAction() = runBlocking {
        val s = store(); s.saveAgent(agent(tools = setOf("test"))); val id = s.enqueue("manager", "Try")
        val e = WorkflowEngine(s, scripted({ ToolResult.Success("done") }, RiskLevel.DESTRUCTIVE), brain(WorkDecision("tool", "test")))
        e.tick(id)
        assertTrue(runCatching { s.approve(id, "wrong-id", true) }.isFailure)
        s.updateRun(id) { it.copy(steps = it.steps.map { step -> step.copy(approvalExpiresAt = 1) }) }
        assertTrue(runCatching { s.approve(id, s.run(id)!!.steps.last().id, true) }.isFailure)
        e.tick(id); assertEquals("BLOCKED", s.run(id)!!.status)
    }
    @Test fun corruptedCheckpointIsPreservedNotReset() {
        val f = File(temp.root, "state.json"); f.writeText("broken")
        assertTrue(runCatching { store() }.isFailure); assertEquals("broken", f.readText())
    }
    @Test fun traversalAndSymlinkEscapesAreRejected() = runBlocking {
        val tools = registry(); val outside = temp.newFolder("outside"); File(outside, "secret").writeText("secret")
        java.nio.file.Files.createSymbolicLink(File(temp.root, "workspace/link").toPath(), outside.toPath())
        for (path in listOf("../outside/secret", "link/secret")) {
            assertTrue(tools.byId("files.read")!!.execute(mapOf("path" to path)) is ToolResult.Failure)
            assertTrue(tools.byId("files.write")!!.execute(mapOf("path" to path, "content" to "bad")) is ToolResult.Failure)
        }
        val listing = tools.byId("files.list")!!.execute(emptyMap()) as ToolResult.Success
        assertFalse(listing.output.contains("secret")); assertEquals("secret", File(outside, "secret").readText())
    }
    @Test fun csvCaseAliasesAreUnambiguousAndMatchingModeIsReported() {
        val text = "id,amount,status\nA,7.11,unpaid\nB,2.22,Unpaid\nC,90.00,paid\n"
        val result = CsvSummary.summarize(text, "Amount", "Status", "Unpaid")
        assertTrue(result.contains("Selected rows: 2"))
        assertTrue(result.contains("Sum of amount: 9.33"))
        assertTrue(result.contains("case-insensitive"))
        val exact = CsvSummary.summarize(text, "amount", "status", "Unpaid", caseSensitive = true)
        assertTrue(exact.contains("Selected rows: 1"))
        assertTrue(exact.contains("Sum of amount: 2.22"))
        assertTrue(exact.contains("case-sensitive"))
    }

    @Test fun csvRejectsAmbiguousOrInventedColumnsWithActualChoices() {
        val text = "amount,Amount,status\n1,2,paid\n"
        assertTrue(runCatching { CsvSummary.summarize(text, "AMOUNT") }.exceptionOrNull()!!.message!!.contains("ambiguous"))
        assertTrue(CsvSummary.summarize(text, "Amount").contains("Sum of Amount: 2"))
        val error = runCatching { CsvSummary.summarize(text, "amount", "payment_status", "paid") }.exceptionOrNull()!!.message!!
        assertTrue(error.contains("amount, Amount, status"))
        assertTrue(error.contains("payment_status"))
    }

    @Test fun csvDoesNotFuzzyMatchOrTrimFilterValues() {
        val text = "amount,status\n1,unpaid\n2, unpaid\n4,paid\n"
        assertTrue(CsvSummary.summarize(text, "amount", "status", "Unpaid").contains("Sum of amount: 1"))
        assertTrue(CsvSummary.summarize(text, "amount", "status", "unpai").contains("Selected rows: 0"))
    }

    @Test fun csvHandlesQuotedFieldsNewlinesAndExactDecimals() {
        val text = "id,amount,note\r\n1,0.10,\"a,b\"\r\n2,0.20,\"a\nline\"\r\n"
        assertTrue(CsvSummary.summarize(text, "amount").contains("0.30"))
        for (invalid in listOf("id,amount\na,10USD", "id,amount\na,1,2", "id,amount\na,\"1")) {
            assertTrue(runCatching { CsvSummary.summarize(invalid, "amount") }.isFailure)
        }
    }
    @Test fun decisionParserRejectsProseAndMalformedArguments() {
        assertEquals("tool", WorkflowModelBrain.parse("""{"action":"tool","tool":"files.list"}""").action)
        assertTrue(runCatching { WorkflowModelBrain.parse("I have completed everything") }.isFailure)
        assertTrue(runCatching { WorkflowModelBrain.parse("""{"action":"tool","args":{"x":[]}}""") }.isFailure)
    }
    @Test fun voiceCommandsReachBusinessToolsWithoutModelGuesswork() {
        val router = com.example.meowmba.core.agent.runtime.AgentRouter
        assertEquals("business.open", router.route("open business agents")?.toolId)
        assertEquals("business.jobs", router.route("show business jobs")?.toolId)
        val start = router.route("ask business agent Operations manager to summarize orders")!!
        assertEquals("business.start", start.toolId)
        assertEquals("Operations manager", start.args["agent"])
        assertEquals("summarize orders", start.args["goal"])
    }
    @Test fun cancellingWhileModelWaitsStopsInferenceAndLeavesNoNewStep() = runTest {
        val s = store(); s.saveAgent(agent()); val id = s.enqueue("manager", "Try")
        var cancelled = false
        val e = WorkflowEngine(s, registry(), WorkflowBrain { _, _, _, _, _ ->
            try { delay(80_000); WorkDecision("tool", "files.list") } finally { cancelled = true }
        })
        val work = launch { e.tick(id) }
        runCurrent()
        s.cancel(id); work.join()
        assertTrue(cancelled); assertTrue(s.run(id)!!.steps.isEmpty())
        assertEquals("CANCELLED", s.run(id)!!.status)
    }
    @Test fun gettingHotDuringPlanningStopsNativeWorkBeforeAnyTool() = runTest {
        val s = store(); s.saveAgent(agent()); val id = s.enqueue("manager", "Try")
        var cool = true
        val e = WorkflowEngine(s, registry(), WorkflowBrain { _, _, _, _, _ -> delay(80_000); WorkDecision("tool", "files.list") }, { cool })
        val work = launch { e.tick(id) }
        runCurrent(); cool = false; work.join()
        assertEquals("PAUSED", s.run(id)!!.status); assertTrue(s.run(id)!!.steps.isEmpty())
    }
    @Test fun completionRequiresTheConfiguredActionNotJustSomeSuccessfulRead() = runBlocking {
        val s = store(); s.saveAgent(agent().copy(completionTools = setOf("files.write")))
        val id = s.enqueue("manager", "Write a report")
        s.updateRun(id) { it.copy(steps = listOf(WorkStep(id = "s1", agentId = "manager", tool = "files.read", args = emptyMap(), status = "SUCCEEDED", output = "data"))) }
        val e = WorkflowEngine(s, registry(), brain(WorkDecision("finish", summary = "Done", evidence = listOf("s1"))))
        repeat(4) { e.tick(id) }
        assertEquals("BLOCKED", s.run(id)!!.status)
        assertEquals(3, s.run(id)!!.steps.count { it.status == "REJECTED" })
        assertTrue(s.run(id)!!.steps.none { it.tool == "agent.result" })
    }
    @Test fun failedReadCanBeCorrectedButDoesNotCountAsEvidence() = runBlocking {
        val s = store(); s.saveAgent(agent()); val id = s.enqueue("manager", "Read orders")
        val e = WorkflowEngine(s, registry(), brain(WorkDecision("tool", "files.read", mapOf("path" to "missing.csv"))))
        e.tick(id); e.tick(id)
        assertEquals("QUEUED", s.run(id)!!.status)
        assertEquals("FAILED", s.run(id)!!.steps.last().status)
        repeat(6) { e.tick(id) }
        assertEquals("BLOCKED", s.run(id)!!.status)
    }
    @Test fun localGrammarExcludesPrematureFinishAndInventedDelegates() {
        val agent = agent().copy(completionTools = setOf("files.write"))
        val run = BusinessRun(agentId = agent.id, goal = "Write report")
        val schema = org.json.JSONObject(WorkflowModelBrain.decisionSchema(agent, run.frames.first(), run, registry().definitions(), listOf(agent("real-child-id"))))
        val props = schema.getJSONObject("properties")
        assertFalse(props.getJSONObject("action").getJSONArray("enum").toString().contains("finish"))
        assertEquals("[\"d1\"]", props.getJSONObject("agentId").getJSONArray("enum").toString())
    }
    @Test fun explicitUserFileReferencesConstrainPathsWithoutInventingFiles() {
        val goal = "Analyze imports/alpha-42/orders.csv and write reports/unpaid.txt. Use files.csv_summary."
        assertEquals(listOf("imports/alpha-42/orders.csv", "reports/unpaid.txt"), WorkflowModelBrain.fileReferences(goal))
        val a = agent(tools = setOf("files.csv_summary"))
        val run = BusinessRun(agentId = a.id, goal = goal)
        val schema = org.json.JSONObject(WorkflowModelBrain.decisionSchema(a, run.frames.first(), run, registry().definitions().filter { it.id in a.tools }, emptyList()))
        val paths = schema.getJSONObject("properties").getJSONObject("args").getJSONObject("properties").getJSONObject("path").getJSONArray("enum")
        assertEquals(2, paths.length())
        assertFalse(paths.toString().contains("files.csv_summary"))
        assertEquals(listOf("imports/monthly orders.csv", "reports/2026.09.summary.txt"),
            WorkflowModelBrain.fileReferences("Read \"imports/monthly orders.csv\" and write reports/2026.09.summary.txt."))
        assertTrue(WorkflowModelBrain.fileReferences("Use https://example.com/file.csv or ../outside.csv").isEmpty())
    }
    @Test fun reportFilenameDoesNotRestrictAServicePathParameter() {
        val tool = ToolDefinition("mcp.test.read", "Remote read", "Read a remote path", mapOf("path" to "string"), riskLevel = RiskLevel.SAFE)
        val a = agent(tools = setOf(tool.id, "files.write"))
        val run = BusinessRun(agentId = a.id, goal = "Read the remote project and save reports/summary.txt")
        val schema = kotlinx.serialization.json.Json.parseToJsonElement(WorkflowModelBrain.decisionSchema(a, run.frames.first(), run, listOf(tool), emptyList())).jsonObject
        assertNull(schema["properties"]!!.jsonObject["args"]!!.jsonObject["properties"])
    }

    @Test fun completedDelegateIsNotRepeatedToBurnThePhoneBattery() = runBlocking {
        val s = store(); s.saveAgent(agent("child")); s.saveAgent(agent().copy(delegates = setOf("child")))
        val id = s.enqueue("manager", "Analyze then report")
        s.updateRun(id) { it.copy(steps = listOf(WorkStep("s1", "child", "agent.result", emptyMap(), "SUCCEEDED", "Actual result: 25.25"))) }
        var offered: List<BusinessAgent>? = null
        val model = WorkflowBrain { _, _, _, _, team -> offered = team; WorkDecision("delegate", agentId = "child", goal = "Repeat") }
        WorkflowEngine(s, registry(), model).tick(id)
        assertTrue(offered!!.isEmpty())
        assertEquals("BLOCKED", s.run(id)!!.status)
    }
}
