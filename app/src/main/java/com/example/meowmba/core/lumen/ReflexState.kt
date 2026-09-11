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
 * Cheap continuous update from camera / social / idle cues.
 * Kept pure so it can run on the draw path when the flag is on.
 */
object ReflexDynamics {
    fun step(
        state: ReflexState,
        dt: Float,
        cameraEnabled: Boolean,
        userInView: Boolean,
        speaking: Boolean,
        resting: Boolean,
    ): ReflexState {
        val t = dt.coerceIn(0f, 0.05f)
        var curiosity = state.curiosity
        var social = state.socialAttention
        var fatigue = state.fatigue
        var caution = state.caution

        if (cameraEnabled && userInView) {
            social += (0.55f - social) * (0.35f * t)
            curiosity += (0.45f - curiosity) * (0.2f * t)
            caution += (0.15f - caution) * (0.15f * t)
        } else if (cameraEnabled) {
            curiosity += (0.55f - curiosity) * (0.25f * t)
            social += (0.15f - social) * (0.2f * t)
        } else {
            social += (0.1f - social) * (0.15f * t)
            caution += (0.25f - caution) * (0.1f * t)
        }

        if (speaking) {
            fatigue += 0.04f * t
            curiosity += (0.3f - curiosity) * (0.1f * t)
        } else if (resting) {
            fatigue += (0.08f - fatigue) * (0.12f * t)
            curiosity += (0.4f - curiosity) * (0.08f * t)
        }

        return ReflexState(curiosity, social, fatigue, caution).coerced()
    }
}
