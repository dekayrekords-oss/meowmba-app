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
)

object CompanionAgenticHooks {

    fun fromGoals(goals: List<AgenticGoal>): AgenticPresence {
        if (goals.isEmpty()) {
            return AgenticPresence(
                emotion = null,
                hint = "",
                primaryStatus = null,
                openGoalCount = 0,
            )
        }
        // Priority: waiting_approval > blocked > running > open
        val primary = goals.minBy { priority(it.status) }
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
                "Agentic: waiting for approval on '" + primary.title.take(40) + "'"
            GoalStatus.BLOCKED ->
                "Agentic: blocked on '" + primary.title.take(40) + "'"
            GoalStatus.RUNNING ->
                "Agentic: working on '" + primary.title.take(40) + "'"
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
        )
    }

    fun fromRuntime(runtime: AgenticRuntime): AgenticPresence {
        if (!runtime.enabled) {
            return AgenticPresence(emotion = null, hint = "")
        }
        return fromGoals(runtime.listOpenGoals())
    }

    private fun priority(status: GoalStatus): Int = when (status) {
        GoalStatus.WAITING_APPROVAL -> 0
        GoalStatus.BLOCKED -> 1
        GoalStatus.RUNNING -> 2
        GoalStatus.OPEN -> 3
        GoalStatus.DONE -> 4
        GoalStatus.CANCELLED -> 5
    }
}
