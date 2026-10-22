package com.example.meowmba.ui.companion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Brief idle visits always return to the saved baseline; timing is deliberately irregular. */
data class IdleFormTarget(
    val animal: AvatarAnimal? = null,
    val generatedId: String? = null,
) {
    val isGenerated: Boolean get() = !generatedId.isNullOrBlank()
}

class IdleFormCycler(
    private val scope: CoroutineScope,
    private val canMorph: () -> Boolean,
    private val authoredForms: () -> List<AvatarAnimal> = {
        AvatarAnimal.entries.filter { it != AvatarAnimal.NONE }
    },
    private val generatedFormIds: () -> List<String> = { emptyList() },
    private val includeGenerated: () -> Boolean = { true },
    private val randomness: () -> Float = { 1f },
    private val canShow: () -> Boolean = { true },
    private val baseline: () -> IdleFormTarget = { IdleFormTarget() },
    private val onBaseline: () -> Unit = {},
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
    private val onTarget: (IdleFormTarget) -> Unit,
) {
    private var job: Job? = null
    private var remaining = mutableListOf<IdleFormTarget>()
    private var lastPool = emptyList<IdleFormTarget>()
    private var previous: IdleFormTarget? = null
    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                while (isActive) {
                    // Require uninterrupted idle time. Activity restarts the quiet period.
                    var quietMs = 0L
                    var waitMs = nextWaitMs()
                    while (isActive && quietMs < waitMs) {
                        delay(POLL_MS)
                        if (canMorph()) quietMs += POLL_MS else {
                            quietMs = 0L
                            waitMs = nextWaitMs()
                        }
                    }
                    while (isActive && canMorph() && !canShow()) delay(POLL_MS)
                    val target = nextTarget() ?: continue
                    if (!canMorph() || !canShow()) continue
                    val savedBaseline = baseline()
                    onTarget(target)
                    var remainingMs = random.nextLong(3_000L, 6_001L)
                    while (isActive && remainingMs > 0 && canMorph() && canShow() && baseline() == savedBaseline) {
                        delay(POLL_MS)
                        remainingMs -= POLL_MS
                    }
                    onBaseline()
                }
            } finally {
                onBaseline()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        onBaseline()
    }

    /** Build the rotating pool: all authored animals, then saved generated ids. */
    fun pool(): List<IdleFormTarget> {
        val authored = authoredForms().map { IdleFormTarget(animal = it) }
        val generated = if (includeGenerated()) {
            generatedFormIds().filter { it.isNotBlank() }.map { IdleFormTarget(generatedId = it) }
        } else emptyList()
        return (authored + generated).distinct().filter { it != baseline() }
    }

    /** Advance one step; exposed for unit tests. */
    fun nextTarget(): IdleFormTarget? {
        val items = pool()
        if (items.isEmpty()) return null
        // Shuffle a whole bag instead of rolling independently: every available
        // character gets a turn before any repeats, including generated forms.
        if (remaining.isEmpty() || items != lastPool) {
            remaining = items.toMutableList()
            lastPool = items
            if (remaining.size > 1 && remaining.first() == previous) {
                val first = remaining.removeAt(0)
                remaining.add(first)
            }
        }
        val randomIndex = if (random.nextFloat() < randomness().coerceIn(0f, 1f))
            remaining.indices.random(random) else 0
        val index = if (remaining.size > 1 && remaining[randomIndex] == previous)
            (randomIndex + 1) % remaining.size else randomIndex
        return remaining.removeAt(index).also { previous = it }
    }

    internal fun nextWaitMs(): Long = random.nextLong(60_000L, 240_001L)

    companion object {
        private const val POLL_MS = 500L
        const val DEFAULT_INTERVAL_MS = 55_000L
        const val MIN_INTERVAL_MS = 20_000L
        const val MAX_INTERVAL_MS = 180_000L
    }
}
