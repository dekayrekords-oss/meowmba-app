package com.example.meowmba.ui.companion.lumen

import com.example.meowmba.core.lumen.ActionCompetition
import com.example.meowmba.core.lumen.ReflexAction
import com.example.meowmba.core.lumen.ReflexDynamics
import com.example.meowmba.core.lumen.ReflexState
import com.example.meowmba.ui.companion.InteractionState
import com.example.meowmba.ui.companion.LumenInput

/**
 * Optional Lumen reflex v2 path. Callers must gate with FeatureFlags.lumenReflexV2;
 * when the flag is off this class must not be invoked so prior behavior is preserved.
 */
class LumenReflexBridge {
    private var state = ReflexState()
    private val competition = ActionCompetition()
    private var lastTime: Float? = null

    val reflexState: ReflexState get() = state
    val currentAction: ReflexAction get() = competition.current

    fun modulate(input: LumenInput, timeSec: Float): LumenInput {
        if (!timeSec.isFinite()) return input
        val previous = lastTime
        lastTime = timeSec
        val dt = if (previous == null) 0f else (timeSec - previous).coerceIn(0f, 0.05f)
        val speaking = input.speaking > 0.02f || input.interaction == InteractionState.SPEAKING
        val resting = (input.interaction == InteractionState.IDLE ||
            input.interaction == InteractionState.MUTED ||
            input.interaction == InteractionState.OFFLINE) && !speaking
        val userInView = input.cameraEnabled && input.gazeX != null && input.gazeY != null
        state = ReflexDynamics.step(
            state = state,
            dt = dt,
            cameraEnabled = input.cameraEnabled,
            userInView = userInView,
            speaking = speaking,
            resting = resting,
        )
        val action = competition.step(state, dt)
        return applyAction(input, state, action)
    }

    private fun applyAction(input: LumenInput, reflex: ReflexState, action: ReflexAction): LumenInput {
        val fatigueDrop = reflex.fatigue * 0.18f
        val cautionLean = reflex.caution * 0.12f
        return when (action) {
            ReflexAction.ORIENT_SOCIAL -> input.copy(
                presence = (input.presence + reflex.socialAttention * 0.15f).coerceIn(0f, 1.2f),
                openness = (input.openness - fatigueDrop * 0.5f).coerceIn(0.35f, 1f),
                earPerk = (input.earPerk + reflex.socialAttention * 0.12f).coerceIn(0f, 1.2f),
            )
            ReflexAction.EXPLORE -> input.copy(
                bounce = input.bounce + reflex.curiosity * 0.08f,
                earPerk = (input.earPerk + reflex.curiosity * 0.1f).coerceIn(0f, 1.2f),
                presence = (input.presence + 0.05f).coerceIn(0f, 1.2f),
            )
            ReflexAction.WITHDRAW -> input.copy(
                openness = (input.openness - fatigueDrop - cautionLean).coerceIn(0.25f, 1f),
                lean = input.lean * (1f - reflex.caution * 0.25f),
                presence = (input.presence - reflex.caution * 0.12f).coerceIn(0.4f, 1.2f),
                earDroop = (input.earDroop + reflex.fatigue * 4f).coerceAtMost(18f),
            )
            ReflexAction.IDLE_HOLD -> input.copy(
                openness = (input.openness - fatigueDrop * 0.35f).coerceIn(0.35f, 1f),
            )
        }
    }
}
