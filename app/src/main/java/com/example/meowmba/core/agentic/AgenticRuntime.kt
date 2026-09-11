package com.example.meowmba.core.agentic

import android.content.Context
import com.example.meowmba.core.config.FeatureFlags
import com.example.meowmba.core.database.MeowmbaDatabase
import com.example.meowmba.core.security.SecureStorageManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Agentic runtime v1 facade (Phases 3–8 production scaffolding).
 *
 * When [FeatureFlags.agenticRuntimeV1] is OFF (default), all methods are
 * no-ops / empty / [SshExecResult.NotEnabled] — callers must not change main UX.
 * When ON, uses Room-backed goal/profile stores, an [AgenticSshExecutor], and a
 * model-driven [AgenticPlanner] loop via [tick].
 *
 * No FlyWire. Secrets never enter event detail / prompts — only credential refs.
 * No opencode. Do not drive SSH from the Lumen frame loop.
 */
class AgenticRuntime private constructor(
    private val appContext: Context,
    private val flags: FeatureFlags,
    private val store: AgenticGoalStore,
    private val profileStore: ConnectionProfileStore,
    private val sshExecutor: AgenticSshExecutor,
    private val planner: AgenticPlanner,
    private val memoryIndex: AgenticMemoryIndex,
    private val credentials: CredentialResolver,
) {
    private val tools = ConcurrentHashMap<String, String>()

    val enabled: Boolean
        get() = flags.agenticRuntimeV1

    init {
        tools["sshExec"] = "Execute argv on a ConnectionProfile (SSH) via AgenticSshExecutor"
        tools["ssh_exec"] = "Alias for sshExec (planner-facing name)"
        tools["local_shell"] = "Run argv locally via Termux/system sh (ProcessBuilder or RUN_COMMAND)"
        tools["https_runtime"] = "Run argv on an HTTPS_RUNTIME worker profile"
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

    fun listWaitingApproval(): List<AgenticGoal> {
        if (!enabled) return emptyList()
        return store.listOpenGoals().filter { it.status == GoalStatus.WAITING_APPROVAL }
    }

    /** Tick every open/running goal once (skips waiting_approval without grant). */
    fun tickOpenGoals(): List<AgenticGoal> {
        if (!enabled) return emptyList()
        return store.listOpenGoals().mapNotNull { g ->
            when (g.status) {
                GoalStatus.OPEN, GoalStatus.RUNNING -> tick(g.id)
                GoalStatus.WAITING_APPROVAL -> if (g.approvalGranted) tick(g.id) else g
                else -> g
            }
        }
    }

    fun sshCapability(): AgenticSshCapability = AgenticSshCapability.probe(jschAvailable = true)

    fun modelConfigSnapshot(storage: SecureStorageManager): AgenticModelConfig.Snapshot =
        AgenticModelConfig.read(storage)

    /**
     * Approve a goal waiting on side-effecting ssh_exec. Sets [AgenticGoal.approvalGranted]
     * and moves status to [GoalStatus.RUNNING]. Returns null when flag OFF / not found.
     */
    fun approveGoal(goalId: String): AgenticGoal? {
        if (!enabled) return null
        val goal = store.getGoal(goalId) ?: return null
        store.appendEvent(goalId, type = "approved", detail = "user approved pending ssh_exec")
        return store.updateGoal(
            goal.copy(status = GoalStatus.RUNNING, approvalGranted = true),
        )
    }

    /** Reject a waiting goal → [GoalStatus.CANCELLED]. */
    fun rejectGoal(goalId: String): AgenticGoal? {
        if (!enabled) return null
        val goal = store.getGoal(goalId) ?: return null
        store.appendEvent(goalId, type = "rejected", detail = "user rejected pending ssh_exec")
        return store.updateGoal(
            goal.copy(status = GoalStatus.CANCELLED, approvalGranted = false),
        )
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

    fun tick(goalId: String): AgenticGoal? {
        if (!enabled) return null
        val goal = store.getGoal(goalId) ?: return null
        if (goal.status == GoalStatus.DONE || goal.status == GoalStatus.CANCELLED) {
            return goal
        }
        if (goal.status == GoalStatus.WAITING_APPROVAL && !goal.approvalGranted) {
            return goal
        }

        val toolsList = listTools()
        val profiles = profileStore.list()
        val events = store.listEvents(goalId)
        // Light memory → planner context (synthetic events; never secrets).
        val memoryEvents = memoryIndex.recall(goalId, limit = 5).mapIndexed { idx, note ->
            AgenticEvent(
                id = "mem-$idx",
                goalId = goalId,
                type = "memory",
                detail = note.take(120),
            )
        }
        val proposal = planner.propose(goal, toolsList, profiles, events + memoryEvents)

        val modelText = (planner as? HeuristicAgenticPlanner)?.lastModelText.orEmpty()
        store.appendEvent(
            goalId,
            type = "model_attempt",
            detail = "plan=${modelText.take(120)} propose=${proposal.tool} reason=${proposal.reason.take(120)}",
        )

        var budgets = goal.budgets.copy(stepsUsed = goal.budgets.stepsUsed + 1)
        var status = if (goal.status == GoalStatus.OPEN) GoalStatus.RUNNING else goal.status
        var approvalGranted = goal.approvalGranted

        if (proposal.isNoop) {
            store.appendEvent(goalId, type = "noop", detail = proposal.reason.take(200))
            if (budgets.exhausted()) {
                status = GoalStatus.BLOCKED
                store.appendEvent(goalId, type = "budget_exhausted", detail = "steps/tools budget hit")
            }
            return store.updateGoal(
                goal.copy(status = status, budgets = budgets, approvalGranted = approvalGranted),
            )
        }

        budgets = budgets.copy(toolCallsUsed = budgets.toolCallsUsed + 1)

        val toolKey = proposal.tool.lowercase().replace('-', '_')
        when (toolKey) {
            "ssh_exec", "sshexec", "https_runtime" -> {
                if (budgets.sshExecsRemaining() <= 0) {
                    store.appendEvent(goalId, type = "ssh_exec_skipped", detail = "ssh budget exhausted")
                    status = GoalStatus.BLOCKED
                    return store.updateGoal(
                        goal.copy(status = status, budgets = budgets, approvalGranted = false),
                    )
                }
                val profileId = proposal.profileId
                if (profileId.isNullOrBlank()) {
                    store.appendEvent(goalId, type = "ssh_exec_skipped", detail = "missing profileId")
                    status = GoalStatus.BLOCKED
                    return store.updateGoal(
                        goal.copy(status = status, budgets = budgets, approvalGranted = false),
                    )
                }

                val argv = proposal.argv.ifEmpty { listOf("uname", "-a") }
                if (AgenticSshPolicy.requiresApproval(argv) && !approvalGranted) {
                    store.appendEvent(
                        goalId,
                        type = "awaiting_approval",
                        detail = "$toolKey profile=$profileId argv=${argv.take(6).joinToString(" ")}",
                    )
                    return store.updateGoal(
                        goal.copy(
                            status = GoalStatus.WAITING_APPROVAL,
                            budgets = budgets,
                            approvalGranted = false,
                        ),
                    )
                }

                if (approvalGranted && AgenticSshPolicy.isSideEffecting(argv)) {
                    approvalGranted = false
                }

                budgets = budgets.copy(sshExecsUsed = budgets.sshExecsUsed + 1)
                val result = sshExec(
                    profileId = profileId,
                    argv = argv,
                    goalId = goalId,
                )
                status = when (result) {
                    is SshExecResult.Ok -> GoalStatus.RUNNING
                    is SshExecResult.Unsupported -> GoalStatus.BLOCKED
                    is SshExecResult.Error -> GoalStatus.BLOCKED
                    is SshExecResult.NotEnabled -> GoalStatus.BLOCKED
                }
                memoryIndex.note(
                    goalId,
                    "$toolKey profile=$profileId result=${result::class.simpleName}",
                )
            }
            "local_shell" -> {
                val argv = proposal.argv.ifEmpty { listOf("uname", "-a") }
                if (AgenticSshPolicy.requiresApproval(argv) && !approvalGranted) {
                    store.appendEvent(
                        goalId,
                        type = "awaiting_approval",
                        detail = "local_shell argv=${argv.take(6).joinToString(" ")}",
                    )
                    return store.updateGoal(
                        goal.copy(
                            status = GoalStatus.WAITING_APPROVAL,
                            budgets = budgets,
                            approvalGranted = false,
                        ),
                    )
                }
                if (approvalGranted && AgenticSshPolicy.isSideEffecting(argv)) {
                    approvalGranted = false
                }
                val script = if (argv.size >= 2 && argv[0] == "sh" && argv[1] == "-c") {
                    argv.drop(2).joinToString(" ")
                } else {
                    argv.joinToString(" ")
                }
                val result = TermuxRunCommandHelper.execSh(appContext, script)
                store.appendEvent(
                    goalId,
                    type = "local_shell",
                    detail = when (result) {
                        is SshExecResult.Ok -> "ok exit=${result.exitCode}"
                        is SshExecResult.Unsupported -> "unsupported: ${result.message.take(160)}"
                        is SshExecResult.Error -> "error: ${result.message.take(160)}"
                        is SshExecResult.NotEnabled -> "not_enabled"
                    },
                )
                status = when (result) {
                    is SshExecResult.Ok -> GoalStatus.RUNNING
                    else -> GoalStatus.BLOCKED
                }
                memoryIndex.note(goalId, "local_shell result=${result::class.simpleName}")
            }
            else -> {
                store.appendEvent(
                    goalId,
                    type = "unknown_tool",
                    detail = "tool=${proposal.tool.take(64)}",
                )
                status = GoalStatus.BLOCKED
            }
        }

        if (budgets.exhausted()) {
            status = GoalStatus.BLOCKED
            store.appendEvent(goalId, type = "budget_exhausted", detail = "steps/tools budget hit")
        }

        return store.updateGoal(
            goal.copy(status = status, budgets = budgets, approvalGranted = approvalGranted),
        )
    }

    fun step(goalId: String): AgenticGoal? = tick(goalId)

    fun noteMemory(goalId: String, text: String): Boolean {
        if (!enabled) return false
        return memoryIndex.note(goalId, text)
    }

    fun recallMemory(goalId: String, limit: Int = 5): List<String> {
        if (!enabled) return emptyList()
        return memoryIndex.recall(goalId, limit)
    }

    /** Export file-backed project notes (second-brain MVP). Null when flag OFF. */
    fun exportMemoryNotes(): java.io.File? {
        if (!enabled) return null
        val fb = memoryIndex as? FileBackedAgenticMemoryIndex ?: return null
        return fb.exportAll()
    }

    fun importMemoryNotes(file: java.io.File): Int {
        if (!enabled) return 0
        val fb = memoryIndex as? FileBackedAgenticMemoryIndex ?: return 0
        return fb.importAll(file)
    }

    fun termuxProbeSummary(): String =
        TermuxRunCommandHelper.probe(appContext).summary

    /**
     * Import a legacy [com.example.meowmba.core.ssh.SshHostRegistry] host into
     * an agentic ConnectionProfile (credentialRef only).
     */
    fun importLegacySshHost(hostName: String): ConnectionProfile? {
        if (!enabled) return null
        val storage = SecureStorageManager(appContext)
        val creds = credentials as? SecureStorageCredentialResolver
            ?: SecureStorageCredentialResolver(storage)
        return AgenticSshBridge.importLegacyHost(storage, profileStore, creds, hostName)
    }

    /** Export an agentic SSH profile into the legacy SshHostRegistry for SshTool. */
    fun exportProfileToLegacyRegistry(profileId: String): Boolean {
        if (!enabled) return false
        val profile = profileStore.get(profileId) ?: return false
        val storage = SecureStorageManager(appContext)
        return AgenticSshBridge.exportToLegacyRegistry(storage, profile, credentials)
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
            val db = MeowmbaDatabase.getDatabase(app)

            // Room-backed stores when flag ON path is used; Empty kept for
            // design/tests. Public API still no-ops while the flag is OFF.
            // Always wire Room so data survives process death after flag toggle.
            val goalStore: AgenticGoalStore = RoomAgenticGoalStore(
                db.agenticGoalDao(),
                db.agenticEventDao(),
            )
            val profiles: ConnectionProfileStore = RoomConnectionProfileStore(
                db.connectionProfileDao(),
            )
            // Migrate any SecureStorage JSON profiles into Room once (best-effort).
            runCatching {
                val legacy = SecureConnectionProfileStore(storage).list()
                if (legacy.isNotEmpty() && profiles.list().isEmpty()) {
                    legacy.forEach { profiles.save(it) }
                }
            }

            val credentials: CredentialResolver = SecureStorageCredentialResolver(storage)
            val executor = DefaultAgenticSshExecutor(
                enabled = { flags.agenticRuntimeV1 },
                profiles = profiles,
                credentials = credentials,
                allowJschFallback = true,
            )
            val modelAdapter = AgenticModelConfig.resolveAdapter(storage, credentials)
            return AgenticRuntime(
                appContext = app,
                flags = flags,
                store = goalStore,
                profileStore = profiles,
                sshExecutor = executor,
                planner = HeuristicAgenticPlanner(modelAdapter),
                memoryIndex = FileBackedAgenticMemoryIndex(app),
                credentials = credentials,
            )
        }

        /** Drop singleton so the next [from] rebuilds (e.g. after model config change). */
        fun invalidate() {
            synchronized(this) { INSTANCE = null }
        }

        fun resetForTests() {
            invalidate()
        }
    }
}
