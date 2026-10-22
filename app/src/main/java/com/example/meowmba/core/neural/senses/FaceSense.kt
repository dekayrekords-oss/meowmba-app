package com.example.meowmba.core.neural.senses

import com.example.meowmba.core.neural.SensoryEvent
import com.example.meowmba.core.neural.SensoryKind
import com.example.meowmba.core.neural.SensorySource
import com.example.meowmba.core.vision.FaceTrack
import com.example.meowmba.core.vision.FollowSource
import com.example.meowmba.core.vision.SocialCue
import kotlin.math.abs
import kotlin.math.exp

/**
 * Maps the vision pipeline's [FaceTrack] into typed sensory events.
 *
 * Emits, per sample:
 * - a continuous [SensoryKind.FACE_PRESENCE] channel: presence, confidence,
 *   proximity (from the size proxy), horizontal and vertical offset, whether
 *   the head is turned toward the device, and how many faces are in view;
 * - [SensoryKind.FACE_ARRIVED] / [SensoryKind.FACE_LOST] on debounced presence edges;
 * - [SensoryKind.LOOMING] when the face grows faster than [loomingRatePerSec],
 *   i.e. something approaching quickly, once per approach;
 * - [SensoryKind.VISUAL_MOTION] and [SensoryKind.WAVE] when the tracker is
 *   following a hand or reports a wave/palm cue.
 *
 * It is a pure mapper with a little state (previous sample, edge debounce). A
 * face here is presence evidence only; nothing about identity leaves this class
 * because nothing about identity enters it.
 */
class FaceSense(
    /** Debounce so detector flicker does not fire arrive/lose pairs. */
    private val edgeDebounceMs: Long = 400L,
    /** Relative growth per second that counts as looming. */
    private val loomingRatePerSec: Float = 0.9f,
    private val loomingCooldownMs: Long = 1_500L,
    /** Head yaw within which the person is considered to be looking at the device. */
    private val gazeYawDeg: Float = 20f,
) {
    private var lastTrack: FaceTrack? = null
    private var lastAtMs: Long = Long.MIN_VALUE
    private var presentSinceMs: Long = Long.MIN_VALUE
    private var absentSinceMs: Long = Long.MIN_VALUE
    private var reportedPresent = false
    private var lastLoomingMs: Long = Long.MIN_VALUE / 2
    private var lastCue: SocialCue = SocialCue.NONE

    fun reset() {
        lastTrack = null
        lastAtMs = Long.MIN_VALUE
        presentSinceMs = Long.MIN_VALUE
        absentSinceMs = Long.MIN_VALUE
        reportedPresent = false
        lastLoomingMs = Long.MIN_VALUE / 2
        lastCue = SocialCue.NONE
    }

    /** [nowMs] is the controller clock at which the sample was observed. */
    fun map(track: FaceTrack, nowMs: Long): List<SensoryEvent> {
        val out = ArrayList<SensoryEvent>(4)
        val present = track.hasFace && track.confidence > 0.2f
        val conf = track.confidence.coerceIn(0f, 1f)

        // Distance proxy: scale 1 = baseline; map (0.5..2) into (0..1) with a soft curve.
        val proximity = if (present) (1f - exp(-(track.scale.coerceAtLeast(0f)) * 0.9f)).coerceIn(0f, 1f) else 0f
        val gazeOnMe = if (present && abs(track.yawDeg) <= gazeYawDeg) 1f - abs(track.yawDeg) / gazeYawDeg else 0f
        val count = (track.faceCount.coerceAtLeast(if (present) 1 else 0) / 3f).coerceIn(0f, 1f)

        out += SensoryEvent(
            kind = SensoryKind.FACE_PRESENCE,
            source = SensorySource.FACE,
            values = floatArrayOf(
                if (present) 1f else 0f,
                conf,
                proximity,
                if (present) track.offsetX.coerceIn(-1f, 1f) else 0f,
                if (present) track.offsetY.coerceIn(-1f, 1f) else 0f,
                gazeOnMe,
                count,
            ),
            confidence = if (present) conf else 1f,
            timestampMs = nowMs,
        )

        // Debounced presence edges.
        if (present) {
            absentSinceMs = Long.MIN_VALUE
            if (presentSinceMs == Long.MIN_VALUE) presentSinceMs = nowMs
            if (!reportedPresent && nowMs - presentSinceMs >= edgeDebounceMs) {
                reportedPresent = true
                out += SensoryEvent.impulse(SensoryKind.FACE_ARRIVED, 1f, SensorySource.FACE, conf, nowMs)
            }
        } else {
            presentSinceMs = Long.MIN_VALUE
            if (absentSinceMs == Long.MIN_VALUE) absentSinceMs = nowMs
            if (reportedPresent && nowMs - absentSinceMs >= edgeDebounceMs) {
                reportedPresent = false
                out += SensoryEvent.impulse(SensoryKind.FACE_LOST, 1f, SensorySource.FACE, 1f, nowMs)
            }
        }

        // Looming: relative growth rate of the size proxy.
        val prev = lastTrack
        if (present && prev != null && prev.hasFace && lastAtMs != Long.MIN_VALUE) {
            val dtSec = (nowMs - lastAtMs) / 1000f
            if (dtSec > 0.01f && prev.scale > 0.05f) {
                val rate = (track.scale - prev.scale) / prev.scale / dtSec
                if (rate >= loomingRatePerSec && nowMs - lastLoomingMs >= loomingCooldownMs) {
                    lastLoomingMs = nowMs
                    val magnitude = (rate / (loomingRatePerSec * 3f)).coerceIn(0.4f, 1f)
                    out += SensoryEvent.impulse(SensoryKind.LOOMING, magnitude, SensorySource.FACE, conf, nowMs)
                }
            }
        }

        // Hand following and gesture cues are motion evidence.
        val handFollow = if (track.followSource == FollowSource.HAND) 1f else 0f
        val motionEnergy = prev?.let {
            val dx = track.offsetX - it.offsetX
            val dy = track.offsetY - it.offsetY
            val dtSec = ((nowMs - lastAtMs).coerceAtLeast(1L)) / 1000f
            ((abs(dx) + abs(dy)) / dtSec / 4f).coerceIn(0f, 1f)
        } ?: 0f
        if (present || handFollow > 0f) {
            out += SensoryEvent(
                kind = SensoryKind.VISUAL_MOTION,
                source = SensorySource.VISUAL_MOTION,
                values = floatArrayOf(motionEnergy, handFollow),
                confidence = conf.coerceAtLeast(if (handFollow > 0f) 0.6f else 0f),
                timestampMs = nowMs,
            )
        }
        if (track.cue != lastCue && (track.cue == SocialCue.WAVE || track.cue == SocialCue.PALM || track.cue == SocialCue.HAND_UP)) {
            out += SensoryEvent.impulse(SensoryKind.WAVE, 1f, SensorySource.VISUAL_MOTION, conf.coerceAtLeast(0.5f), nowMs)
        }
        lastCue = track.cue

        lastTrack = track
        lastAtMs = nowMs
        return out
    }
}
