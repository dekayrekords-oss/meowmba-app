package com.example.meowmba.core.robot

import com.example.meowmba.ui.companion.AnimFrame
import com.example.meowmba.ui.companion.AvatarPose
import com.example.meowmba.ui.companion.CatMotionEngine
import com.example.meowmba.ui.companion.ComposedExpression
import com.example.meowmba.ui.companion.CustomAnimationSpec
import com.example.meowmba.ui.companion.MotionPose
import com.example.meowmba.ui.companion.applyTo
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compiles the face's actual composed score into bounded physical command intent.
 * The former three phase mapper guessed a gesture/light from the body enum and
 * lost bob reversals, a coil's held anticipation, and a snap's early strike.
 * This samples CatMotionEngine instead, including its existing body springs.
 *
 * This is a deterministic simulation from the score's neutral starting pose,
 * not a measurement of the robot or a claim of identical physical dynamics.
 * The face may carry spring velocity from an interrupted clip; the physical
 * adapter has no measured joint state to reproduce that. Its existing safety
 * lease, quarantine, scheduler and transport remain authoritative and unchanged.
 */
object EmotionMap {
    internal const val MAX_DURATION_MS = 10_000
    internal const val MAX_BODY_AMP = 2f
    internal const val SIMULATION_HZ = 120
    private const val FACE_RADIUS = 200f
    private const val NECK_BOB_SCALE = .5f // Same interpretation as BodyMotionMap's DESK bob.
    private const val TILT_DEADZONE_DEG = 4f

    fun cues(expr: ComposedExpression, profile: BodyProfile): List<BodyCue> {
        val durationMs = durationMs(expr) ?: return listOf(BodyCue(0, BodyVerb.Brake))
        if (!expr.bodyAmp.isFinite() || expr.bodyAmp !in 0f..MAX_BODY_AMP) {
            return listOf(BodyCue(0, BodyVerb.Brake))
        }
        // An effect shorter than one existing neck interval has no representable
        // physical choreography. Never stretch it or generate a faster pulse.
        if (durationMs < BodyNeckProfile.SAMPLE_MS || expr.bodyAmp == 0f) {
            return listOf(BodyCue(durationMs, BodyVerb.Brake))
        }
        val spec = sampledScore(expr, profile, durationMs)
            ?: return listOf(BodyCue(0, BodyVerb.Brake))
        // BodyScheduler already runs BodyNeckProfile.expand under its existing
        // fenced lease. Keep that single application; do not double-profile or
        // mark raw targets as profiled outside the lease. The terminal brake
        // remains at the expression's own cutoff, with no post-clip decay lease.
        return BodyMotionMap.cues(spec, profile).map { cue ->
            val neck = cue.verb as? BodyVerb.Neck
            // Gaze->pitch float arithmetic can exceed an exact endpoint by one
            // ulp. Keep even simulated intent inside the existing motor bound.
            if (neck == null) cue else cue.copy(verb = neck.copy(
                pitch = neck.pitch.coerceIn(-RobotManager.NECK_RANGE, RobotManager.NECK_RANGE)))
        }
    }

    private fun durationMs(expr: ComposedExpression): Int? {
        val seconds = expr.durationSeconds
        if (!seconds.isFinite() || seconds <= 0f || seconds > MAX_DURATION_MS / 1000f) return null
        return (seconds * 1000f).roundToInt().takeIf { it in 1..MAX_DURATION_MS }
    }

    private fun sampledScore(expr: ComposedExpression, profile: BodyProfile, durationMs: Int): CustomAnimationSpec? {
        val engine = CatMotionEngine().also { it.play(expr) }
        val frames = mutableListOf(AnimFrame(0))
        val spacingMs = BodyNeckProfile.SAMPLE_MS.toInt()
        val ticksPerSample = SIMULATION_HZ * spacingMs / 1000
        require(ticksPerSample > 0 && SIMULATION_HZ * spacingMs % 1000 == 0)
        var tick = 0
        // Each retained point lies on the existing 250 ms command cadence;
        // the finer simulation captures face spring dynamics, never wire writes.
        for (atMs in spacingMs until durationMs step spacingMs) {
            val targetTick = atMs * SIMULATION_HZ / 1000
            var motion = engine.restPose()
            while (tick < targetTick) {
                motion = engine.update(1f / SIMULATION_HZ, FACE_RADIUS) ?: engine.restPose()
                tick++
            }
            if (!motionIsFinite(motion)) return null
            frames += frameFor(motion, atMs, profile)
        }
        // An empty terminal keyframe fixes duration without inventing a final
        // neck move less than 250 ms after the preceding point. Brake is exact.
        frames += AnimFrame(durationMs)
        return CustomAnimationSpec("emotion:${expr.body.name.lowercase()}", frames)
    }

    private fun frameFor(motion: MotionPose, atMs: Int, profile: BodyProfile): AnimFrame {
        // Use the score's actual radius-normalized vertical travel for pitch.
        // Visual perspective scale/squash has no neck axis; mixing it into the
        // bob nearly cancels COIL's anticipation in the legacy face adapter.
        // Ear asymmetry, eyelids and jaw have no matching actuator here; notably,
        // do not turn the base just because one visual ear swings backwards.
        val face = motion.applyTo(AvatarPose(), FACE_RADIUS)
        val pitch = (motion.offsetY / (FACE_RADIUS * .25f) * NECK_BOB_SCALE)
            .coerceIn(-RobotManager.NECK_RANGE, RobotManager.NECK_RANGE)
        val tilt = motion.headTiltDeg.coerceIn(-25f, 25f)
        return AnimFrame(
            atMs = atMs,
            // Absolute neck target through the existing gaze-to-pitch mapper.
            // Passing sampled DESK bounce directly would inject center pulses
            // at +200ms and erase its held/reversing score before the next sample.
            pupilGazeUp = (.5f - pitch * .5f).coerceIn(0f, 1f),
            headTiltDeg = tilt,
            // FLOOR retains the pre-existing positive-only drive pulse mapping.
            // Its +200ms brake remains unchanged and cannot overrun the clip.
            bodyBounce = if (profile == BodyProfile.FLOOR) face.bodyBounce else null,
            // One turn proposal per point; tilt and wag both address the same base.
            tailWag = if (abs(tilt) < TILT_DEADZONE_DEG) face.tailWag else null,
        )
    }

    private fun motionIsFinite(motion: MotionPose): Boolean =
        motion.offsetX.isFinite() && motion.offsetY.isFinite() &&
            motion.headTiltDeg.isFinite() && motion.scaleDelta.isFinite()
}
