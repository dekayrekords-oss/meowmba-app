package com.example.meowmba.core.agent

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

interface VerifiedPhoneDevice {
    suspend fun observe(): PhoneObservation?
    /** Must recheck the fingerprint immediately before dispatch. */
    suspend fun execute(action: PhoneAction, before: PhoneObservation): String
}
data class VerifiedPhoneStep(val before: PhoneObservation, val action: PhoneAction, val after: PhoneObservation)
data class PhoneRunResult(val verified: Boolean, val message: String, val steps: Int,
    val plannerCalls: Int, val replayed: Boolean, val reason: String)

/** A single sequential action/observation owner. No mutation retries, no speculative
 * execution during planning, and no model-authored summary accepted as evidence. */
class VerifiedPhoneRunner(
    private val device: VerifiedPhoneDevice,
    private val plan: suspend (PhoneObservation, List<String>) -> PhoneAction?,
    private val recipes: PhoneRecipeStore? = null,
    private val maxSteps: Int = 15,
    private val settleTimeoutMs: Long = 1800,
    private val pollMs: Long = 100,
    private val progress: (String) -> Unit = {},
    private val resolveApp: (String) -> String? = { null },
    private val learningAllowed: () -> Boolean = { true },
    private val verificationTimeoutMs: Long = 8000,
    private val metric: suspend (String) -> Unit = {},
) {
    private suspend fun stable(expect: PhoneExpectation? = null, before: PhoneObservation? = null, action: PhoneAction? = null, targetPackage: String? = null): PhoneObservation? =
        withTimeoutOrNull(if (action == null) settleTimeoutMs.coerceIn(200, 5000) else verificationTimeoutMs.coerceIn(200, 15_000)) {
            var previous: PhoneObservation? = null
            var matches = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val next = device.observe()
                currentCoroutineContext().ensureActive()
                matches = if (next != null && previous?.stableWith(next) == true) matches + 1 else 0
                if (matches >= 2 && next != null && (targetPackage == null || next.pkg == targetPackage) && (if (action != null) PhoneVerification.effects(action, before ?: next, next) else expect == null || PhoneVerification.holds(expect, before ?: next, next))) return@withTimeoutOrNull next
                previous = next
                delay(pollMs.coerceIn(20, 300))
            }
            @Suppress("UNREACHABLE_CODE") null
        }

    suspend fun run(goal: String): PhoneRunResult {
        val started = System.nanoTime()
        val trace = mutableListOf<VerifiedPhoneStep>()
        val history = mutableListOf<String>()
        var calls = 0
        var attempts = 0
        var replayed = false
        var candidate: PhoneRecipe? = null
        fun result(ok: Boolean, message: String, reason: String) = PhoneRunResult(ok, message, trace.size, calls, replayed, reason)
        val outcome = withTimeoutOrNull(180_000) {
            var frame = stable() ?: return@withTimeoutOrNull result(false, "The screen has not settled or cannot be read. No completion was verified.", "unstable")
            val search = PhoneSearchRequest.parse(goal)
            val searchPackage = search?.let { resolveApp(it.app) }
            if (search != null && searchPackage == null) return@withTimeoutOrNull result(false, "I could not resolve one installed app for this search. No action was taken.", "unknown_app")
            candidate = search?.takeIf { learningAllowed() }?.let { recipes?.match(it, frame) }
            val replay = candidate?.takeIf { it.promoted }?.bind(search!!)
            if (replay != null && candidate != null && recipes?.beginReplay(candidate!!.id) != true)
                return@withTimeoutOrNull result(false, "The learned workflow could not reserve its recovery record. No action was taken.", "recipe_changed")
            var replayIndex = 0
            while (attempts++ < maxSteps.coerceIn(1, 40)) {
                currentCoroutineContext().ensureActive()
                val fromRecipe = replay != null
                val action = if (fromRecipe) {
                    if (!learningAllowed()) return@withTimeoutOrNull result(false, "Learned workflow stopped because its settings changed.", "revoked")
                    replayed = true
                    replay!!.getOrNull(replayIndex)?.also {
                        if (it.pkg != frame.pkg || it.version != frame.version || it.layout != frame.recipeContext()) {
                            return@withTimeoutOrNull result(false, "The app changed. This learned workflow was stopped; no action was repeated.", "recipe_changed")
                        }
                    }?.action ?: return@withTimeoutOrNull result(false, "The learned workflow has no verified ending.", "recipe_incomplete")
                } else {
                    calls++
                    val planned = withTimeoutOrNull(30_000) { plan(frame, history.takeLast(12)) }
                    currentCoroutineContext().ensureActive()
                    planned ?: return@withTimeoutOrNull result(false, "The planner did not return a valid action in time. No completion was verified.", "planner")
                }
                val fresh = device.observe()
                currentCoroutineContext().ensureActive()
                if (fresh == null || !frame.sameControls(fresh)) {
                    if (fromRecipe) return@withTimeoutOrNull result(false, "The screen changed during replay. The workflow was stopped.", "stale")
                    history += "Screen changed during planning; action discarded."
                    frame = stable() ?: return@withTimeoutOrNull result(false, "The screen is still changing. No action was taken.", "unstable")
                    continue
                }
                frame = fresh
                if (action.type == "blocked") return@withTimeoutOrNull result(false, action.text ?: "Please handle the next step manually.", "handoff")
                if (action.type == "done") {
                    if (searchPackage != null && frame.pkg != searchPackage)
                        return@withTimeoutOrNull result(false, "The requested app is not showing the search result. Completion was not verified.", "wrong_app")
                    val expect = action.expect
                    if (expect == null || expect.kind == "changed" || !PhoneVerification.holds(expect, frame, frame)) {
                        if (fromRecipe) return@withTimeoutOrNull result(false, "The learned result could not be verified.", "verification")
                        history += "Completion rejected: provide an exact currently visible outcome using expect; summary alone is not evidence."
                        continue
                    }
                    if (search != null && !PhoneVerification.searchResult(search, frame, trace, expect)) {
                        if (fromRecipe) return@withTimeoutOrNull result(false, "The current query's search result could not be verified.", "verification")
                        history += "Search completion rejected: entering a query or an old results heading is not evidence of new results. Observe the actual results before finishing."
                        continue
                    }
                    // Say exactly what was observed, never repeat unsupported sent/saved claims.
                    val message = "Verified on screen: ${PhoneVerification.description(expect)}."
                    if (search != null && trace.isNotEmpty() && learningAllowed()) {
                        try { recipes?.record(search, trace, action, completedReplay = replayed) }
                        catch (_: java.io.IOException) { /* The observed task result remains valid when caching fails. */ }
                    }
                    return@withTimeoutOrNull result(true, message, "verified")
                }
                if (action.type == "wait") { frame = stable() ?: return@withTimeoutOrNull result(false, "The screen did not settle.", "unstable"); continue }
                if (action.type == "type" && !PhoneGoalLimits.mayType(goal))
                    return@withTimeoutOrNull result(false, "Stopped before typing: this task did not authorize text entry.", "permission")
                if (action.type == "launch_app" && resolveApp(action.text.orEmpty()) == null)
                    return@withTimeoutOrNull result(false, "The requested app is missing or ambiguous. No launch was attempted.", "unknown_app")
                val grounded = PhoneVerification.ground(action, frame)
                    ?: return@withTimeoutOrNull result(false, "I could not identify one matching control. No action was taken.", "ambiguous")
                val expect = grounded.expect ?: PhoneExpectation("changed")
                if (!expect.valid()) return@withTimeoutOrNull result(false, "The action had an invalid result check. No action was taken.", "contract")
                if (trace.takeLast(3).count { it.before.fingerprint == frame.fingerprint && it.action == grounded } >= 2)
                    return@withTimeoutOrNull result(false, "The same action is repeating. I stopped the workflow.", "loop")
                progress("${if (fromRecipe) "Replaying" else "Checking"} step ${trace.size + 1}")
                currentCoroutineContext().ensureActive()
                if (fromRecipe && !learningAllowed()) return@withTimeoutOrNull result(false, "Learned workflow stopped because its settings changed.", "revoked")
                val dispatch = device.execute(grounded, frame)
                currentCoroutineContext().ensureActive()
                if (dispatch.startsWith("Handoff required:") || dispatch.startsWith("Confirmation cancelled:") || dispatch.startsWith("Confirmed tap dispatched:"))
                    return@withTimeoutOrNull result(false, dispatch, "handoff")
                if (!accepted(dispatch)) return@withTimeoutOrNull result(false, "Action stopped: $dispatch", "dispatch")
                val after = stable(expect, frame, grounded, if (grounded.type == "launch_app") resolveApp(grounded.text.orEmpty()) else null)
                    ?: return@withTimeoutOrNull result(false, "The action was dispatched, but its result could not be verified. Check the screen before retrying; it was not repeated.", "verification")
                trace += VerifiedPhoneStep(frame, grounded.copy(expect = expect), after)
                history += "Verified step ${trace.size}: ${PhoneVerification.description(expect)}."
                frame = after
                replayIndex++
            }
            result(false, "Reached the action budget without verified completion.", "budget")
        } ?: result(false, "The task timed out. Check the screen before retrying; an action may already have completed.", "timeout")
        currentCoroutineContext().ensureActive()
        if (!outcome.verified && learningAllowed()) candidate?.let { runCatching { recipes?.suspendRecipe(it.id) } }
        try { metric("elapsed_ms=${(System.nanoTime() - started) / 1_000_000} verified=${outcome.verified} steps=${outcome.steps} planning_turns=${outcome.plannerCalls} replay=${outcome.replayed} reason=${outcome.reason}") }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { /* Diagnostics cannot invalidate an observed outcome. */ }
        return outcome
    }
    private fun accepted(result: String) = listOf("Tapped ", "Typed into ", "Scrolled ", "Swiped ", "Performed ", "launched ").any(result::startsWith)
}
