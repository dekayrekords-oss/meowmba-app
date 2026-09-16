package com.example.meowmba.ui.companion

/** One vsync sample advances ambient motion and the current performance together. */
internal class AvatarFrameClock {
    private var previousNanos: Long? = null
    private var performanceId: Long? = null
    private var performanceStartNanos = 0L
    private var activeSeconds = 0.0

    data class Tick(val seconds: Float, val dt: Float, val performanceMillis: Long)

    fun step(nowNanos: Long, id: Long?): Tick {
        val previous = previousNanos
        if (previous != null && nowNanos <= previous) return Tick(activeSeconds.toFloat(), 0f,
            if (performanceId == null) 0 else ((previous - performanceStartNanos) / 1_000_000L).coerceAtLeast(0))
        val dt = if (previous == null) 0.0 else ((nowNanos - previous) / 1_000_000_000.0).coerceIn(0.0, .05)
        previousNanos = nowNanos
        activeSeconds += dt
        if (id != performanceId) {
            performanceId = id
            performanceStartNanos = nowNanos
        }
        return Tick(activeSeconds.toFloat(), dt.toFloat(),
            if (id == null) 0 else ((nowNanos - performanceStartNanos) / 1_000_000L).coerceAtLeast(0))
    }

    /** Hidden performances are cancelled by the owner; ambient physiology freezes. */
    fun pause() { previousNanos = null; performanceId = null }
}
