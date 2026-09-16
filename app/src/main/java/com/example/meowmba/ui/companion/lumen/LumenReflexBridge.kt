package com.example.meowmba.ui.companion.lumen

import com.example.meowmba.core.lumen.ActionCompetition
import com.example.meowmba.core.lumen.ReflexAction
import com.example.meowmba.core.lumen.ReflexDynamics
import com.example.meowmba.core.lumen.ReflexSensoryFeed
import com.example.meowmba.core.lumen.ReflexState
import com.example.meowmba.ui.companion.InteractionState
import com.example.meowmba.ui.companion.LumenInput

/**
 * Optional Lumen reflex v2 path. Callers must gate with FeatureFlags.lumenReflexV2;
 * when the flag is off this class must not be invoked so prior behavior is preserved.
 *
 * Accepts an optional rich [ReflexSensoryFeed] (vision / social / speech / touch /
 * motion / thermal). When null, falls back to coarse LumenInput booleans.
 * Temporal novelty / loss / continuous-track are tracked inside the bridge.
 */
class LumenReflexBridge {
    private var state = ReflexState()
    private val competition = ActionCompetition()
    private var lastTime: Float? = null
    private var lastTrackingId: Int = -1
    private var hadFace: Boolean = false
    private var continuousTrackSec: Float = 0f
    private var recoveringFromLoss: Boolean = false

    val reflexState: ReflexState get() = state
    val currentAction: ReflexAction get() = competition.current

    fun modulate(
        input: LumenInput,
        timeSec: Float,
        feed: ReflexSensoryFeed? = null,
        gain: Float = 1f,
    ): LumenInput {
        if (!timeSec.isFinite()) return input
        val previous = lastTime
        lastTime = timeSec
        val dt = if (previous == null) 0f else (timeSec - previous).coerceIn(0f, 0.05f)
        val speaking = input.speaking > 0.02f || input.interaction == InteractionState.SPEAKING
        val resting = (input.interaction == InteractionState.IDLE ||
            input.interaction == InteractionState.MUTED ||
            input.interaction == InteractionState.OFFLINE) && !speaking
        val coarseInView = input.cameraEnabled && input.gazeX != null && input.gazeY != null

        val base = feed ?: ReflexSensoryFeed(
            cameraEnabled = input.cameraEnabled,
            hasFace = coarseInView,
            attentionFresh = coarseInView,
            speaking = speaking,
            resting = resting,
            speechEnergy = input.speaking.coerceIn(0f, 1f),
            affectionPulse = input.affection,
        )
        val enriched = enrichTemporal(base.copy(
            speaking = base.speaking || speaking,
            resting = if (base.hasFace) false else (base.resting || resting),
            cameraEnabled = base.cameraEnabled || input.cameraEnabled,
            speechEnergy = maxOf(base.speechEnergy, input.speaking.coerceIn(0f, 1f)),
            affectionPulse = base.affectionPulse || input.affection,
        ), dt)

        val next = ReflexDynamics.step(state = state, dt = dt, feed = enriched)
        val g = gain.coerceIn(0.25f, 1.5f)
        state = if (g == 1f) next else blend(state, next, g)
        val action = competition.step(state, dt)
        return applyAction(input, state, action)
    }

    private fun enrichTemporal(feed: ReflexSensoryFeed, dt: Float): ReflexSensoryFeed {
        val faceNow = feed.hasFace && feed.attentionFresh
        val novel = faceNow && feed.trackingId >= 0 &&
            lastTrackingId >= 0 && feed.trackingId != lastTrackingId
        val firstId = faceNow && feed.trackingId >= 0 && lastTrackingId < 0 && !hadFace
        val lost = hadFace && !faceNow
        val recovery = faceNow && recoveringFromLoss
        if (faceNow) {
            continuousTrackSec = (continuousTrackSec + dt).coerceAtMost(600f)
            if (feed.trackingId >= 0) lastTrackingId = feed.trackingId
            recoveringFromLoss = false
        } else {
            continuousTrackSec = 0f
            if (lost) recoveringFromLoss = true
            if (!feed.cameraEnabled) {
                lastTrackingId = -1
                recoveringFromLoss = false
            }
        }
        hadFace = faceNow
        return feed.copy(
            novelTarget = feed.novelTarget || novel || firstId,
            lostTrack = feed.lostTrack || lost,
            lostTrackRecovery = feed.lostTrackRecovery || recovery,
            continuousTrackSec = if (feed.continuousTrackSec > 0f) {
                feed.continuousTrackSec
            } else {
                continuousTrackSec
            },
        )
    }

    private fun blend(from: ReflexState, to: ReflexState, gain: Float): ReflexState {
        fun lerp(a: Float, b: Float): Float = a + (b - a) * gain
        return ReflexState(
            curiosity = lerp(from.curiosity, to.curiosity),
            socialAttention = lerp(from.socialAttention, to.socialAttention),
            fatigue = lerp(from.fatigue, to.fatigue),
            caution = lerp(from.caution, to.caution),
        ).coerced()
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

    fun summaryLabel(): String? {
        val s = state.coerced()
        val action = competition.current.name.lowercase().replace('_', ' ')
        return "Reflex: $action · c${"%.0f".format(s.curiosity * 100)} s${"%.0f".format(s.socialAttention * 100)}"
    }
}
