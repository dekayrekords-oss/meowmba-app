package com.example.meowmba.core.robot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays a cue list on the clip's own timeline.
 *
 * The face renders the same [com.example.meowmba.ui.companion.CustomAnimationSpec]
 * from the same instant, so as long as both start together they cannot drift.
 *
 * Owns three concerns the mapper deliberately does not:
 *
 *  1. **Write budget** — BLE is not a frame buffer. Writes closer together
 *     than [minWriteIntervalMs] are coalesced, keeping the wire quiet.
 *  2. **One motion verb in flight** — [BodyVerb.Turn] and [BodyVerb.Drive]
 *     carry their own duration in ticks, so overlapping them stacks motion the
 *     robot will execute unpredictably. [BodyVerb.Neck] and [BodyVerb.Light]
 *     are state writes and interleave freely.
 *  3. **Cancellation** — a superseded or cancelled clip brakes at once.
 */
class BodyScheduler(
    private val scope: CoroutineScope,
    private val output: BodyOutput,
    private val minWriteIntervalMs: Long = 60L,
    private val onMotionWindowStart: (Long) -> Unit = {},
    private val onMotionWindowEnd: (Long) -> Unit = {},
) {

    private var job: Job? = null

    /** Start a clip. Any clip already playing is superseded and braked. */
    fun play(cues: List<BodyCue>) {
        job?.cancel()
        if (cues.isEmpty()) return

        job = scope.launch {
            var elapsed = 0L
            var lastWriteAt = Long.MIN_VALUE
            var motionBusyUntil = Long.MIN_VALUE
            var windowOpen = false

            cues.sortedBy { it.atMs }.forEach { cue ->
                if (!isActive) return@launch
                val wait = cue.atMs - elapsed
                if (wait > 0) {
                    delay(wait)
                    elapsed = cue.atMs.toLong()
                }

                val isMotion = cue.verb is BodyVerb.Turn || cue.verb is BodyVerb.Drive

                // A motion verb still executing on the robot wins; drop the new one.
                if (isMotion && elapsed < motionBusyUntil) return@forEach

                // Brake is never budgeted or dropped. BodyMotionMap emits
                // Light(false) and Brake at the same instant, so a budget check
                // here would swallow the stop and leave the base moving.
                val isBrake = cue.verb is BodyVerb.Brake

                // Other state writes are cheap but not free — respect the budget.
                // A write strictly earlier than minWriteIntervalMs coalesces into
                // it; the FIRST write of a clip (lastWriteAt starts at
                // Long.MIN_VALUE) and same-instant neighbours always go through —
                // the overflow guard keeps `elapsed - Long.MIN_VALUE` from
                // reading as "Recent" and swallowing the opening cue.
                if (!isMotion && !isBrake) {
                    val since = elapsed - lastWriteAt
                    if (lastWriteAt != Long.MIN_VALUE && since > 0 && since < minWriteIntervalMs) {
                        return@forEach
                    }
                }

                if (isMotion && !windowOpen) {
                    windowOpen = true
                    onMotionWindowStart(elapsed)
                }
                if (cue.verb is BodyVerb.Brake && windowOpen) {
                    windowOpen = false
                    onMotionWindowEnd(elapsed)
                }

                output.perform(cue.verb)
                lastWriteAt = elapsed
                if (isMotion) motionBusyUntil = elapsed + motionDurationMs(cue.verb)
            }

            if (windowOpen) onMotionWindowEnd(elapsed)
        }
    }

    /** Stop now: brake first, then drop everything still scheduled. */
    fun stop() {
        job?.cancel()
        job = null
        output.perform(BodyVerb.Brake)
    }

    /** How long the robot will be busy executing this verb. Ticks are ~20ms. */
    private fun motionDurationMs(verb: BodyVerb): Long = when (verb) {
        is BodyVerb.Turn -> verb.ticks * TICK_MS
        is BodyVerb.Drive -> DRIVE_BUSY_MS
        else -> 0L
    }

    private companion object {
        const val TICK_MS = 20L
        /** fed0 drive is a level, not a pulse; treat it as busy until braked. */
        const val DRIVE_BUSY_MS = 200L
    }
}