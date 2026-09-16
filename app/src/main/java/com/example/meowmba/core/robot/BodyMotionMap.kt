package com.example.meowmba.core.robot

import com.example.meowmba.ui.companion.AnimFrame
import com.example.meowmba.ui.companion.CustomAnimationSpec
import kotlin.math.abs

/**
 * Turns a face clip into body motion.
 *
 * Pure by construction: [BodyProfile] is an argument, not ambient state, so
 * "DESK never drives" is a property a test can prove exhaustively rather than
 * a promise made in a comment.
 *
 * ## On axes
 *
 * `headTiltDeg` is ROLL — a head cocked sideways ("negative=left"). The neck
 * characteristic fed1 is PITCH — up and down. They are different axes, and
 * mapping one to the other (as FaceMotorMap used to) makes a cocked head nod.
 * The channel that means pitch is `pupilGazeUp`.
 *
 * ## On turn direction
 *
 * LooiBleProtocol.rotateFrame carries no direction parameter, so a pair of
 * opposite turns would rotate the same way twice rather than cancelling.
 * [BodyVerb.Turn.sign] is populated because the information is real and free,
 * but LooiBleBodyOutput ignores it. Rotation is in-place, so this is a heading
 * problem, never a falling problem.
 */
object BodyMotionMap {

    /** Below these, a channel is noise and earns no command. */
    private const val TILT_DEADZONE_DEG = 4f
    private const val BOUNCE_DEADZONE = 0.15f
    private const val WAG_DEADZONE = 0.15f

    private const val TURN_SPEED_PERCENT = 25
    private const val TURN_TICKS = 3
    private const val NECK_BOB_SCALE = 0.5f
    private const val DRIVE_SCALE = 0.5f
    private const val DRIVE_PULSE_MS = 200

    /** A glow this dim reads as mood lighting, not "light up". */
    private const val GLOW_LUMA_THRESHOLD = 0.25f
    private const val SPARKLE_THRESHOLD = 0.5f

    fun cues(spec: CustomAnimationSpec, profile: BodyProfile): List<BodyCue> {
        val out = mutableListOf<BodyCue>()
        var lightOn = false

        spec.frames.forEach { frame ->
            out += neckCues(frame)
            out += tiltCues(frame)
            out += bounceCues(frame, profile)
            out += wagCues(frame)
            if (!lightOn && wantsLight(frame)) {
                lightOn = true
                out += BodyCue(frame.atMs, BodyVerb.Light(true))
            }
        }

        val end = spec.durationMs
        if (lightOn) out += BodyCue(end, BodyVerb.Light(false))
        // Unconditional: a clip always ends with the base stopped.
        out += BodyCue(end, BodyVerb.Brake)

        // A bounce may ask to recover 200 ms after its source keyframe. If
        // that frame is near the clip end, never restart the neck/drive after
        // the unconditional final brake or extend this clip's motion lease.
        return out.filter { it.atMs <= end }.sortedBy { it.atMs }
    }

    /** gaze 1.0 = looking up = neck pitch -1. gaze 0.5 = center = 0. */
    private fun neckCues(frame: AnimFrame): List<BodyCue> {
        val gaze = frame.pupilGazeUp ?: return emptyList()
        return listOf(BodyCue(frame.atMs, BodyVerb.Neck(1f - 2f * gaze)))
    }

    private fun tiltCues(frame: AnimFrame): List<BodyCue> {
        val tilt = frame.headTiltDeg ?: return emptyList()
        if (abs(tilt) < TILT_DEADZONE_DEG) return emptyList()
        return listOf(
            BodyCue(
                frame.atMs,
                BodyVerb.Turn(TURN_SPEED_PERCENT, TURN_TICKS, if (tilt < 0) -1 else 1),
            )
        )
    }

    private fun bounceCues(frame: AnimFrame, profile: BodyProfile): List<BodyCue> {
        val bounce = frame.bodyBounce ?: return emptyList()
        if (abs(bounce) < BOUNCE_DEADZONE) return emptyList()
        return when (profile) {
            // A bob works in both directions: the neck can go up and down.
            BodyProfile.DESK -> listOf(
                BodyCue(frame.atMs, BodyVerb.Neck(-bounce * NECK_BOB_SCALE)),
                BodyCue(frame.atMs + DRIVE_PULSE_MS, BodyVerb.Neck(0f)),
            )
            // Drive is forward-only, so a downward squish has no floor analogue.
            BodyProfile.FLOOR -> if (bounce <= 0f) emptyList() else listOf(
                BodyCue(frame.atMs, BodyVerb.Drive(bounce * DRIVE_SCALE)),
                BodyCue(frame.atMs + DRIVE_PULSE_MS, BodyVerb.Brake),
            )
        }
    }

    private fun wagCues(frame: AnimFrame): List<BodyCue> {
        val wag = frame.tailWag ?: return emptyList()
        if (abs(wag) < WAG_DEADZONE) return emptyList()
        return listOf(
            BodyCue(
                frame.atMs,
                BodyVerb.Turn(TURN_SPEED_PERCENT, TURN_TICKS, if (wag < 0) -1 else 1),
            )
        )
    }

    private fun wantsLight(frame: AnimFrame): Boolean {
        if ((frame.sparkle ?: 0f) > SPARKLE_THRESHOLD) return true
        val argb = frame.glowColorHex ?: return false
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        // Rec. 601 luma — good enough to tell "lit" from "moody".
        return (0.299f * r + 0.587f * g + 0.114f * b) > GLOW_LUMA_THRESHOLD
    }
}
