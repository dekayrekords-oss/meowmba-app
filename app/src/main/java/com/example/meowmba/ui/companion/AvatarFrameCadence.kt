package com.example.meowmba.ui.companion

import android.os.PowerManager

/**
 * Keep animation time real while bounding decorative redraws on high-refresh phones.
 *
 * Two rates, because one number was never right for both jobs. A performed
 * clip or a face being tracked needs the snappier active rate. Idle breathing,
 * drifting weather and background particles do not: they are multi-second
 * cycles that remain readable at a lower idle rate.
 *
 * Root cause of post-1.3.x heat: ACTIVE was briefly ~60 FPS (16 ms). On OLED
 * phones that meant continuous GPU/CPU work even when "just breathing", and
 * THERMAL_STATUS_LIGHT never reduced the admit rate — only MODERATE+ paused
 * via [AvatarWorkPolicy]. Default ACTIVE is now ~30 FPS; user Avatar FPS
 * target (15–60) selects the cool ACTIVE rate; LIGHT halves effective
 * FPS; MODERATE+ refuses admits here as belt-and-suspenders (policy still pauses).
 */
internal class AvatarFrameCadence {
    private var previous = Long.MIN_VALUE

    /**
     * [active] = something is actually performing or being tracked.
     * [fpsTarget] = user ACTIVE target FPS when cool (15–60 inclusive; default 30).
     * [thermalStatus] = [PowerManager] thermal severity.
     */
    fun admit(
        nowNanos: Long,
        active: Boolean = true,
        fpsTarget: Int = DEFAULT_ACTIVE_FPS,
        thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    ): Boolean {
        if (thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) return false
        val target = fpsTarget.coerceIn(MIN_FPS, MAX_FPS)
        val baseFps = if (active) target.toFloat() else (target / 2f).coerceAtLeast(MIN_IDLE_FPS.toFloat())
        val thermalMul = if (thermalStatus >= PowerManager.THERMAL_STATUS_LIGHT) 0.5f else 1f
        val effectiveFps = (baseFps * thermalMul).coerceAtLeast(1f)
        val intervalNs = (1_000_000_000.0 / effectiveFps).toLong().coerceAtLeast(MIN_INTERVAL_NS)
        if (previous != Long.MIN_VALUE && nowNanos - previous in 0 until intervalNs) return false
        previous = nowNanos
        return true
    }

    companion object {
        const val DEFAULT_ACTIVE_FPS = 30
        const val MIN_FPS = 15
        const val MAX_FPS = 60
        const val MIN_IDLE_FPS = 8
        /** Hard floor (~120 Hz) so a mis-set target cannot peg the vsync loop. */
        const val MIN_INTERVAL_NS = 8_000_000L
        /** ~30 FPS default active interval (documentation / tests). */
        const val ACTIVE_INTERVAL_NS = 33_000_000L
        /** ~15 FPS ambient at default target. */
        const val IDLE_INTERVAL_NS = 66_000_000L
    }
}
