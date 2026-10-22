package com.example.meowmba

import com.example.meowmba.core.agent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class VerifiedPhoneRunnerTest {
    private val search = PhoneSelector(viewId = "com.search:id/query", role = "edit")
    private fun frame(value: String = "", version: Long = 1) = PhoneObservation("com.search", version, "fingerprint:$value",
        listOf(PhoneControl(1, "Search", "com.search:id/query", "edit", value)),
        if (value.isEmpty()) listOf("Home") else listOf("Search results", value), "screen")
    private inner class Device : VerifiedPhoneDevice {
        var state = frame()
        var writes = 0
        var acknowledgeOnly = false
        override suspend fun observe() = state
        override suspend fun execute(action: PhoneAction, before: PhoneObservation): String {
            assertTrue(before.stableWith(state))
            writes++
            if (!acknowledgeOnly) state = frame(action.value.orEmpty(), state.version)
            return "Typed into Search."
        }
    }
    private fun runner(d: Device, query: String, store: PhoneRecipeStore? = null, allowed: () -> Boolean = { true }) =
        VerifiedPhoneRunner(d, { f, _ -> if (f.controls.first().value != query) PhoneAction("type", value = query, selector = search)
            else PhoneAction("done", expect = PhoneExpectation("visible_text", f.pkg, query)) },
            store, maxSteps = 6, pollMs = 20, settleTimeoutMs = 200, learningAllowed = allowed, resolveApp = { "com.search" })

    @Test fun summaryAloneCannotFinishAndBudgetIncludesRejectedFinishes() = runTest {
        val d = Device()
        val result = VerifiedPhoneRunner(d, { _, _ -> PhoneAction("done", "Sent it!") }, maxSteps = 2, pollMs = 20).run("send a message")
        assertFalse(result.verified); assertEquals("budget", result.reason)
        assertEquals(2, result.plannerCalls); assertEquals(0, d.writes)
    }
    @Test fun dispatchAcknowledgmentIsNotAResultAndNeverRetriesMutation() = runTest {
        val d = Device().apply { acknowledgeOnly = true }
        val r = runner(d, "birds").run("search for birds in Search")
        assertFalse(r.verified); assertEquals("verification", r.reason); assertEquals(1, d.writes)
    }
    @Test fun observedResultReplacesUnsupportedModelSummary() = runTest {
        val d = Device().apply { state = frame("birds") }
        val r = VerifiedPhoneRunner(d, { _, _ -> PhoneAction("done", "Posted and paid!", expect = PhoneExpectation("visible_text", text = "birds")) }, pollMs = 20).run("read birds")
        assertTrue(r.verified); assertFalse(r.message.contains("paid")); assertEquals(0, d.writes)
    }
    @Test fun stalePlannerDecisionIsDiscardedBeforeExecution() = runTest {
        val d = Device(); var calls = 0
        val r = VerifiedPhoneRunner(d, { _, _ ->
            calls++
            if (calls == 1) { d.state = frame("changed elsewhere"); PhoneAction("type", index = 1, value = "bad") }
            else PhoneAction("blocked", "Screen changed")
        }, pollMs = 20, resolveApp = { "com.search" }).run("search for birds in Search")
        assertFalse(r.verified); assertEquals(0, d.writes); assertEquals(2, calls)
    }
    @Test fun cancellingNonCooperativePlannerCannotDispatchLateAction() = runTest {
        val d = Device(); val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val job = launch {
            VerifiedPhoneRunner(d, { _, _ -> started.complete(Unit); withContext(NonCancellable) { release.await() }; PhoneAction("type", index = 1, value = "late") }, pollMs = 20).run("search")
        }
        started.await(); job.cancel(); release.complete(Unit); job.join()
        assertEquals(0, d.writes)
    }
    @Test fun ambiguousDuplicateSelectorNeverChoosesFirstMatch() = runTest {
        val d = Device().apply { state = state.copy(controls = listOf(state.controls[0], state.controls[0].copy(index = 2))) }
        val r = runner(d, "birds").run("search for birds in Search")
        assertEquals("ambiguous", r.reason); assertEquals(0, d.writes)
    }
    @Test fun changedAloneCannotVerifyCompletion() = runTest {
        val d = Device()
        val r = VerifiedPhoneRunner(d, { _, _ -> PhoneAction("done", expect = PhoneExpectation("changed")) }, maxSteps = 1, pollMs = 20).run("task")
        assertFalse(r.verified)
    }
    @Test fun stableScreenContinuesWithoutFixedFifteenHundredMillisecondDelay() = runTest {
        val d = Device()
        val r = runner(d, "birds").run("search for birds in Search")
        assertTrue(r.verified); assertTrue(testScheduler.currentTime < 1500)
    }
    @Test fun searchRecipesPromoteOnlyAfterThreeRealVerifiedRunsAndReplayNewQuery() = runTest {
        val dir = Files.createTempDirectory("recipes-test").toFile()
        try {
            val file = java.io.File(dir, "recipes.json")
            for (query in listOf("birds", "fish", "dogs")) {
                val d = Device(); val store = PhoneRecipeStore(file)
                val r = runner(d, query, store).run("search for $query in Search")
                assertTrue(r.verified); assertFalse(r.replayed); assertEquals(2, r.plannerCalls)
            }
            val text = file.readText()
            assertFalse(text.contains("birds")); assertFalse(text.contains("fish")); assertFalse(text.contains("dogs"))
            val d = Device(); val query = "a \"quoted\" query"
            val r = runner(d, query, PhoneRecipeStore(file)).run("search for $query in Search")
            assertTrue(r.verified); assertTrue(r.replayed); assertEquals(0, r.plannerCalls); assertEquals(query, d.state.controls[0].value)
        } finally { dir.deleteRecursively() }
    }
    @Test fun failingReplaySuspendsRecipeWithoutRetryingTheAction() = runTest {
        val dir = Files.createTempDirectory("recipes-test").toFile()
        try {
            val file = java.io.File(dir, "recipes.json"); val store = PhoneRecipeStore(file)
            repeat(3) { assertTrue(runner(Device(), "birds", store).run("search for birds in Search").verified) }
            val d = Device().apply { acknowledgeOnly = true }
            val r = runner(d, "fish", store).run("search for fish in Search")
            assertFalse(r.verified); assertTrue(r.replayed); assertEquals(1, d.writes)
            assertNull(PhoneRecipeStore(file).match(PhoneSearchRequest("search", "fish"), frame()))
        } finally { dir.deleteRecursively() }
    }
    @Test fun changedAppVersionRequiresPlanningAgain() = runTest {
        val dir = Files.createTempDirectory("recipes-test").toFile()
        try {
            val store = PhoneRecipeStore(java.io.File(dir, "recipes.json"))
            repeat(3) { runner(Device(), "birds", store).run("search for birds in Search") }
            val d = Device().apply { state = frame(version = 2) }
            val r = runner(d, "fish", store).run("search for fish in Search")
            assertFalse(r.replayed); assertTrue(r.plannerCalls > 0)
        } finally { dir.deleteRecursively() }
    }
    @Test fun corruptRecipesDoNotPreventNormalPlanning() = runTest {
        val dir = Files.createTempDirectory("recipes-test").toFile()
        try {
            val file = java.io.File(dir, "recipes.json").apply { writeText("broken") }
            assertTrue(runner(Device(), "birds", PhoneRecipeStore(file)).run("search for birds in Search").verified)
        } finally { dir.deleteRecursively() }
    }
    @Test fun learningDisabledDoesNotSaveQueriesOrRecipes() = runTest {
        val dir = Files.createTempDirectory("recipes-test").toFile()
        try {
            val file = java.io.File(dir, "recipes.json")
            assertTrue(runner(Device(), "birds", PhoneRecipeStore(file), { false }).run("search for birds in Search").verified)
            assertFalse(file.exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun passwordAndCompoundRequestsAreNotLearnedSearches() {
        assertNull(PhoneSearchRequest.parse("search for password in Search"))
        assertNull(PhoneSearchRequest.parse("search for birds in Search and send results"))
        assertNull(PhoneSearchRequest.parse("draft a message to Bob"))
    }
    @Test fun invalidExpectationAndSelectorAreRejectedAtParserBoundary() {
        assertNull(PhoneAgentLoop.parseAction("""{"action":"done","expect":{"kind":"nothing"}}"""))
        assertNull(PhoneAgentLoop.parseAction("""{"action":"tap","selector":{}}"""))
        assertNull(PhoneAgentLoop.parseAction("""{"action":"done","expect":{"kind":"visible_text","text":""}}"""))
        val action = PhoneAgentLoop.parseAction("""{"action":"type","selector":{"role":"edit"},"value":"hi"}""")!!
        assertEquals("edit", action.selector!!.role)
    }
    @Test fun aMissingViewIdNeverFallsBackToTheLabel() {
        val f = frame()
        assertNull(f.find(PhoneSelector(viewId = "missing", label = "Search")))
        assertNull(PhoneVerification.ground(PhoneAction("tap", selector = PhoneSelector(viewId = "missing", label = "Search")), f))
    }
    @Test fun phoneSearchRoutesAreReachableAndNativeLinksStayAvailable() {
        val router = com.example.meowmba.core.agent.runtime.AgentRouter
        assertEquals("phone.run_goal", router.route("use my phone to search for birds in YouTube")?.toolId)
        assertEquals("search for birds in YouTube", router.route("use my phone to search for birds in YouTube")?.args?.get("goal"))
        assertEquals("web.search", router.route("search for birds in WorkApp")?.toolId)
        assertEquals("web.search", router.route("search for restaurants in Phoenix")?.toolId)
        assertEquals("phone.open_url", router.route("search for birds in YouTube")?.toolId)
        assertEquals("web.search", router.route("search the web for birds")?.toolId)
    }
    @Test fun protectedFieldsAndUnrelatedExistingDraftsAreNotTypedInto() {
        val password = frame().copy(controls = listOf(PhoneControl(1, "Password", "app:id/password", "edit", protected = true)))
        assertNull(PhoneVerification.ground(PhoneAction("type", index = 1, value = "secret"), password))
        val draft = frame().copy(controls = listOf(PhoneControl(1, "Message", "app:id/message", "edit", value = "existing draft")))
        assertNull(PhoneVerification.ground(PhoneAction("type", index = 1, value = "overwrite"), draft))
        assertNotNull(PhoneVerification.ground(PhoneAction("type", index = 1, value = "new search"), frame("old search")))
    }
    @Test fun navigationOnlyGoalCannotTypeEvenWithValidSelector() = runTest {
        val d = Device()
        val r = runner(d, "birds").run("navigation only, do not type")
        assertFalse(r.verified); assertEquals("permission", r.reason); assertEquals(0, d.writes)
    }
    @Test fun observedSearchInTheWrongAppIsNotCompletion() = runTest {
        val d = Device().apply { state = frame("birds") }
        val r = VerifiedPhoneRunner(d, { _, _ -> PhoneAction("done", expect = PhoneExpectation("visible_text", text = "birds")) },
            pollMs = 20, resolveApp = { "com.other" }).run("search for birds in Other")
        assertFalse(r.verified); assertEquals("wrong_app", r.reason)
    }
    @Test fun disabledLearningDuringReplayStopsBeforeNextAction() = runTest {
        val dir = Files.createTempDirectory("recipes-test").toFile()
        try {
            val store = PhoneRecipeStore(java.io.File(dir, "recipes.json"))
            repeat(3) { runner(Device(), "birds", store).run("search for birds in Search") }
            val d = Device(); var allowed = true
            val proxy = object : VerifiedPhoneDevice {
                override suspend fun observe() = d.observe()
                override suspend fun execute(action: PhoneAction, before: PhoneObservation): String {
                    allowed = false; return d.execute(action, before)
                }
            }
            val r = VerifiedPhoneRunner(proxy, { _, _ -> error("Replay should not call planner") }, store,
                pollMs = 20, learningAllowed = { allowed }, resolveApp = { "com.search" }).run("search for fish in Search")
            assertFalse(r.verified); assertEquals("revoked", r.reason); assertEquals(1, d.writes)
        } finally { dir.deleteRecursively() }
    }
    @Test fun aPlannerTimeoutCannotReachTheDevice() = runTest {
        val d = Device()
        val r = VerifiedPhoneRunner(d, { _, _ -> delay(40_000); PhoneAction("type", index = 1, value = "late") }, pollMs = 20).run("search")
        assertFalse(r.verified); assertEquals("planner", r.reason); assertEquals(0, d.writes)
    }

    @Test fun noninteractiveClockChangeDoesNotDiscardAStillValidControl() = runTest {
        val d = Device(); var calls = 0
        val r = VerifiedPhoneRunner(d, { _, _ ->
            if (++calls == 1) {
                d.state = d.state.copy(visibleText = listOf("Clock 12:01"))
                PhoneAction("type", value = "birds", selector = search)
            } else PhoneAction("done", expect = PhoneExpectation("visible_text", text = "birds"))
        }, pollMs = 20, resolveApp = { "com.search" }).run("search for birds in Search")
        assertTrue(r.verified); assertEquals(2, calls); assertEquals(1, d.writes)
    }
    @Test fun diagnosticsFailureCannotTurnVerifiedOutcomeIntoFailure() = runTest {
        val d = Device().apply { state = frame("birds") }
        val r = VerifiedPhoneRunner(d, { _, _ -> PhoneAction("done", expect = PhoneExpectation("visible_text", text = "birds")) },
            pollMs = 20, metric = { error("disk unavailable") }).run("read birds")
        assertTrue(r.verified)
    }
    @Test fun executedActionsCannotExceedTheBudget() = runTest {
        val d = Device(); var n = 0
        val r = VerifiedPhoneRunner(d, { _, _ -> PhoneAction("type", selector = search, value = "query${++n}") },
            pollMs = 20, maxSteps = 2).run("search")
        assertFalse(r.verified); assertEquals("budget", r.reason); assertEquals(2, d.writes)
    }

}
