package com.example.meowmba.ui.companion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Plays an occasional random animation while the companion is idle, so the
 * face feels alive instead of frozen between interactions.
 *
 * The director never decides WHETHER an animation is appropriate on its own —
 * it asks [canPlay] (owned by the ViewModel, which knows about TTS, tasks,
 * active clips and interaction state) and only fires when the answer is yes.
 * The first clip arrives early (12–25 s) so the face performs within the first
 * moments of idle watching; afterwards a random 25–80 s wait keeps the
 * behaviour from looking metronomic.
 */
class IdleAnimationDirector(
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
    /** Interactions are quiet and nothing else is animating. */
    private val canPlay: () -> Boolean,
    /** Built-in clips plus any user-saved inventions. */
    private val pickSpec: () -> CustomAnimationSpec?,
    private val play: (CustomAnimationSpec) -> Unit,
    /** Micro-clips: tiny movements (ear twitch, gaze drift) every 15-30s. */
    private val pickMicroSpec: () -> CustomAnimationSpec? = { null },
) {
    private var job: Job? = null
    private var microJob: Job? = null
    private var hasPlayed = false

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(if (hasPlayed) nextWaitMs() else firstWaitMs())
                if (canPlay()) {
                    pickSpec()?.let { spec ->
                        hasPlayed = true
                        play(spec)
                    }
                }
            }
        }
        // Micro-motion layer: subtle movements every 15-30s
        if (microJob?.isActive != true) {
            microJob = scope.launch {
                while (isActive) {
                    delay(microWaitMs())
                    if (canPlay()) {
                        pickMicroSpec()?.let { play(it) }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        microJob?.cancel()
        microJob = null
    }

    /** First-clip cadence: 12–25 s so a performance happens early. Internal for unit tests. */
    internal fun firstWaitMs(): Long = (12_000L + random.nextLong(13_000L))

    /** Random cadence in 25–80 s; internal for unit tests. */
    internal fun nextWaitMs(): Long = (25_000L + random.nextLong(55_000L))

    /** Micro-motion cadence: 15–30 s; internal for unit tests. */
    internal fun microWaitMs(): Long = (15_000L + random.nextLong(15_000L))
}
