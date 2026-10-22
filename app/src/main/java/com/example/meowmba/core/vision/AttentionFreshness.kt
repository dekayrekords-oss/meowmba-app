package com.example.meowmba.core.vision

/** Monotonic camera observation lifetime, independent of delivery or UI frame rate. */
object AttentionFreshness {
    const val STALE_AFTER_MS = 500L
    const val WATCHDOG_PERIOD_MS = 100L

    fun isFresh(observedAtMs: Long, nowMs: Long): Boolean =
        observedAtMs >= 0L && nowMs >= observedAtMs && nowMs - observedAtMs < STALE_AFTER_MS

    /** The owner serializes access. A rebind/disable invalidates every old detector result. */
    class Session {
        var generation = 0L
            private set
        var active = false
            private set

        fun start(): Long {
            active = true
            return invalidate()
        }

        fun stop() {
            active = false
            invalidate()
        }

        fun invalidate(): Long = ++generation

        fun accepts(token: Long, observedAtMs: Long, nowMs: Long): Boolean =
            active && token == generation && isFresh(observedAtMs, nowMs)
    }
}
