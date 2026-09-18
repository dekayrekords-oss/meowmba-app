package com.example.meowmba.ui.companion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Idle morph showcase: rotates Lumen through every authored [AvatarAnimal]
 * (except [AvatarAnimal.NONE]) plus optional saved generated forms.
 *
 * Previously ambient performances only temporarily mapped a few clips to
 * cat / dog / duck via [AvatarAnimal.performanceFor]. This director is the
 * dedicated idle-form rotator and does not permanently overwrite the user's
 * preferred form — callers apply the [IdleFormTarget] as a non-persisted overlay.
 *
 * Cadence is configurable; defaults keep morphs thermal-sane (~45–75 s).
 */
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
    private val intervalMs: () -> Long = { DEFAULT_INTERVAL_MS },
    private val onTarget: (IdleFormTarget) -> Unit,
) {
    private var job: Job? = null
    private var index = 0

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(intervalMs().coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS))
                if (!canMorph()) continue
                val target = nextTarget() ?: continue
                onTarget(target)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Build the rotating pool: all authored animals, then saved generated ids. */
    fun pool(): List<IdleFormTarget> {
        val authored = authoredForms().map { IdleFormTarget(animal = it) }
        val generated = if (includeGenerated()) {
            generatedFormIds().filter { it.isNotBlank() }.map { IdleFormTarget(generatedId = it) }
        } else emptyList()
        return authored + generated
    }

    /** Advance one step; exposed for unit tests. */
    fun nextTarget(): IdleFormTarget? {
        val items = pool()
        if (items.isEmpty()) return null
        val i = ((index % items.size) + items.size) % items.size
        index = i + 1
        return items[i]
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 55_000L
        const val MIN_INTERVAL_MS = 20_000L
        const val MAX_INTERVAL_MS = 180_000L
    }
}
