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
 * Cadence is a random 45–120 s wait so the behaviour never looks metronomic.
 */
class IdleAnimationDirector(
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
    /** Interactions are quiet and nothing else is animating. */
    private val canPlay: () -> Boolean,
    /** Built-in clips plus any user-saved inventions. */
    private val pickSpec: () -> CustomAnimationSpec?,
    private val play: (CustomAnimationSpec) -> Unit,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(nextWaitMs())
                if (canPlay()) {
                    pickSpec()?.let(play)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Random cadence in 45–120 s; internal for unit tests. */
    internal fun nextWaitMs(): Long = (45_000L + random.nextLong(75_000L))
}
