package com.example.meowmba.core.agentic

/** Bounded execution; every stop has an explicit persisted outcome. */
object AgenticAutoTick {
    val TERMINAL_STATUSES = setOf(GoalStatus.DONE, GoalStatus.BLOCKED, GoalStatus.WAITING_APPROVAL, GoalStatus.CANCELLED)
    fun shouldContinue(status: GoalStatus, budgets: AgenticBudgets) = status !in TERMINAL_STATUSES && !budgets.exhausted()
    fun run(
        initial: AgenticGoal,
        tick: (AgenticGoal) -> AgenticGoal?,
        isCancelled: () -> Boolean = { false },
        onCancel: (AgenticGoal) -> AgenticGoal = { it.copy(status = GoalStatus.CANCELLED) },
        onBudgetExhausted: (AgenticGoal) -> AgenticGoal = { it.copy(status = GoalStatus.BLOCKED) },
        maxGuard: Int = 32,
    ): AgenticGoal {
        var current = initial
        repeat(maxGuard.coerceIn(1, 64)) {
            if (isCancelled()) return onCancel(current)
            if (current.status in TERMINAL_STATUSES) return current
            if (current.budgets.exhausted()) return onBudgetExhausted(current)
            val next = tick(current) ?: return onBudgetExhausted(current)
            if (isCancelled()) return onCancel(next)
            if (next.status in TERMINAL_STATUSES) return next
            if (next.status == current.status && next.budgets.stepsUsed == current.budgets.stepsUsed)
                return onBudgetExhausted(next)
            current = next
        }
        return if (current.status in TERMINAL_STATUSES) current else onBudgetExhausted(current)
    }
}
