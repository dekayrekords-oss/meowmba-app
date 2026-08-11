package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.agent.prime.Goal
import com.example.meowmba.core.agent.prime.HarnessState
import com.example.meowmba.core.agent.prime.HarnessStore
import com.example.meowmba.core.agent.prime.RefineEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Prime-Agent-style harness, exposed to the companion.
 *
 * Three verbs, matching the originals: `/refine`, `/goal`, and reading the
 * state back. All SAFE — none of them touch the device, the network or a file
 * outside the app's own harness document. What they change is how the
 * companion DECIDES, never what it is permitted to do.
 */

/**
 * `prime.refine` — review the trajectory and keep what the evidence supports.
 *
 * Reads the audit log through [trajectory] rather than owning a database
 * handle, so the reviewing logic stays pure and testable and this class stays
 * a thin adapter.
 */
class PrimeRefineTool(
    private val store: HarnessStore,
    private val trajectory: suspend () -> List<RefineEngine.Step>,
    private val now: () -> Long = { System.currentTimeMillis() },
) : Tool {

    override val definition = ToolDefinition(
        id = "prime.refine",
        displayName = "Learn from what just happened",
        description = "Review recent actions and remember what repeatedly worked or " +
            "failed on this device. Use when something has gone wrong more than once, " +
            "or when the user asks you to learn from a mistake. Produces guidance for " +
            "yourself only — it cannot change your permissions.",
        inputSchema = emptyMap(),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 10_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            val steps = runCatching { trajectory() }.getOrDefault(emptyList())
            if (steps.size < RefineEngine.MIN_OCCURRENCES) {
                return@withContext ToolResult.Success(
                    "Not enough history yet to learn anything honestly " +
                        "(${steps.size} steps recorded)."
                )
            }
            val t = now()
            val before = RefineEngine.expire(store.load(), t)
            val candidates = RefineEngine.refine(steps, t)
            val after = RefineEngine.merge(before, candidates)
            store.save(after)

            val added = after.lessons.filter { l -> before.lessons.none { it.id == l.id } }
            val expired = before.lessons.size - (after.lessons.size - added.size)
            when {
                added.isNotEmpty() -> ToolResult.Success(
                    "Learned ${added.size} thing(s): " +
                        added.joinToString("; ") { it.text }
                )
                expired > 0 -> ToolResult.Success(
                    "Nothing new. Forgot $expired stale lesson(s) whose evidence aged out."
                )
                else -> ToolResult.Success(
                    "Reviewed ${steps.size} steps; nothing recurred often enough to be worth remembering."
                )
            }
        }
}

/**
 * `prime.goal` — the standing objective, kept across turns and restarts.
 *
 * Singular by design: a queue of goals is a task list, and the orchestrator
 * already is one. This is the thing to keep in mind while doing everything
 * else.
 */
class PrimeGoalTool(
    private val store: HarnessStore,
    private val now: () -> Long = { System.currentTimeMillis() },
) : Tool {

    override val definition = ToolDefinition(
        id = "prime.goal",
        displayName = "Standing goal",
        description = "Set, note progress on, read, or clear a standing goal the " +
            "companion keeps in mind across conversations. " +
            "action: set | note | get | clear. text: the goal, or the progress note.",
        inputSchema = mapOf(
            "action" to "set | note | get | clear",
            "text" to "the goal or progress note (not needed for get/clear)",
        ),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 5_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"]?.trim()?.lowercase() ?: "get"
            val text = args["text"]?.trim().orEmpty()
            val state = store.load()
            when (action) {
                "set" -> {
                    if (text.isBlank()) {
                        return@withContext ToolResult.Failure("Missing 'text' — what is the goal?")
                    }
                    store.save(
                        state.copy(goal = Goal(text.take(Goal.MAX_TEXT), now()))
                    )
                    ToolResult.Success("Standing goal set: ${text.take(Goal.MAX_TEXT)}")
                }
                "note" -> {
                    val goal = state.goal
                        ?: return@withContext ToolResult.Failure("No standing goal to note progress on.")
                    if (text.isBlank()) {
                        return@withContext ToolResult.Failure("Missing 'text' — what progress?")
                    }
                    store.save(state.copy(goal = goal.withNote(text)))
                    ToolResult.Success("Noted against \"${goal.text}\": $text")
                }
                "clear" -> {
                    store.save(state.copy(goal = null))
                    ToolResult.Success("Standing goal cleared.")
                }
                else -> {
                    val goal = state.goal
                        ?: return@withContext ToolResult.Success("No standing goal.")
                    ToolResult.Success(
                        buildString {
                            append("Goal: ").append(goal.text)
                            if (goal.progressNotes.isNotEmpty()) {
                                append("\nProgress: ").append(goal.progressNotes.joinToString("; "))
                            }
                        }
                    )
                }
            }
        }
}

/**
 * `prime.state` — read back, or forget, what the harness has learned.
 *
 * The honesty half of a self-improving system: whatever it decided about
 * itself has to be inspectable and revocable in the same breath the user asks.
 */
class PrimeStateTool(private val store: HarnessStore) : Tool {

    override val definition = ToolDefinition(
        id = "prime.state",
        displayName = "What have you learned",
        description = "Read back everything the companion has learned about itself on " +
            "this device, or forget all of it. action: get | forget.",
        inputSchema = mapOf("action" to "get | forget"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 5_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            if (args["action"]?.trim()?.lowercase() == "forget") {
                store.clear()
                return@withContext ToolResult.Success("Forgot everything I had learned about myself.")
            }
            val state: HarnessState = store.load()
            if (state.lessons.isEmpty() && state.goal == null) {
                return@withContext ToolResult.Success("I haven't learned anything about myself yet.")
            }
            ToolResult.Success(
                state.promptBlock() ?: "I haven't learned anything about myself yet."
            )
        }
}
