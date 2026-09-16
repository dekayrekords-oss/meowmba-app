package com.example.meowmba.core.car

import kotlin.math.sqrt

/** Input conditioning only: crash thresholds and decisions remain in DrivingDetector. */
internal class DrivingSensorInput {
    data class Motion(
        val gForce: Float,
        val accel: CarDynamics.Accel,
        val timestampNanos: Long,
        val continuous: Boolean,
    )

    private val gravity = FloatArray(3)
    private var gravityAt = 0L
    private var lastRawAt = 0L
    private var lastLinearAt = 0L
    private var lastGravityAt = 0L
    private var motionAt = 0L

    fun reset() {
        gravity.fill(0f)
        gravityAt = 0L
        lastRawAt = 0L
        lastLinearAt = 0L
        lastGravityAt = 0L
        motionAt = 0L
    }

    fun clearMotion() { motionAt = 0L }
    fun motionIsFresh(nowNanos: Long): Boolean = fresh(motionAt, nowNanos)

    fun updateGravity(values: FloatArray, timestampNanos: Long, nowNanos: Long): Boolean {
        if (!valid(values, timestampNanos, nowNanos, lastGravityAt) || magnitude(values) < 0.5f) {
            gravityAt = 0L
            return false
        }
        lastGravityAt = timestampNanos
        values.copyInto(gravity, endIndex = 3)
        gravityAt = timestampNanos
        return true
    }

    /** Used for gravity only when a linear sensor exists but TYPE_GRAVITY does not. */
    fun updateRawGravity(values: FloatArray, timestampNanos: Long, nowNanos: Long): Boolean {
        if (!valid(values, timestampNanos, nowNanos, lastRawAt)) {
            gravityAt = 0L
            return false
        }
        lastRawAt = timestampNanos
        if (!fresh(gravityAt, nowNanos)) {
            // Seed from the first complete sample: stationary portrait/landscape
            // phones must not spend startup reporting their own gravity as force.
            values.copyInto(gravity, endIndex = 3)
        } else {
            for (i in 0..2) gravity[i] = gravity[i] * 0.90f + values[i] * 0.10f
        }
        gravityAt = timestampNanos
        return true
    }

    fun raw(values: FloatArray, timestampNanos: Long, nowNanos: Long): Motion? {
        if (!updateRawGravity(values, timestampNanos, nowNanos)) return null
        return motion(FloatArray(3) { values[it] - gravity[it] }, timestampNanos, nowNanos)
    }

    fun linear(values: FloatArray, timestampNanos: Long, nowNanos: Long): Motion? {
        if (!valid(values, timestampNanos, nowNanos, lastLinearAt)) return null
        lastLinearAt = timestampNanos
        return motion(values, timestampNanos, nowNanos)
    }

    private fun motion(values: FloatArray, timestampNanos: Long, nowNanos: Long): Motion? {
        val gForce = magnitude(values) / G
        if (!gForce.isFinite()) return null
        val continuous = motionAt > 0L && timestampNanos - motionAt in 1..MAX_SAMPLE_AGE_NS
        motionAt = timestampNanos
        // Magnitude still reaches the unchanged impact detector if orientation
        // is unavailable. Direction cannot be inferred from a zero/stale vector.
        val accel = if (fresh(gravityAt, nowNanos) && magnitude(gravity) >= 0.5f) {
            CarDynamics.decompose(values, gravity)
        } else CarDynamics.Accel()
        return Motion(gForce, accel, timestampNanos, continuous)
    }

    private fun valid(values: FloatArray, at: Long, now: Long, previous: Long): Boolean =
        values.size >= 3 && values[0].isFinite() && values[1].isFinite() && values[2].isFinite() &&
            magnitude(values).isFinite() &&
            at > previous && fresh(at, now)

    private fun fresh(at: Long, now: Long): Boolean = at > 0L && now - at in 0..MAX_SAMPLE_AGE_NS
    private fun magnitude(v: FloatArray): Float = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    companion object {
        const val G = 9.80665f
        const val MAX_SAMPLE_AGE_NS = 2_000_000_000L
    }
}

/** A direction/category change is a new maneuver even if both samples exceed a threshold. */
internal data class DrivingManeuver(val turn: Int = 0, val braking: Boolean = false) {
    companion object {
        fun from(lateralG: Float, longitudinalG: Float, hardTurnG: Float, hardBrakeG: Float) =
            DrivingManeuver(
                turn = when {
                    lateralG >= hardTurnG -> 1
                    lateralG <= -hardTurnG -> -1
                    else -> 0
                },
                braking = longitudinalG <= -hardBrakeG,
            )
    }
}
