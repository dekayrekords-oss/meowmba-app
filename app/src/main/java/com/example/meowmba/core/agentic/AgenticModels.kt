package com.example.meowmba.core.agentic

/**
 * Lightweight goal / task / event records for agentic runtime v1.
 * Persisted via Room when `agentic_runtime_v1` wiring is active
 * (see [RoomAgenticGoalStore]). No secrets in [detail] / payloads.
 */
enum class GoalStatus {
    OPEN,
    RUNNING,
    BLOCKED,
    WAITING_APPROVAL,
    DONE,
    CANCELLED,
}

enum class TaskStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED,
}

/**
 * Per-goal execution budgets. The planner / [AgenticRuntime.tick] must stop
 * proposing tools once any counter reaches its max (scaffold enforcement).
 */
data class AgenticBudgets(
    val maxSteps: Int = 8,
    val maxToolCalls: Int = 4,
    val maxSshExecs: Int = 2,
    val stepsUsed: Int = 0,
    val toolCallsUsed: Int = 0,
    val sshExecsUsed: Int = 0,
) {
    fun stepsRemaining(): Int = (maxSteps - stepsUsed).coerceAtLeast(0)
    fun toolCallsRemaining(): Int = (maxToolCalls - toolCallsUsed).coerceAtLeast(0)
    fun sshExecsRemaining(): Int = (maxSshExecs - sshExecsUsed).coerceAtLeast(0)
    fun exhausted(): Boolean =
        stepsRemaining() == 0 || toolCallsRemaining() == 0
}

data class AgenticGoal(
    val id: String,
    val title: String,
    val status: GoalStatus = GoalStatus.OPEN,
    val budgets: AgenticBudgets = AgenticBudgets(),
    /** One-shot approval for the next side-effecting ssh_exec. */
    val approvalGranted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

data class AgenticTask(
    val id: String,
    val goalId: String,
    val title: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

data class AgenticEvent(
    val id: String,
    val goalId: String,
    val taskId: String? = null,
    /** Short verb, e.g. "created", "progress", "error", "model_attempt". */
    val type: String,
    val detail: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

/** Planner output: next tool to invoke, or a no-op. */
data class ProposedToolCall(
    val tool: String,
    val profileId: String? = null,
    val argv: List<String> = emptyList(),
    val reason: String = "",
) {
    val isNoop: Boolean
        get() = tool.equals("noop", ignoreCase = true) ||
            tool.equals("no-op", ignoreCase = true) ||
            tool.isBlank()
}
