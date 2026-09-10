package com.example.meowmba.ui.companion

/** Keep animation time real while bounding decorative redraws on 120-Hz phones. */
internal class AvatarFrameCadence {
    private var previous = Long.MIN_VALUE
    fun admit(nowNanos: Long): Boolean {
        if (previous != Long.MIN_VALUE && nowNanos - previous in 0 until 32_000_000L) return false
        previous = nowNanos
        return true
    }
}
