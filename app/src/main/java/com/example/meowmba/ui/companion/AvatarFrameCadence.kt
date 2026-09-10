package com.example.meowmba.ui.companion

/**
 * Keep animation time real while bounding decorative redraws on 120-Hz phones.
 *
 * Two rates, because one number was never right for both jobs. A performed
 * clip or a face being tracked needs the original ~31 FPS. Idle breathing,
 * drifting weather and background particles do not: they are multi-second
 * cycles that are indistinguishable at 15, and redrawing them 31 times a
 * second was paying for frames nobody can see — in heat, not just in CPU.
 *
 * The idle rate only ever *lowers* work. Nothing here can raise the frame rate
 * above what the old fixed 32 ms allowed, so this cannot make a hot device
 * hotter than it already was.
 */
internal class AvatarFrameCadence {
    private var previous = Long.MIN_VALUE

    /**
     * [active] = something is actually performing or being tracked. Everything
     * else is ambient drift and runs at the slow rate.
     */
    fun admit(nowNanos: Long, active: Boolean = true): Boolean {
        val intervalNs = if (active) ACTIVE_INTERVAL_NS else IDLE_INTERVAL_NS
        if (previous != Long.MIN_VALUE && nowNanos - previous in 0 until intervalNs) return false
        previous = nowNanos
        return true
    }

    private companion object {
        /** ~31 FPS. The previous fixed value, kept exactly. */
        const val ACTIVE_INTERVAL_NS = 32_000_000L
        /** ~15 FPS. Ambient motion only. */
        const val IDLE_INTERVAL_NS = 66_000_000L
    }
}
