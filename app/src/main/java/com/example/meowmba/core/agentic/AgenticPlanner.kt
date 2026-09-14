package com.example.meowmba.core.agentic

/**
 * Phase 5+ model-driven (or heuristic) planner.
 *
 * Given a goal id context + available tools, proposes the next [ProposedToolCall].
 * [HeuristicAgenticPlanner] calls [ModelAdapter.complete], then prefers
 * [AgenticToolCallParser] JSON / tool_calls output; falls back to heuristics.
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
 * Model-aware planner:
 * 1. Asks [ModelAdapter] for a plan (HTTP or stub).
 * 2. Parses JSON tool_calls / function-call style via [AgenticToolCallParser].
 * 3. Falls back to heuristic ssh_exec / local_shell / noop when parse empty.
 * 4. Respects budgets and waiting/blocked statuses.
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
        lastModelText = ""

        if (goal.budgets.exhausted() || goal.budgets.stepsRemaining() == 0) {
            return ProposedToolCall(
                tool = "noop",
                reason = "budget exhausted steps=${goal.budgets.stepsUsed}/${goal.budgets.maxSteps}",
            )
        }
        if ((goal.status == GoalStatus.WAITING_APPROVAL && !goal.approvalGranted) || goal.status == GoalStatus.BLOCKED) {
            return ProposedToolCall(
                tool = "noop",
                reason = "goal status=${goal.status} — waiting",
            )
        }
        if (goal.status == GoalStatus.DONE || goal.status == GoalStatus.CANCELLED) {
            return ProposedToolCall(tool = "noop", reason = "goal already ${goal.status}")
        }

        val toolNames = tools.map { it.lowercase().replace('-', '_') }.toSet()
        // An explicit Computer command is already a complete user request. A model
        // must not replace it with a generic SSH probe or a configured stub response.
        if ("computer_exec" in toolNames && Regex("(?i)^(run on computer[:\\s]+|computer:)" ).containsMatchIn(goal.title.trim())) {
            return ProposedToolCall(tool = "computer_exec", argv = shellArgvFromTitle(goal.title),
                reason = "explicit Computer command")
        }
        val prompt = buildPrompt(goal, tools, profiles, recentEvents)
        lastModelText = model.complete(prompt)
        val sshProfile = profiles.firstOrNull { it.type == ConnectionProfileType.SSH }
        val httpsProfile = profiles.firstOrNull { it.type == ConnectionProfileType.HTTPS_RUNTIME }
        val fallbackProfileId = sshProfile?.id ?: httpsProfile?.id

        val parsed = AgenticToolCallParser.parseFirst(lastModelText, fallbackProfileId)
        if (parsed != null) {
            val normalized = parsed.tool.lowercase().replace('-', '_')
            if (normalized == "noop" || normalized in toolNames ||
                normalized == "sshexec" && "ssh_exec" in toolNames
            ) {
                return parsed.copy(
                    tool = when (normalized) {
                        "sshexec" -> "ssh_exec"
                        else -> parsed.tool
                    },
                    profileId = parsed.profileId ?: fallbackProfileId,
                    argv = parsed.argv.ifEmpty {
                        when (normalized) {
                            "ssh_exec", "sshexec", "https_runtime", "local_shell",
                            "computer_exec", "computerexec",
                            ->
                                listOf("uname", "-a")
                            else -> emptyList()
                        }
                    },
                    reason = "model-parsed: ${parsed.reason}".take(160),
                )
            }
        }

        val hasSshTool = "ssh_exec" in toolNames || "sshexec" in toolNames
        val hasLocal = "local_shell" in toolNames
        val hasComputer = "computer_exec" in toolNames || "computerexec" in toolNames
        val hasHttps = "https_runtime" in toolNames
        val shellishTitle = looksLikeShellCommand(goal.title)

        return when {
            hasComputer && shellishTitle ->
                ProposedToolCall(
                    tool = "computer_exec",
                    argv = shellArgvFromTitle(goal.title),
                    reason = "heuristic: shell-like goal → computer_exec",
                )
            hasSshTool && sshProfile != null && goal.budgets.sshExecsRemaining() > 0 &&
                !shellishTitle ->
                ProposedToolCall(
                    tool = "ssh_exec",
                    profileId = sshProfile.id,
                    argv = listOf("uname", "-a"),
                    reason = "heuristic: probe SSH profile=${sshProfile.id}",
                )
            hasHttps && httpsProfile != null && goal.budgets.sshExecsRemaining() > 0 &&
                !shellishTitle ->
                ProposedToolCall(
                    tool = "https_runtime",
                    profileId = httpsProfile.id,
                    argv = listOf("uname", "-a"),
                    reason = "heuristic: probe HTTPS_RUNTIME profile=${httpsProfile.id}",
                )
            hasComputer ->
                ProposedToolCall(
                    tool = "computer_exec",
                    argv = listOf("uname", "-a"),
                    reason = "heuristic: computer_exec probe",
                )
            hasLocal ->
                ProposedToolCall(
                    tool = "local_shell",
                    argv = listOf("uname", "-a"),
                    reason = "heuristic: local_shell probe",
                )
            else ->
                ProposedToolCall(
                    tool = "noop",
                    reason = "heuristic: no usable tool/profile; model=${lastModelText.take(80)}",
                )
        }
    }

    companion object {
        private val SHELL_FIRST = setOf(
            "ls", "pwd", "cd", "cat", "head", "tail", "echo", "uname", "whoami",
            "id", "df", "free", "ps", "top", "git", "ssh", "scp", "curl", "wget",
            "python", "python3", "pip", "npm", "node", "java", "javac", "mkdir",
            "touch", "cp", "mv", "rm", "chmod", "chown", "grep", "find", "which",
            "env", "export", "apt", "pkg", "proot", "termux", "bash", "sh",
        )

        fun looksLikeShellCommand(text: String): Boolean {
            val t = text.trim()
            if (t.isEmpty()) return false
            val lower = t.lowercase()
            if (lower.startsWith("run on computer")) return true
            if (lower.startsWith("computer:")) return true
            if (lower.startsWith("$") || lower.startsWith("#")) return true
            val first = t.split(Regex("\\s+")).firstOrNull()?.substringAfterLast('/')?.lowercase()
                ?: return false
            return first in SHELL_FIRST
        }

        fun shellArgvFromTitle(title: String): List<String> {
            var t = title.trim()
            val m = Regex("(?i)^(?:run on computer[:\\s]+|computer:\\s*)(.+)$").find(t)
            if (m != null) t = m.groupValues[1].trim()
            if (t.startsWith("$")) t = t.drop(1).trim()
            if (t.isEmpty()) return listOf("uname", "-a")
            return listOf("sh", "-c", t)
        }
    }

    private fun buildPrompt(
        goal: AgenticGoal,
        tools: List<String>,
        profiles: List<ConnectionProfile>,
        recentEvents: List<AgenticEvent>,
    ): String = buildString {
        append("You are the Meowmba agentic planner. Reply with JSON only.\n")
        append("Preferred shapes:\n")
        append("""{"tool":"ssh_exec","profileId":"<id>","argv":["uname","-a"],"reason":"..."}""")
        append('\n')
        append("""{"tool":"local_shell","argv":["uname","-a"]}""")
        append('\n')
        append("""{"tool":"computer_exec","argv":["ls","-la"]}""")
        append('\n')
        append("""{"tool":"https_runtime","profileId":"<id>","argv":["uname","-a"]}""")
        append('\n')
        append("""{"tool":"noop","reason":"..."}""")
        append('\n')
        append("OpenAI tool_calls function-call JSON is also accepted. No secrets.\n")
        append("goal id=").append(goal.id).append(" title=").append(goal.title).append('\n')
        append("status=").append(goal.status).append('\n')
        append("budgets steps=")
            .append(goal.budgets.stepsUsed).append('/').append(goal.budgets.maxSteps)
            .append(" tools=").append(goal.budgets.toolCallsUsed).append('/')
            .append(goal.budgets.maxToolCalls)
            .append(" ssh=").append(goal.budgets.sshExecsUsed).append('/')
            .append(goal.budgets.maxSshExecs).append('\n')
        append("tools=").append(tools.joinToString(",")).append('\n')
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
    }
}

/** Alias kept for call sites that prefer "AgentLoop" naming. */
typealias AgentLoop = AgenticPlanner
