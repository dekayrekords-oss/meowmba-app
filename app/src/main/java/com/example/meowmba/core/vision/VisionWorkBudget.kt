package com.example.meowmba.core.vision

/** Wall-clock admission, independent of whether the camera delivers 15, 30 or 60 FPS. */
internal class VisionWorkBudget {
    private var lastFrame = Long.MIN_VALUE
    private var lastPose = Long.MIN_VALUE

    fun admit(nowMs: Long, hasRecentFace: Boolean, thermalStatus: Int): Boolean {
        if (thermalStatus >= 3) return false
        val interval = when {
            thermalStatus >= 2 -> if (hasRecentFace) 500L else 1200L
            thermalStatus >= 1 -> if (hasRecentFace) 250L else 800L
            else -> if (hasRecentFace) 125L else 400L
        }
        if (lastFrame != Long.MIN_VALUE && nowMs - lastFrame in 0 until interval) return false
        lastFrame = nowMs
        return true
    }

    fun admitPose(nowMs: Long, hasRecentFace: Boolean, thermalStatus: Int, followOnly: Boolean): Boolean {
        if (followOnly || hasRecentFace || thermalStatus > 0) return false
        if (lastPose != Long.MIN_VALUE && nowMs - lastPose in 0 until 1000L) return false
        lastPose = nowMs
        return true
    }
}
