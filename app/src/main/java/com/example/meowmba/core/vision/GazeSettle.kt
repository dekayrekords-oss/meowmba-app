package com.example.meowmba.core.vision

/**
 * Face-lock rest. A locked face box jitters ±0.05 every frame; publishing
 * that makes the avatar fidget. Inside [IN] we look straight ahead. Once
 * settled, stay put until the target leaves [OUT]. Hands skip this — a wave
 * should still yank the eyes.
 */
object GazeSettle {
    const val IN = 0.14f
    const val OUT = 0.26f

    data class Result(val x: Float, val y: Float, val settled: Boolean)

    fun apply(x: Float, y: Float, settled: Boolean, follow: FollowSource): Result {
        if (follow == FollowSource.HAND) return Result(x, y, settled = false)
        // Selfie framing parks a face in the upper third (y≈-0.5). That is
        // not "off center" — only X should break a lock.
        val ax = kotlin.math.abs(x)
        val now = if (settled) ax < OUT else ax < IN
        return if (now) Result(0f, 0f, settled = true) else Result(x, y, settled = false)
    }
}
