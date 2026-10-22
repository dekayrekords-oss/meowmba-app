package com.example.meowmba.core.lumen

/**
 * Phase-0/1 Lumen "nervous system" drives (no FlyWire).
 * All channels are unitless 0..1; callers keep them in range.
 */
data class ReflexState(
    val curiosity: Float = 0.35f,
    val socialAttention: Float = 0.25f,
    val fatigue: Float = 0.1f,
    val caution: Float = 0.2f,
) {
    fun coerced(): ReflexState = copy(
        curiosity = curiosity.coerceIn(0f, 1f),
        socialAttention = socialAttention.coerceIn(0f, 1f),
        fatigue = fatigue.coerceIn(0f, 1f),
        caution = caution.coerceIn(0f, 1f),
    )
}

/**
 * Rich sensory snapshot for [ReflexDynamics] — vision / social / speech / touch /
 * motion / thermal. Pure data; no FlyWire. Temporal fields (novel / lost /
 * continuousTrackSec) are filled by the bridge or ViewModel before each step.
 */
data class ReflexSensoryFeed(
    val cameraEnabled: Boolean = false,
    val hasFace: Boolean = false,
    val trackingId: Int = -1,
    val followHand: Boolean = false,
    val speechLikely: Boolean = false,
    val attentionFresh: Boolean = false,
    val attentionStale: Boolean = false,
    val socialCueActive: Boolean = false,
    val mouthOpen: Float = 0f,
    /** Recent pet / affection stroke window. */
    val affectionPulse: Boolean = false,
    /** TTS or talker mouth energy 0..1. */
    val speechEnergy: Float = 0f,
    /** High speech energy / startle-ish audio cue when available. */
    val loudSound: Boolean = false,
    /** Reply-affect / emotion just changed. */
    val emotionChanged: Boolean = false,
    /** Signed arousal from reply affect: + lifts social/curiosity, - lifts caution. */
    val emotionArousal: Float = 0f,
    /** Combined IMU / drive-G magnitude (g-ish, typically 0..2). */
    val motionMagnitude: Float = 0f,
    val thermalCaution: Boolean = false,
    val speaking: Boolean = false,
    val resting: Boolean = false,
    val novelTarget: Boolean = false,
    val lostTrack: Boolean = false,
    /** Face returned after a loss — recovery boost. */
    val lostTrackRecovery: Boolean = false,
    val continuousTrackSec: Float = 0f,
)

/**
 * Cheap continuous update from camera / social / idle / extra drives.
 * Kept pure so it can run on the draw path when the flag is on.
 */
object ReflexDynamics {
    /**
     * Coarse boolean path kept for callers/tests that only have LumenInput-level cues.
     * Prefer [step] with [ReflexSensoryFeed] for the full synapse wiring.
     */
    fun step(
        state: ReflexState,
        dt: Float,
        cameraEnabled: Boolean,
        userInView: Boolean,
        speaking: Boolean,
        resting: Boolean,
    ): ReflexState = step(
        state = state,
        dt = dt,
        feed = ReflexSensoryFeed(
            cameraEnabled = cameraEnabled,
            hasFace = userInView,
            attentionFresh = userInView,
            speaking = speaking,
            resting = resting,
        ),
    )

    fun step(
        state: ReflexState,
        dt: Float,
        feed: ReflexSensoryFeed,
    ): ReflexState {
        val t = dt.coerceIn(0f, 0.05f)
        var curiosity = state.curiosity
        var social = state.socialAttention
        var fatigue = state.fatigue
        var caution = state.caution

        // Curiosity: novel targets + loss-of-track recovery + hand-follow chase.
        if (feed.novelTarget) curiosity = (curiosity + 0.18f).coerceAtMost(1f)
        if (feed.lostTrackRecovery) curiosity = (curiosity + 0.14f).coerceAtMost(1f)
        if (feed.followHand) {
            curiosity += (0.72f - curiosity) * (0.45f * t)
        }

        // Social attention: face lock + talker / speechLikely / mouth.
        if (feed.hasFace && feed.attentionFresh) {
            social += (0.62f - social) * (0.4f * t)
            curiosity += (0.48f - curiosity) * (0.18f * t)
            if (feed.speechLikely || feed.mouthOpen > 0.35f) {
                social += (0.88f - social) * (0.55f * t)
            }
            if (feed.socialCueActive) {
                social = (social + 0.06f).coerceAtMost(1f)
                curiosity = (curiosity + 0.04f).coerceAtMost(1f)
            }
            caution += (0.14f - caution) * (0.12f * t)
        } else if (feed.cameraEnabled) {
            curiosity += (0.55f - curiosity) * (0.25f * t)
            social += (0.18f - social) * (0.22f * t)
        } else {
            social += (0.1f - social) * (0.15f * t)
            caution += (0.25f - caution) * (0.1f * t)
        }

        // Fatigue: accumulates with continuous tracking time.
        if (feed.hasFace && feed.attentionFresh) {
            val trackLoad = (0.025f + feed.continuousTrackSec * 0.0012f).coerceIn(0.025f, 0.09f)
            fatigue += trackLoad * t
        }

        // Caution: stale attention, loss of track, thermal.
        if (feed.attentionStale || feed.lostTrack) {
            caution += (0.58f - caution) * (0.4f * t)
        }
        if (feed.thermalCaution) {
            caution += (0.72f - caution) * (0.45f * t)
            fatigue += 0.03f * t
        }

        // Extra drives: pet/affection, speech energy / loud, reply-affect, IMU/drive motion.
        if (feed.affectionPulse) {
            social = (social + 0.12f).coerceAtMost(1f)
            curiosity = (curiosity + 0.05f).coerceAtMost(1f)
            caution += (0.18f - caution) * 0.35f
            fatigue += (0.07f - fatigue) * 0.25f
        }
        val energy = feed.speechEnergy.coerceIn(0f, 1f)
        if (energy > 0.08f) {
            curiosity += energy * 0.06f
            social += energy * 0.04f
        }
        if (feed.loudSound) {
            curiosity = (curiosity + 0.1f).coerceAtMost(1f)
            caution = (caution + 0.12f).coerceAtMost(1f)
        }
        if (feed.emotionChanged) {
            curiosity = (curiosity + 0.06f).coerceAtMost(1f)
        }
        val arousal = feed.emotionArousal.coerceIn(-1f, 1f)
        if (arousal > 0f) {
            social += arousal * 0.07f
            curiosity += arousal * 0.04f
        } else if (arousal < 0f) {
            caution += (-arousal) * 0.1f
        }
        val motion = feed.motionMagnitude.coerceIn(0f, 2.5f)
        if (motion > 0.12f) {
            val m = (motion - 0.12f).coerceAtMost(1.5f)
            curiosity += m * 0.05f * t * 18f
            caution += m * 0.04f * t * 18f
            fatigue += m * 0.02f * t * 12f
        }

        if (feed.speaking) {
            fatigue += 0.04f * t
            curiosity += (0.3f - curiosity) * (0.1f * t)
        } else if (feed.resting && !feed.hasFace) {
            fatigue += (0.08f - fatigue) * (0.12f * t)
            curiosity += (0.4f - curiosity) * (0.08f * t)
        }

        return ReflexState(curiosity, social, fatigue, caution).coerced()
    }
}
