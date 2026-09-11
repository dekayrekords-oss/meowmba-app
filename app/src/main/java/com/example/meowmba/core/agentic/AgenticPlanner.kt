package com.example.meowmba.core.agentic

/**
 * Phase 5 model-driven (or heuristic) planner.
 *
 * Given a goal id context + available tools, proposes the next [ProposedToolCall].
 * Default [HeuristicAgenticPlanner] calls [ModelAdapter.complete] for a model
 * attempt event payload, then heuristically picks `ssh_exec` or `noop`.
 */
interface AgenticPlanner {
    fun propose(
        goal: AgenticGoal,
        tools: List<String>,
        profiles: List<ConnectionProfile>,
        recentEvents: List<AgenticEvent>,
    ): ProposedToolCall
}

/**
 * Stub / heuristic planner:
 * 1. Asks [ModelAdapter] for a canned plan (records happen in runtime).
 * 2. If budgets exhausted → noop.
 * 3. If an SSH profile exists and ssh_exec/sshExec is registered → propose
 *    a benign `uname -a` probe (no secrets).
 * 4. Else → noop.
 */
class HeuristicAgenticPlanner(
    private val model: ModelAdapter = StubModelAdapter(),
) : AgenticPlanner {

    /** Last model completion text (for runtime event detail). */
    @Volatile
    var lastModelText: String = ""
        private set

    override fun propose(
        goal: AgenticGoal,
        tools: List<String>,
        profiles: List<ConnectionProfile>,
        recentEvents: List<AgenticEvent>,
    ): ProposedToolCall {
        val prompt = buildPrompt(goal, tools, profiles, recentEvents)
        lastModelText = model.complete(prompt)

        if (goal.budgets.exhausted() || goal.budgets.stepsRemaining() == 0) {
            return ProposedToolCall(
                tool = "noop",
                reason = "budget exhausted steps=${goal.budgets.stepsUsed}/${goal.budgets.maxSteps}",
            )
        }
        if (goal.status == GoalStatus.WAITING_APPROVAL || goal.status == GoalStatus.BLOCKED) {
            return ProposedToolCall(
                tool = "noop",
                reason = "goal status=${goal.status} — waiting",
            )
        }
        if (goal.status == GoalStatus.DONE || goal.status == GoalStatus.CANCELLED) {
            return ProposedToolCall(tool = "noop", reason = "goal already ${goal.status}")
        }

        val toolNames = tools.map { it.lowercase() }.toSet()
        val hasSshTool = "ssh_exec" in toolNames || "sshexec" in toolNames
        val sshProfile = profiles.firstOrNull { it.type == ConnectionProfileType.SSH }

        return if (hasSshTool && sshProfile != null && goal.budgets.sshExecsRemaining() > 0) {
            ProposedToolCall(
                tool = "ssh_exec",
                profileId = sshProfile.id,
                argv = listOf("uname", "-a"),
                reason = "heuristic: probe host via profile=${sshProfile.id} (model stub)",
            )
        } else {
            ProposedToolCall(
                tool = "noop",
                reason = "heuristic: no SSH profile or ssh tool; model=${lastModelText.take(80)}",
            )
        }
    }

    private fun buildPrompt(
        goal: AgenticGoal,
        tools: List<String>,
        profiles: List<ConnectionProfile>,
        recentEvents: List<AgenticEvent>,
    ): String = buildString {
        append("goal id=").append(goal.id).append(" title=").append(goal.title).append('\n')
        append("status=").append(goal.status).append('\n')
        append("budgets steps=")
            .append(goal.budgets.stepsUsed).append('/').append(goal.budgets.maxSteps)
            .append(" tools=").append(goal.budgets.toolCallsUsed).append('/')
            .append(goal.budgets.maxToolCalls)
            .append(" ssh=").append(goal.budgets.sshExecsUsed).append('/')
            .append(goal.budgets.maxSshExecs).append('\n')
        append("tools=").append(tools.joinToString(",")).append('\n')
        // Profile ids / hosts / types only — never credential material.
        append("profiles=")
        append(
            profiles.joinToString(";") { p ->
                "${p.id}:${p.type}:${p.host}"
            },
        )
        append('\n')
        append("recent=")
        append(
            recentEvents.takeLast(5).joinToString(";") { e ->
                "${e.type}:${e.detail.take(60)}"
            },
        )
        append('\n')
        append("Propose next tool (ssh_exec or noop). No secrets.\n")
    }
}

/** Alias kept for call sites that prefer "AgentLoop" naming. */
typealias AgentLoop = AgenticPlanner
