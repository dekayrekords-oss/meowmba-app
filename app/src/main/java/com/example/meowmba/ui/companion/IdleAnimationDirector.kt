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
    /**
     * Name of the next clip: a composed [CatExpressions] name, a keyframe
     * built-in, or a user invention. Names rather than specs, because the two
     * animation systems are resolved by name downstream and a director that
     * carried one system's spec type could only ever pick from that system.
     */
    private val pickName: () -> String?,
    private val play: (String) -> Unit,
) {
    private var job: Job? = null
    private var hasPlayed = false

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(if (hasPlayed) nextWaitMs() else firstWaitMs())
                if (canPlay()) {
                    pickName()?.let { name ->
                        hasPlayed = true
                        play(name)
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** First-clip cadence: 4–9 s so a performance happens early. Internal for unit tests. */
    internal fun firstWaitMs(): Long = (4_000L + random.nextLong(5_000L))

    /**
     * Random cadence in 9–26 s; internal for unit tests.
     *
     * Was 25–80 s. A clip runs 1.5–3.5 s, so at the old cadence the face spent
     * up to 95% of an idle minute with nothing but the ambient layer — long
     * enough that the ambient reads as the whole behaviour and the cat looks
     * switched off between performances. Deskmate-class companions land a
     * visible beat every few seconds; this plus the always-on [ambientLife]
     * layer is what closes that gap.
     */
    internal fun nextWaitMs(): Long = (9_000L + random.nextLong(17_000L))
}
