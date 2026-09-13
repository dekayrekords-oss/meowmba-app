package com.example.meowmba.core.neural

/** Cosmetic clips only. Busy/thermal/privacy ownership stays with the host. */
class NeuralMotorDirector {
    private var previous: BehaviorAction? = null
    private var lastPlayedAt: Long? = null
    fun choose(action: BehaviorAction, nowMs: Long, allowed: Boolean, busy: Boolean): String? {
        if (!allowed) { previous = null; return null }
        if (busy || action == previous) return null
        previous = action
        val clip = when (action) {
            BehaviorAction.STARTLE -> "startled_peek"
            BehaviorAction.INSPECT -> "curious_sniff"
            BehaviorAction.SETTLE -> "drowsy_drift"
            else -> return null
        }
        val last = lastPlayedAt
        if (last != null && (nowMs < last || nowMs - last < 10_000L)) return null
        lastPlayedAt = nowMs
        return clip
    }
}
