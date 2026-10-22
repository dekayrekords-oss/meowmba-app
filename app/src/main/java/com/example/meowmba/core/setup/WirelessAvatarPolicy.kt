package com.example.meowmba.core.setup

/** Edge-triggered, not tied to battery percentage/status (FULL still has wireless power). */
internal class WirelessAvatarPolicy {
    private var handled = false
    private var disconnectedAt: Long? = null

    fun update(plugged: Int, enabled: Boolean, now: Long): Boolean {
        if (!enabled) { handled = false; disconnectedAt = null; return false }
        if (plugged < 0) return false // Missing reading must not invent a disconnection.
        if (plugged and 4 == 0) {
            if (disconnectedAt == null) disconnectedAt = now
            return false
        }
        if (disconnectedAt?.let { now - it >= 5_000 } == true) handled = false
        disconnectedAt = null
        if (handled) return false
        handled = true
        return true
    }
}
