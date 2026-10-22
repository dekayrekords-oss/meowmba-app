package com.example.meowmba

import com.example.meowmba.core.agent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

class AgentHardeningTest {
    private val selector = PhoneSelector(viewId = "com.search:id/query", role = "edit")
    private fun frame(query: String = "", texts: List<String> = listOf("Home")) = PhoneObservation("com.search", 1, "state:$query",
        listOf(PhoneControl(1, "Search", "com.search:id/query", "edit", query)), texts, "screen")
    private class Device(var frame: PhoneObservation, val change: (PhoneAction, PhoneObservation) -> PhoneObservation) : VerifiedPhoneDevice {
        var writes = 0
        override suspend fun observe() = frame
        override suspend fun execute(action: PhoneAction, before: PhoneObservation): String { writes++; frame = change(action, frame); return "Typed into Search." }
    }
    @Test fun searchCannotFinishBecauseAnAppOrSearchFieldIsMerelyOpen() = runTest {
        val d = Device(frame()) { _, f -> f }
        val r = VerifiedPhoneRunner(d, { _, _ -> PhoneAction("done", expect = PhoneExpectation("package", pkg = "com.search")) },
            maxSteps = 2, pollMs = 20, resolveApp = { "com.search" }).run("search for birds in Search")
        assertFalse(r.verified)
    }
    @Test fun staleResultsHeadingIsNotEvidenceForANewQuery() = runTest {
        val d = Device(frame("old", listOf("Search results", "old results"))) { a, f -> f.copy(fingerprint = "new", controls = f.controls.map { it.copy(value = a.value!!) }) }
        var call = 0
        val r = VerifiedPhoneRunner(d, { _, _ -> if (call++ == 0) PhoneAction("type", value = "birds", selector = selector)
            else PhoneAction("done", expect = PhoneExpectation("visible_text", text = "Search results")) },
            maxSteps = 3, pollMs = 20, resolveApp = { "com.search" }).run("search for birds in Search")
        assertFalse(r.verified); assertEquals(1, d.writes)
    }
    @Test fun typingDoesNotDiscardTheSuppliedResultCheck() = runTest {
        val d = Device(frame()) { a, _ -> frame(a.value!!) }
        val r = VerifiedPhoneRunner(d, { _, _ -> PhoneAction("type", value = "birds", selector = selector,
            expect = PhoneExpectation("visible_text", text = "Loaded results")) },
            maxSteps = 2, pollMs = 20, settleTimeoutMs = 200).run("search")
        assertEquals("verification", r.reason); assertEquals(1, d.writes)
    }
    @Test fun missingTargetDoesNotSelectAControlWhoseIdIsAlsoNull() {
        val f = frame().copy(controls = listOf(PhoneControl(1, "Search", null, "edit")))
        assertNull(PhoneVerification.ground(PhoneAction("type", value = "oops"), f))
    }
    @Test fun corruptedRecipeCannotSupplyAnEarlyFinishOrNoQueryEntry() {
        val dir = Files.createTempDirectory("recipe-corruption").toFile()
        try {
            val file = File(dir, "recipes.json")
            file.writeText("""{"version":2,"recipes":[{"id":"aaaaaaaaaaaaaaaaaaaaaaaa","app":"search","startPackage":"com.search","startVersion":1,"greenRuns":3,"steps":[{"pkg":"com.search","version":1,"action":{"type":"done","expect":{"kind":"package","pkg":"com.search"}}},{"pkg":"com.search","version":1,"action":{"type":"done","expect":{"kind":"package","pkg":"com.search"}}}]}]}""")
            assertNull(PhoneRecipeStore(file).match(PhoneSearchRequest("search", "birds"), frame()))
        } finally { dir.deleteRecursively() }
    }
    @Test fun roleOnlySelectorCannotBeSavedForAutomaticTypingOnAnotherScreen() = runTest {
        val dir = Files.createTempDirectory("recipe-role").toFile()
        try {
            val store = PhoneRecipeStore(File(dir, "recipes.json"))
            repeat(3) {
                val before = frame().copy(controls = listOf(PhoneControl(1, "Message", null, "edit")))
                val after = before.copy(fingerprint = "after", controls = before.controls.map { it.copy(value = "birds") }, visibleText = listOf("birds"))
                val action = PhoneAction("type", value = "birds", selector = PhoneSelector(role = "edit"), expect = PhoneExpectation("editable_equals", text = "birds", selector = PhoneSelector(role = "edit")))
                store.record(PhoneSearchRequest("search", "birds"), listOf(VerifiedPhoneStep(before, action, after)), PhoneAction("done", expect = PhoneExpectation("visible_text", text = "birds")))
            }
            assertNull(store.match(PhoneSearchRequest("search", "birds"), frame()))
        } finally { dir.deleteRecursively() }
    }
    @Test fun aChangedClockDoesNotMakeOldQueryTextIntoNewSearchEvidence() {
        val before = frame("old", listOf("birds", "Clock 12:00"))
        val after = frame("birds", listOf("birds", "Clock 12:01"))
        val action = PhoneAction("type", value = "birds", selector = selector, expect = PhoneExpectation("editable_equals", text = "birds", selector = selector))
        assertFalse(PhoneVerification.searchResult(PhoneSearchRequest("search", "birds"), after, listOf(VerifiedPhoneStep(before, action, after)), PhoneExpectation("visible_text", text = "birds")))
    }
    @Test fun unresolvedRequestedAppStopsBeforePlanningOrActing() = runTest {
        val d = Device(frame()) { _, f -> f }
        val r = VerifiedPhoneRunner(d, { _, _ -> error("must not plan against an unresolved app") }, pollMs = 20)
            .run("search for birds in MissingApp")
        assertEquals("unknown_app", r.reason); assertEquals(0, d.writes)
    }
    private fun learn(store: PhoneRecipeStore) {
        repeat(3) {
            val before = frame()
            val after = frame("birds", listOf("birds"))
            val action = PhoneAction("type", value = "birds", selector = selector, expect = PhoneExpectation("editable_equals", text = "birds", selector = selector))
            store.record(PhoneSearchRequest("search", "birds"), listOf(VerifiedPhoneStep(before, action, after)), PhoneAction("done", expect = PhoneExpectation("visible_text", text = "birds")))
        }
    }
    @Test fun interruptedReplayCannotBeSilentlyResumedAfterRestart() = runTest {
        val dir = Files.createTempDirectory("recipe-restart").toFile()
        try {
            val file = File(dir, "recipes.json"); val store = PhoneRecipeStore(file); learn(store)
            val started = CompletableDeferred<Unit>()
            val d = object : VerifiedPhoneDevice {
                override suspend fun observe() = frame()
                override suspend fun execute(action: PhoneAction, before: PhoneObservation): String { started.complete(Unit); awaitCancellation() }
            }
            val job = launch { VerifiedPhoneRunner(d, { _, _ -> error("must replay") }, store, pollMs = 20, resolveApp = { "com.search" }).run("search for fish in Search") }
            started.await(); job.cancelAndJoin()
            assertTrue(file.readText().contains("inFlight"))
            assertNull(PhoneRecipeStore(file).match(PhoneSearchRequest("search", "fish"), frame()))
        } finally { dir.deleteRecursively() }
    }
    @Test fun delayedResultsCanArriveAfterTheOldOnePointFiveSecondLimit() = runTest {
        val d = Device(frame()) { a, _ -> frame(a.value!!) }
        var plans = 0
        val r = VerifiedPhoneRunner(d, { _, _ -> if (plans++ == 0) {
            launch { delay(2500); d.frame = frame("birds", listOf("Loaded results", "birds")) }
            PhoneAction("type", value = "birds", selector = selector, expect = PhoneExpectation("visible_text", text = "Loaded results"))
        } else PhoneAction("done", expect = PhoneExpectation("visible_text", text = "birds")) },
            pollMs = 20, resolveApp = { "com.search" }).run("search for birds in Search")
        assertTrue(r.verified); assertEquals(1, d.writes)
    }

    @Test fun accountResetAndPermissionControlsCannotBeClassifiedAsRoutineTaps() {
        for (label in listOf("Authorize", "Grant access", "Factory reset", "Erase all", "Wipe device")) assertTrue(label, PhoneActionSafety.needsHandoff(label))
        assertTrue(PhoneActionSafety.screenNeedsManualControl("com.android.permissioncontroller", listOf("Continue")))
        assertTrue(PhoneActionSafety.screenNeedsManualControl("com.shop", listOf("Checkout", "Continue")))
        assertFalse(PhoneActionSafety.screenNeedsManualControl("com.search", listOf("How to delete an account")))
    }
    @Test fun emptySelectorsAndContradictoryIndicesAreRejected() {
        assertNull(frame().find(PhoneSelector()))
        assertNull(PhoneAgentLoop.parseAction("""{"action":"tap","text":"2","index":1}"""))
    }

    @Test fun aDifferentScreenInTheSameAppCannotReuseASearchRecipe() {
        val dir = Files.createTempDirectory("recipe-context").toFile()
        try {
            val store = PhoneRecipeStore(File(dir, "recipes.json")); learn(store)
            assertNotNull(store.match(PhoneSearchRequest("search", "birds"), frame()))
            assertNull(store.match(PhoneSearchRequest("search", "birds"), frame(texts = listOf("Orders"))))
            assertEquals(frame("private query A").recipeContext(), frame("private query B").recipeContext())
        } finally { dir.deleteRecursively() }
    }
    @Test fun manualPaymentHandoffCannotTurnIntoASocialApproval() {
        assertFalse(PhoneActionSafety.mayOfferSocialConfirmation("Handoff required: this payment, account or permission screen needs direct manual control.", "send payment", "tap_index"))
        assertTrue(PhoneActionSafety.mayOfferSocialConfirmation(PhoneActionSafety.SOCIAL_REVIEW, "send message", "tap_index"))
        assertFalse(PhoneActionSafety.mayOfferSocialConfirmation(PhoneActionSafety.SOCIAL_REVIEW, "draft a message", "tap_index"))
    }

}
