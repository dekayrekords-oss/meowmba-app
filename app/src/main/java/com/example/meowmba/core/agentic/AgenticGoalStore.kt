package com.example.meowmba.core.agentic

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * DAO-shaped store for agentic goals / events.
 * Production path: [RoomAgenticGoalStore]. In-memory kept for tests.
 */
interface AgenticGoalStore {
    fun insertGoal(title: String, budgets: AgenticBudgets = AgenticBudgets()): AgenticGoal
    fun getGoal(id: String): AgenticGoal?
    fun updateGoal(goal: AgenticGoal): AgenticGoal?
    fun listOpenGoals(): List<AgenticGoal>
    fun appendEvent(goalId: String, type: String, detail: String = "", taskId: String? = null): AgenticEvent?
    fun listEvents(goalId: String): List<AgenticEvent>
}

private val OPENISH: Set<GoalStatus> = setOf(
    GoalStatus.OPEN,
    GoalStatus.RUNNING,
    GoalStatus.BLOCKED,
    GoalStatus.WAITING_APPROVAL,
)

/** Process-local concurrent store (tests / fallback). */
class InMemoryAgenticGoalStore : AgenticGoalStore {
    private val goals = ConcurrentHashMap<String, AgenticGoal>()
    private val events = ConcurrentHashMap<String, MutableList<AgenticEvent>>()

    override fun insertGoal(title: String, budgets: AgenticBudgets): AgenticGoal {
        val now = System.currentTimeMillis()
        val goal = AgenticGoal(
            id = UUID.randomUUID().toString(),
            title = title,
            status = GoalStatus.OPEN,
            budgets = budgets,
            createdAt = now,
            updatedAt = now,
        )
        goals[goal.id] = goal
        events[goal.id] = mutableListOf()
        return goal
    }

    override fun getGoal(id: String): AgenticGoal? = goals[id]

    override fun updateGoal(goal: AgenticGoal): AgenticGoal? {
        if (!goals.containsKey(goal.id)) return null
        val updated = goal.copy(updatedAt = System.currentTimeMillis())
        goals[goal.id] = updated
        return updated
    }

    override fun listOpenGoals(): List<AgenticGoal> =
        goals.values.filter { it.status in OPENISH }.sortedBy { it.createdAt }

    override fun appendEvent(
        goalId: String,
        type: String,
        detail: String,
        taskId: String?,
    ): AgenticEvent? {
        if (!goals.containsKey(goalId)) return null
        val event = AgenticEvent(
            id = UUID.randomUUID().toString(),
            goalId = goalId,
            taskId = taskId,
            type = type,
            detail = detail,
            timestamp = System.currentTimeMillis(),
        )
        val bucket = events.getOrPut(goalId) { mutableListOf() }
        synchronized(bucket) { bucket.add(event) }
        goals[goalId]?.let { g ->
            goals[goalId] = g.copy(updatedAt = event.timestamp)
        }
        return event
    }

    override fun listEvents(goalId: String): List<AgenticEvent> {
        val bucket = events[goalId] ?: return emptyList()
        synchronized(bucket) { return bucket.toList() }
    }
}
