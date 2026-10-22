package com.example.meowmba.ui.companion

import com.example.meowmba.core.agentic.AgenticGoal
import com.example.meowmba.core.agentic.AgenticRuntime
import com.example.meowmba.core.agentic.GoalStatus

/**
 * Phase 6 — Lumen ↔ agentic coupling (presence only).
 *
 * Maps open/running/blocked/waiting_approval goals to a [CompanionEmotion]
 * suggestion and a short interaction hint. **Does not** drive SSH, and must
 * never be called from the Lumen frame / [AvatarRenderer] loop.
 *
 * Call only when [com.example.meowmba.core.config.FeatureFlags.agenticRuntimeV1]
 * is ON (see optional hook in [CompanionViewModel]).
 */
data class AgenticPresence(
    val emotion: CompanionEmotion?,
    val hint: String,
    val primaryStatus: GoalStatus? = null,
    val openGoalCount: Int = 0,
    val dismissibleRevision: String? = null,
)

object CompanionAgenticHooks {

    /** Acknowledgement hides only this blocked revision; retrying must surface a new failure. */
    fun revision(goal: AgenticGoal): String = "${goal.id}:${goal.status}:${goal.updatedAt}"

    fun fromGoals(goals: List<AgenticGoal>, acknowledged: Set<String> = emptySet()): AgenticPresence {
        val visible = goals.filter {
            it.status !in setOf(GoalStatus.DONE, GoalStatus.CANCELLED) &&
                !(it.status == GoalStatus.BLOCKED && revision(it) in acknowledged)
        }
        if (visible.isEmpty()) {
            return AgenticPresence(
                emotion = null,
                hint = "",
                primaryStatus = null,
                openGoalCount = 0,
            )
        }
        // Old failures must not cover current work. Among failures, show the newest first.
        val primary = visible.sortedWith(compareBy<AgenticGoal> { priority(it.status) }
            .thenByDescending { it.updatedAt }.thenBy { it.id }).first()
        val emotion = when (primary.status) {
            GoalStatus.WAITING_APPROVAL -> CompanionEmotion.ALERT
            GoalStatus.BLOCKED -> CompanionEmotion.CONCERNED
            GoalStatus.RUNNING -> CompanionEmotion.DETERMINED
            GoalStatus.OPEN -> CompanionEmotion.CURIOUS
            GoalStatus.DONE -> CompanionEmotion.PROUD
            GoalStatus.CANCELLED -> CompanionEmotion.APOLOGETIC
        }
        val hint = when (primary.status) {
            GoalStatus.WAITING_APPROVAL ->
                "Task needs approval: " + primary.title.take(60)
            GoalStatus.BLOCKED ->
                "Task stopped: " + primary.title.take(60)
            GoalStatus.RUNNING ->
                "Working on: " + primary.title.take(60)
            GoalStatus.OPEN ->
                "Agentic: ${goals.size} open goal(s)"
            GoalStatus.DONE ->
                "Agentic: goal done"
            GoalStatus.CANCELLED ->
                "Agentic: goal cancelled"
        }
        return AgenticPresence(
            emotion = emotion,
            hint = hint,
            primaryStatus = primary.status,
            openGoalCount = goals.size,
            dismissibleRevision = primary.takeIf { it.status == GoalStatus.BLOCKED }?.let(::revision),
        )
    }

    fun fromRuntime(runtime: AgenticRuntime, acknowledged: Set<String> = emptySet()): AgenticPresence {
        if (!runtime.enabled) {
            return AgenticPresence(emotion = null, hint = "")
        }
        return fromGoals(runtime.listOpenGoals(), acknowledged)
    }

    private fun priority(status: GoalStatus): Int = when (status) {
        GoalStatus.WAITING_APPROVAL -> 0
        GoalStatus.RUNNING -> 1
        GoalStatus.BLOCKED -> 2
        GoalStatus.OPEN -> 3
        GoalStatus.DONE -> 4
        GoalStatus.CANCELLED -> 5
    }
}
