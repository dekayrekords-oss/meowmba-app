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
 *  2. **One motion verb in flight** — [BodyVerb.Turn] carries its own duration;
 *     [BodyVerb.Drive] holds a level until braked. Overlapping them stacks
 *     motion unpredictably. [BodyVerb.Neck] and [BodyVerb.Light]
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
    private var activePlayback: Playback? = null

    private inner class Playback(val session: BodySession?) {
        var elapsed = 0L
        var windowOpen = false
        var finished = false
        var needsBrake = false
        val valid: Boolean get() = !finished && (session?.isValid != false)

        fun submit(verb: BodyVerb): Boolean {
            val accepted = session?.submit(verb) ?: run { output.perform(verb); true }
            if (accepted) needsBrake = verb !is BodyVerb.Brake
            return accepted
        }

        fun finish() {
            if (finished) return
            finished = true
            if (session != null) session.finish() else if (needsBrake) output.perform(BodyVerb.Brake)
            if (windowOpen) {
                windowOpen = false
                onMotionWindowEnd(elapsed)
            }
        }
    }

    /** Start a clip. Any clip already playing is superseded and braked. */
    fun play(cues: List<BodyCue>) {
        job?.cancel()
        activePlayback?.finish()
        activePlayback = null
        if (cues.isEmpty()) return

        // Capture the connection NOW. Resolving only after launch/delay could
        // play a pre-connect animation on an entirely different physical base.
        val source = output as? BodyPlaybackSource
        val session = source?.beginPlayback()
        if (source != null && session == null) return
        val playback = Playback(session)
        activePlayback = playback
        // Only a live, fenced body lease may play interpolated neck samples.
        // Reuse this clip's one timeline; never start an output-side motion job.
        val timeline = if (session != null) BodyNeckProfile.expand(cues) else cues

        job = scope.launch {
            var lastWriteAt = Long.MIN_VALUE
            var motionBusyUntil = Long.MIN_VALUE
            try {
                timeline.sortedBy { it.atMs }.forEach { cue ->
                    if (!isActive || !playback.valid) return@launch
                    val wait = cue.atMs - playback.elapsed
                    if (wait > 0) {
                        delay(wait)
                        playback.elapsed = cue.atMs.toLong()
                    }
                    if (!playback.valid) return@launch
                    val elapsed = playback.elapsed

                    val isMotion = cue.verb is BodyVerb.Turn || cue.verb is BodyVerb.Drive

                    // A motion verb still executing on the robot wins; drop the new one.
                    if (isMotion && elapsed < motionBusyUntil) return@forEach

                    // Brake is never budgeted or dropped. BodyMotionMap emits
                    // Light(false) and Brake at the same instant, so a budget check
                    // here would swallow the stop and leave the base moving.
                    val isBrake = cue.verb is BodyVerb.Brake

                    // Other state writes are cheap but not free — respect the budget.
                    // Same-instant neighbours and the first write always survive.
                    if (!isMotion && !isBrake) {
                        val since = elapsed - lastWriteAt
                        if (lastWriteAt != Long.MIN_VALUE && since > 0 && since < minWriteIntervalMs) {
                            return@forEach
                        }
                    }

                    if (!playback.submit(cue.verb)) return@forEach
                    if (isMotion && !playback.windowOpen) {
                        playback.windowOpen = true
                        onMotionWindowStart(elapsed)
                    }
                    if (isBrake && playback.windowOpen) {
                        playback.windowOpen = false
                        onMotionWindowEnd(elapsed)
                    }

                    lastWriteAt = elapsed
                    if (isMotion) {
                        motionBusyUntil = if (cue.verb is BodyVerb.Drive) Long.MAX_VALUE
                        else elapsed + motionDurationMs(cue.verb)
                    }
                    if (isBrake) motionBusyUntil = Long.MIN_VALUE
                }
            } finally {
                playback.finish()
                if (activePlayback === playback) activePlayback = null
            }
        }.also { launched ->
            // A scope may cancel a queued coroutine before its try/finally runs.
            launched.invokeOnCompletion { playback.finish() }
        }
    }

    /** Stop now: brake first, then drop everything still scheduled. */
    fun stop() {
        job?.cancel()
        job = null
        activePlayback?.finish()
        activePlayback = null
        output.perform(BodyVerb.Brake)
    }

    /** How long the robot will be busy executing this verb. Ticks are ~20ms. */
    private fun motionDurationMs(verb: BodyVerb): Long = when (verb) {
        is BodyVerb.Turn -> verb.ticks * TICK_MS
        else -> 0L
    }

    private companion object {
        const val TICK_MS = 20L
    }
}
