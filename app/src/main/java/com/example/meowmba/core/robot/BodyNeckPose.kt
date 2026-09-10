package com.example.meowmba.core.robot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Ease an authored target from the last submitted pitch, then retain its lease
 * through a bounded settling interval. With no known command after a brake or
 * connection, retain the old first-pose retry instead of inventing a position.
 *
 * The same lease owns the profile and settling interval, so following and mood
 * cannot overwrite it. Cancellation releases/brakes that lease; safety or a
 * new connection invalidates it before another pitch can be submitted.
 */
internal suspend fun BodySession.holdNeckPose(pitch: Float): Boolean {
    try {
        if (!pitch.isFinite()) return false
        val target = pitch.coerceIn(-RobotManager.NECK_RANGE, RobotManager.NECK_RANGE)
        val start = lastCommandedNeckPitch
        if (start != null && start.isFinite()) {
            // Mark ownership before the first delay, even if the identical
            // start sample is coalesced. Cancellation must still brake now.
            if (!submit(BodyVerb.Neck(start, profiled = true))) return false
            val duration = BodyNeckProfile.durationMs(start, target)
            var elapsed = BodyNeckProfile.SAMPLE_MS
            while (elapsed <= duration) {
                delay(BodyNeckProfile.SAMPLE_MS)
                val point = BodyNeckProfile.sample(start, target, elapsed.toFloat() / duration)
                if (!submit(BodyVerb.Neck(point, profiled = true))) return false
                elapsed += BodyNeckProfile.SAMPLE_MS
            }
        } else {
            // No joint feedback / no command since brake: retain the existing
            // bounded first-pose contract, never assume the neck is centered.
            if (!submit(BodyVerb.Neck(target))) return false
            delay(LooiBleBodyOutput.NECK_SPACING_MS)
            if (!submit(BodyVerb.Neck(target))) return false
        }
        // Retain ownership for another bounded interval after the confirmed
        // retry. This is command timing, not a claim of measured servo travel.
        delay(LooiBleBodyOutput.NECK_SPACING_MS)
        return isValid
    } catch (cancelled: CancellationException) {
        finish()
        throw cancelled
    }
}
