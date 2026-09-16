package com.example.meowmba.core.audio

/** Small delivery adjustments, captured when speech is queued. Engines may
 * support only rate or neither parameter; this never delays response onset.
 */
data class VoiceExpression(val pitch: Float = 1f, val rate: Float = 1f) {
    fun bounded() = VoiceExpression(
        pitch.takeIf { it.isFinite() }?.coerceIn(.9f, 1.1f) ?: 1f,
        rate.takeIf { it.isFinite() }?.coerceIn(.9f, 1.1f) ?: 1f,
    )
}
