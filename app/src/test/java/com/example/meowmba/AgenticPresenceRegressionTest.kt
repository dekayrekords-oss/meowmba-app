package com.example.meowmba

import com.example.meowmba.core.agentic.AgenticGoal
import com.example.meowmba.core.agentic.GoalStatus
import com.example.meowmba.ui.companion.CompanionAgenticHooks
import org.junit.Assert.*
import org.junit.Test

class AgenticPresenceRegressionTest {
    private fun goal(id: String, status: GoalStatus, updated: Long = 1L) =
        AgenticGoal(id = id, title = id, status = status, createdAt = 1L, updatedAt = updated)

    @Test fun oldFailureDoesNotCoverRunningTask() {
        val presence = CompanionAgenticHooks.fromGoals(listOf(goal("old", GoalStatus.BLOCKED), goal("current", GoalStatus.RUNNING)))
        assertEquals(GoalStatus.RUNNING, presence.primaryStatus)
        assertTrue(presence.hint.contains("current"))
        assertNull(presence.dismissibleRevision)
    }

    @Test fun mostRecentFailureWinsRegardlessOfStoreOrder() {
        val goals = listOf(goal("old", GoalStatus.BLOCKED), goal("new", GoalStatus.BLOCKED, 2L))
        assertTrue(CompanionAgenticHooks.fromGoals(goals).hint.contains("new"))
        assertEquals(CompanionAgenticHooks.fromGoals(goals), CompanionAgenticHooks.fromGoals(goals.reversed()))
    }

    @Test fun dismissalIsSpecificToTaskAndRevision() {
        val blocked = goal("failed", GoalStatus.BLOCKED)
        val acknowledged = setOf(CompanionAgenticHooks.revision(blocked))
        assertEquals("", CompanionAgenticHooks.fromGoals(listOf(blocked), acknowledged).hint)
        assertEquals(GoalStatus.BLOCKED, blocked.status)
        assertEquals(GoalStatus.BLOCKED, CompanionAgenticHooks.fromGoals(listOf(blocked.copy(updatedAt = 2L)), acknowledged).primaryStatus)
        assertEquals(GoalStatus.BLOCKED, CompanionAgenticHooks.fromGoals(listOf(goal("other", GoalStatus.BLOCKED)), acknowledged).primaryStatus)
    }

    @Test fun approvalCannotBeDismissedAndTerminalTasksDoNotShow() {
        val approval = goal("approval", GoalStatus.WAITING_APPROVAL)
        val presence = CompanionAgenticHooks.fromGoals(listOf(approval, goal("active", GoalStatus.RUNNING)), setOf(CompanionAgenticHooks.revision(approval)))
        assertEquals(GoalStatus.WAITING_APPROVAL, presence.primaryStatus)
        assertNull(presence.dismissibleRevision)
        assertEquals("", CompanionAgenticHooks.fromGoals(listOf(goal("done", GoalStatus.DONE), goal("cancelled", GoalStatus.CANCELLED))).hint)
    }
}
