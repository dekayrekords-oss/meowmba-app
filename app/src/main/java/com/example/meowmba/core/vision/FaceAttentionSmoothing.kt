package com.example.meowmba.core.vision

import kotlin.math.exp

/**
 * Time-normalized detector smoothing: quiet boxes keep the established ~78ms
 * rest filter, while a real displacement shortens its lag to 30ms. Low-rate
 * body-follow samples no longer pay the old fixed-alpha delay four times over.
 * This filters only observed positions; it never extrapolates or changes capture.
 */
internal object FaceAttentionSmoothing {
    fun alpha(elapsedMs: Long, displacement: Float, hasHistory: Boolean): Float {
        if (!hasHistory) return 1f
        val dt = (if (elapsedMs > 0L) elapsedMs else 33L).coerceIn(1L, 250L).toFloat()
        val error = displacement.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        val amount = ((error - 0.035f) / 0.40f).coerceIn(0f, 1f)
        val fast = amount * amount * (3f - 2f * amount)
        val tau = 78f + (30f - 78f) * fast
        return (1f - exp(-dt / tau)).coerceIn(0f, 1f)
    }
}
