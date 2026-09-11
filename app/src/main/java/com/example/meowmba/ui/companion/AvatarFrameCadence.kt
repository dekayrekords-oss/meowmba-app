package com.example.meowmba.ui.companion

/**
 * Keep animation time real while bounding decorative redraws on 120-Hz phones.
 *
 * Two rates, because one number was never right for both jobs. A performed
 * clip or a face being tracked needs the snappy active rate. Idle breathing,
 * drifting weather and background particles do not: they are multi-second
 * cycles that remain readable at a lower idle rate.
 *
 * Default active admit is ~60 FPS (16 ms). AvatarWorkPolicy still stops
 * decorative work entirely at THERMAL_STATUS_MODERATE or above — this cadence
 * never overrides that throttle; it only applies while work is allowed.
 */
internal class AvatarFrameCadence {
    private var previous = Long.MIN_VALUE

    /**
     * [active] = something is actually performing or being tracked. Everything
     * else is ambient drift and runs at the idle rate.
     */
    fun admit(nowNanos: Long, active: Boolean = true): Boolean {
        val intervalNs = if (active) ACTIVE_INTERVAL_NS else IDLE_INTERVAL_NS
        if (previous != Long.MIN_VALUE && nowNanos - previous in 0 until intervalNs) return false
        previous = nowNanos
        return true
    }

    companion object {
        /** ~60 FPS when AvatarWorkPolicy allows (cool / interactive / started). */
        const val ACTIVE_INTERVAL_NS = 16_000_000L
        /** ~30 FPS ambient. Still cooler than active; was historically ~15 FPS. */
        const val IDLE_INTERVAL_NS = 33_000_000L
    }
}
