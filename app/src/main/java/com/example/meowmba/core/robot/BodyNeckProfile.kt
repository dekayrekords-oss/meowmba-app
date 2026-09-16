package com.example.meowmba.core.robot

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Minimum-jerk position targets on the existing neck channel, not a servo
 * feedback controller. The fifth-order curve starts/ends with zero velocity
 * and acceleration, never overshoots, and stays inside the existing envelope.
 * Samples retain the existing 250 ms BLE budget. Fast face beats are attenuated
 * to the command-speed budget instead of making the physical neck snap or
 * extending the clip past its stop. Actual travel and motor dynamics are unknown.
 */
internal object BodyNeckProfile {
    const val SAMPLE_MS = LooiBleBodyOutput.NECK_SPACING_MS
    private const val MIN_DURATION_MS = 750L
    private const val MAX_SPEED_PER_SECOND = 0.7f
    // The maximum derivative of 6t^5 - 15t^4 + 10t^3 is 1.875.
    private const val PEAK_SLOPE = 1.875f
    private const val MAX_TIMELINE_MS = 10_000

    fun ease(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return t * t * t * (10f + t * (-15f + 6f * t))
    }

    fun sample(from: Float, to: Float, progress: Float): Float =
        (from + (to - from) * ease(progress)).coerceIn(-RobotManager.NECK_RANGE, RobotManager.NECK_RANGE)

    fun durationMs(from: Float, to: Float): Long {
        require(from.isFinite() && to.isFinite())
        val distance = abs(bounded(to) - bounded(from))
        val minimum = maxOf(MIN_DURATION_MS.toDouble(), distance * PEAK_SLOPE / MAX_SPEED_PER_SECOND * 1000.0)
        return (ceil(minimum / SAMPLE_MS) * SAMPLE_MS).toLong()
    }

    /** Only neck cues are changed; brake, light, turn and drive retain exact times. */
    fun expand(cues: List<BodyCue>): List<BodyCue> {
        val result = mutableListOf<BodyCue>()
        var previous: BodyCue? = null
        val sorted = cues.sortedBy { it.atMs }
        // Gaze and bounce can both emit a neck target at the same instant.
        // One joint needs one target; retain their existing last-writer order.
        val finalNeckAt = mutableMapOf<Int, Int>()
        sorted.forEachIndexed { index, cue -> if (cue.verb is BodyVerb.Neck) finalNeckAt[cue.atMs] = index }
        sorted.forEachIndexed { index, cue ->
            val neck = cue.verb as? BodyVerb.Neck
            if (neck == null) {
                result += cue
                if (cue.verb is BodyVerb.Brake) previous = null
                return@forEachIndexed
            }
            if (finalNeckAt[cue.atMs] != index) return@forEachIndexed
            if (!neck.pitch.isFinite() || cue.atMs !in 0..MAX_TIMELINE_MS) {
                previous = null
                return@forEachIndexed
            }
            val prior = previous
            val from = (prior?.verb as? BodyVerb.Neck)?.pitch
            val dt = prior?.let { cue.atMs - it.atMs } ?: 0
            var target = bounded(neck.pitch)
            if (prior != null && from != null && dt > 0) {
                val maxDistance = dt / 1000f * MAX_SPEED_PER_SECOND / PEAK_SLOPE
                target = from + (target - from).coerceIn(-maxDistance, maxDistance)
                var at = prior.atMs.toLong() + SAMPLE_MS
                while (at < cue.atMs) {
                    result += BodyCue(at.toInt(), BodyVerb.Neck(
                        sample(from, target, (at - prior.atMs).toFloat() / dt), profiled = true))
                    at += SAMPLE_MS
                }
            }
            // The first point has no measured initial position. Do not invent
            // a center or replay the previous connection's pose to approach it.
            val point = BodyCue(cue.atMs, BodyVerb.Neck(target, profiled = true))
            result += point
            previous = point
        }
        return result.sortedBy { it.atMs }
    }

    private fun bounded(value: Float): Float = value.coerceIn(-RobotManager.NECK_RANGE, RobotManager.NECK_RANGE)
}
