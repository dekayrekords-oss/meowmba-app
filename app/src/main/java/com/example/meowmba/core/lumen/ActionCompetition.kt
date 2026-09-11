package com.example.meowmba.core.lumen

/**
 * Winner-take-most action selection with hysteresis and per-action cooldowns.
 * Scaffolding only — maps reflex drives to a coarse intent label for LumenInput.
 */
enum class ReflexAction {
    IDLE_HOLD,
    ORIENT_SOCIAL,
    EXPLORE,
    WITHDRAW,
}

data class ActionScores(
    val idleHold: Float = 0f,
    val orientSocial: Float = 0f,
    val explore: Float = 0f,
    val withdraw: Float = 0f,
) {
    fun forAction(action: ReflexAction): Float = when (action) {
        ReflexAction.IDLE_HOLD -> idleHold
        ReflexAction.ORIENT_SOCIAL -> orientSocial
        ReflexAction.EXPLORE -> explore
        ReflexAction.WITHDRAW -> withdraw
    }
}

class ActionCompetition(
    private val switchMargin: Float = 0.12f,
    private val cooldownSec: Float = 0.85f,
) {
    var current: ReflexAction = ReflexAction.IDLE_HOLD
        private set
    private val cooldownRemaining = mutableMapOf<ReflexAction, Float>()

    fun score(state: ReflexState): ActionScores {
        val s = state.coerced()
        return ActionScores(
            idleHold = (1f - s.curiosity) * 0.45f + (1f - s.socialAttention) * 0.25f + s.fatigue * 0.2f,
            orientSocial = s.socialAttention * 0.7f + (1f - s.fatigue) * 0.2f - s.caution * 0.15f,
            explore = s.curiosity * 0.65f + (1f - s.socialAttention) * 0.15f - s.fatigue * 0.2f,
            withdraw = s.caution * 0.55f + s.fatigue * 0.35f - s.curiosity * 0.1f,
        )
    }

    fun step(state: ReflexState, dt: Float): ReflexAction {
        val t = dt.coerceIn(0f, 0.05f)
        for (action in ReflexAction.entries) {
            val left = (cooldownRemaining[action] ?: 0f) - t
            cooldownRemaining[action] = left.coerceAtLeast(0f)
        }
        val scores = score(state)
        val best = ReflexAction.entries.maxBy { scores.forAction(it) }
        val bestScore = scores.forAction(best)
        val currentScore = scores.forAction(current)
        val cooling = (cooldownRemaining[best] ?: 0f) > 0f
        if (best != current && !cooling && bestScore >= currentScore + switchMargin) {
            cooldownRemaining[current] = cooldownSec
            current = best
        }
        return current
    }
}
