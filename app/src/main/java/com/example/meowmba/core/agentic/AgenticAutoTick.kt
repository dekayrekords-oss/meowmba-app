package com.example.meowmba.core.agentic

/**
 * Pure auto-tick loop helper (chat Computer path). Stops at DONE | BLOCKED |
 * WAITING_APPROVAL | CANCELLED, on cancel, or when budgets / guard exhaust.
 * Does not touch Lumen / Desktop Bridge.
 */
object AgenticAutoTick {
    val TERMINAL_STATUSES: Set<GoalStatus> = setOf(
        GoalStatus.DONE,
        GoalStatus.BLOCKED,
        GoalStatus.WAITING_APPROVAL,
        GoalStatus.CANCELLED,
    )

    fun shouldContinue(status: GoalStatus, budgets: AgenticBudgets): Boolean =
        status !in TERMINAL_STATUSES && !budgets.exhausted()

    /**
     * @param tick one planner step; return null to abort
     * @return last non-null goal snapshot
     */
    fun run(
        initial: AgenticGoal,
        tick: (AgenticGoal) -> AgenticGoal?,
        isCancelled: () -> Boolean = { false },
        onCancel: (AgenticGoal) -> AgenticGoal = { it.copy(status = GoalStatus.CANCELLED) },
        onBudgetExhausted: (AgenticGoal) -> AgenticGoal = { it.copy(status = GoalStatus.BLOCKED) },
        maxGuard: Int = 32,
    ): AgenticGoal {
        var current = initial
        var guard = 0
        val limit = maxGuard.coerceAtLeast(1).coerceAtMost(64)
        while (shouldContinue(current.status, current.budgets) && guard < limit) {
            if (isCancelled()) return onCancel(current)
            if (current.budgets.exhausted()) return onBudgetExhausted(current)
            val beforeSteps = current.budgets.stepsUsed
            val next = tick(current) ?: return current
            guard++
            if (next.status == current.status && next.budgets.stepsUsed == beforeSteps) {
                return next
            }
            current = next
        }
        return current
    }
}
