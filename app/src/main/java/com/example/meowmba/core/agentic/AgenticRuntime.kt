package com.example.meowmba.core.agentic

import android.content.Context
import com.example.meowmba.core.config.FeatureFlags
import com.example.meowmba.core.security.SecureStorageManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Agentic runtime v1 facade (Phases 3–5).
 *
 * When [FeatureFlags.agenticRuntimeV1] is OFF (default), all methods are
 * no-ops / empty / [SshExecResult.NotEnabled] — callers must not change main UX.
 * When ON, uses in-memory goal store + SecureStorage-backed connection profiles,
 * an [AgenticSshExecutor], and a model-driven [AgenticPlanner] loop via [tick].
 *
 * No FlyWire. Secrets never enter event detail / prompts — only credential refs.
 * No opencode. Do not drive SSH from the Lumen frame loop.
 */
class AgenticRuntime private constructor(
    private val flags: FeatureFlags,
    private val store: AgenticGoalStore,
    private val profileStore: ConnectionProfileStore,
    private val sshExecutor: AgenticSshExecutor,
    private val planner: AgenticPlanner,
    private val memoryIndex: AgenticMemoryIndex,
) {
    private val tools = ConcurrentHashMap<String, String>()

    val enabled: Boolean
        get() = flags.agenticRuntimeV1

    init {
        // Built-in Phase-4/5 tool ids; description only — execution via [sshExec].
        tools["sshExec"] = "Execute argv on a ConnectionProfile (SSH) via AgenticSshExecutor"
        tools["ssh_exec"] = "Alias for sshExec (planner-facing name)"
        tools["noop"] = "No-op / wait / budget stop"
    }

    fun createGoal(title: String, budgets: AgenticBudgets = AgenticBudgets()): AgenticGoal? {
        if (!enabled) return null
        val goal = store.insertGoal(title, budgets)
        store.appendEvent(goal.id, type = "created", detail = title)
        return goal
    }

    fun getGoal(goalId: String): AgenticGoal? {
        if (!enabled) return null
        return store.getGoal(goalId)
    }

    fun appendEvent(
        goalId: String,
        type: String,
        detail: String = "",
        taskId: String? = null,
    ): AgenticEvent? {
        if (!enabled) return null
        return store.appendEvent(goalId, type, detail, taskId)
    }

    fun listOpenGoals(): List<AgenticGoal> {
        if (!enabled) return emptyList()
        return store.listOpenGoals()
    }

    fun listEvents(goalId: String): List<AgenticEvent> {
        if (!enabled) return emptyList()
        return store.listEvents(goalId)
    }

    // --- Phase 4: connection profiles + tool registry + sshExec ---

    fun listConnectionProfiles(): List<ConnectionProfile> {
        if (!enabled) return emptyList()
        return profileStore.list()
    }

    fun getConnectionProfile(id: String): ConnectionProfile? {
        if (!enabled) return null
        return profileStore.get(id)
    }

    fun saveConnectionProfile(profile: ConnectionProfile): ConnectionProfile? {
        if (!enabled) return null
        val saved = profileStore.save(profile)
        // Event detail uses refs / display names only — never secrets.
        store.listOpenGoals().firstOrNull()?.let { g ->
            store.appendEvent(
                g.id,
                type = "connection_profile_saved",
                detail = "id=${saved.id} type=${saved.type} host=${saved.host} ref=${saved.credentialRef}",
            )
        }
        return saved
    }

    fun deleteConnectionProfile(id: String): Boolean {
        if (!enabled) return false
        return profileStore.delete(id)
    }

    /** Register a tool name → short description. Returns false when flag OFF. */
    fun registerTool(name: String, description: String): Boolean {
        if (!enabled) return false
        val key = name.trim()
        if (key.isEmpty()) return false
        tools[key] = description
        return true
    }

    fun listTools(): List<String> {
        if (!enabled) return emptyList()
        return tools.keys.sorted()
    }

    fun toolDescription(name: String): String? {
        if (!enabled) return null
        return tools[name]
    }

    /**
     * Run argv against a saved SSH [ConnectionProfile].
     * Appends a sanitized event when [goalId] is provided (no secrets).
     */
    fun sshExec(
        profileId: String,
        argv: List<String>,
        timeoutMs: Long = 30_000L,
        goalId: String? = null,
    ): SshExecResult {
        if (!enabled) {
            return SshExecResult.NotEnabled()
        }
        val result = sshExecutor.exec(profileId, argv, timeoutMs)
        val detail = when (result) {
            is SshExecResult.Ok ->
                "ok exit=${result.exitCode} profile=$profileId argv=${argv.take(4).joinToString(" ")}"
            is SshExecResult.NotEnabled -> "not_enabled"
            is SshExecResult.Unsupported -> "unsupported: ${result.message.take(200)}"
            is SshExecResult.Error -> "error: ${result.message.take(200)}"
        }
        if (goalId != null) {
            store.appendEvent(goalId, type = "ssh_exec", detail = detail)
        }
        return result
    }

    // --- Phase 5: model-driven agent loop ---

    /**
     * One planner step for [goalId]. Flag OFF → null (no-op).
     * Loads goal, calls planner, records model_attempt, may invoke ssh_exec,
     * appends events, updates status / budgets.
     */
    fun tick(goalId: String): AgenticGoal? {
        if (!enabled) return null
        val goal = store.getGoal(goalId) ?: return null
        if (goal.status == GoalStatus.DONE || goal.status == GoalStatus.CANCELLED) {
            return goal
        }

        val toolsList = listTools()
        val profiles = profileStore.list()
        val events = store.listEvents(goalId)
        val proposal = planner.propose(goal, toolsList, profiles, events)

        val modelText = (planner as? HeuristicAgenticPlanner)?.lastModelText.orEmpty()
        store.appendEvent(
            goalId,
            type = "model_attempt",
            detail = "plan=${modelText.take(120)} propose=${proposal.tool} reason=${proposal.reason.take(120)}",
        )

        var budgets = goal.budgets.copy(stepsUsed = goal.budgets.stepsUsed + 1)
        var status = if (goal.status == GoalStatus.OPEN) GoalStatus.RUNNING else goal.status

        if (proposal.isNoop) {
            store.appendEvent(goalId, type = "noop", detail = proposal.reason.take(200))
            if (budgets.exhausted()) {
                status = GoalStatus.BLOCKED
                store.appendEvent(goalId, type = "budget_exhausted", detail = "steps/tools budget hit")
            }
            return store.updateGoal(goal.copy(status = status, budgets = budgets))
        }

        budgets = budgets.copy(toolCallsUsed = budgets.toolCallsUsed + 1)

        val toolKey = proposal.tool.lowercase().replace('-', '_')
        if (toolKey == "ssh_exec" || toolKey == "sshexec") {
            if (budgets.sshExecsRemaining() <= 0) {
                store.appendEvent(goalId, type = "ssh_exec_skipped", detail = "ssh budget exhausted")
                status = GoalStatus.BLOCKED
                return store.updateGoal(goal.copy(status = status, budgets = budgets))
            }
            val profileId = proposal.profileId
            if (profileId.isNullOrBlank()) {
                store.appendEvent(goalId, type = "ssh_exec_skipped", detail = "missing profileId")
                status = GoalStatus.BLOCKED
                return store.updateGoal(goal.copy(status = status, budgets = budgets))
            }
            budgets = budgets.copy(sshExecsUsed = budgets.sshExecsUsed + 1)
            val result = sshExec(
                profileId = profileId,
                argv = proposal.argv.ifEmpty { listOf("uname", "-a") },
                goalId = goalId,
            )
            status = when (result) {
                is SshExecResult.Ok -> GoalStatus.RUNNING
                is SshExecResult.Unsupported -> GoalStatus.BLOCKED
                is SshExecResult.Error -> GoalStatus.BLOCKED
                is SshExecResult.NotEnabled -> GoalStatus.BLOCKED
            }
            // Optional memory note (sanitized) — does not touch MemoryCore.
            memoryIndex.note(
                goalId,
                "ssh_exec profile=$profileId result=${result::class.simpleName}",
            )
        } else {
            store.appendEvent(
                goalId,
                type = "unknown_tool",
                detail = "tool=${proposal.tool.take(64)}",
            )
            status = GoalStatus.BLOCKED
        }

        if (budgets.exhausted()) {
            status = GoalStatus.BLOCKED
            store.appendEvent(goalId, type = "budget_exhausted", detail = "steps/tools budget hit")
        }

        return store.updateGoal(goal.copy(status = status, budgets = budgets))
    }

    /** Alias for [tick] — one planner/tool step. */
    fun step(goalId: String): AgenticGoal? = tick(goalId)

    // --- Phase 7 memory index (optional) ---

    fun noteMemory(goalId: String, text: String): Boolean {
        if (!enabled) return false
        return memoryIndex.note(goalId, text)
    }

    fun recallMemory(goalId: String, limit: Int = 5): List<String> {
        if (!enabled) return emptyList()
        return memoryIndex.recall(goalId, limit)
    }

    companion object {
        @Volatile
        private var INSTANCE: AgenticRuntime? = null

        fun from(context: Context): AgenticRuntime {
            val app = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(app).also { INSTANCE = it }
            }
        }

        private fun build(app: Context): AgenticRuntime {
            val flags = FeatureFlags.from(app)
            val storage = SecureStorageManager(app)
            // Always wire SecureStorage-backed stores; public API still returns
            // empty / NotEnabled while the flag is OFF. After adb toggle +
            // process restart, persistence is already in place.
            val profiles: ConnectionProfileStore = SecureConnectionProfileStore(storage)
            val credentials: CredentialResolver = SecureStorageCredentialResolver(storage)
            val executor = DefaultAgenticSshExecutor(
                enabled = { flags.agenticRuntimeV1 },
                profiles = profiles,
                credentials = credentials,
            )
            return AgenticRuntime(
                flags = flags,
                store = InMemoryAgenticGoalStore(),
                profileStore = profiles,
                sshExecutor = executor,
                planner = HeuristicAgenticPlanner(StubModelAdapter()),
                memoryIndex = InMemoryAgenticMemoryIndex(),
            )
        }

        /** Test / debug helper to replace the process singleton. */
        fun resetForTests() {
            synchronized(this) { INSTANCE = null }
        }
    }
}
