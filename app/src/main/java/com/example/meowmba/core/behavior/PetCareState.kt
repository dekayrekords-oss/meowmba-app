package com.example.meowmba.core.behavior

/**
 * Optional, gentle virtual-pet needs. These are game meters, never health diagnoses.
 * Off means self-sufficient, including across restarts; enabling starts fresh. Catch-up
 * is capped at one day and no meter falls below 10%, so absence never kills the pet.
 * The caller owns persistence and the clock; this value object schedules no work.
 */
data class PetCareState(
    val careEnabled: Boolean = false,
    val food: Float = 1f,
    val energy: Float = 1f,
    val happiness: Float = 1f,
    val updatedAtMs: Long = 0,
) {
    fun advance(nowMs: Long): PetCareState {
        val previous = updatedAtMs.coerceAtLeast(0)
        val now = nowMs.coerceAtLeast(0).coerceAtLeast(previous)
        if (!careEnabled) return copy(food = 1f, energy = 1f, happiness = 1f, updatedAtMs = now)
        val hours = if (previous == 0L) 0f else
            ((now.toDouble() - previous.toDouble()).coerceIn(0.0, DAY_MS) / HOUR_MS).toFloat()
        return copy(
            food = (meter(food) - hours * .08f).coerceAtLeast(.1f),
            energy = (meter(energy) - hours * .04f).coerceAtLeast(.1f),
            happiness = (meter(happiness) - hours * .05f).coerceAtLeast(.1f),
            updatedAtMs = now,
        )
    }

    fun feed(nowMs: Long): PetCareState = advance(nowMs).let {
        if (!it.careEnabled) it else it.copy(food = (it.food + .35f).coerceAtMost(1f), happiness = (it.happiness + .08f).coerceAtMost(1f))
    }

    fun play(nowMs: Long): PetCareState = advance(nowMs).let {
        if (!it.careEnabled) it else it.copy(happiness = (it.happiness + .30f).coerceAtMost(1f), energy = (it.energy - .08f).coerceAtLeast(.1f))
    }

    fun rest(nowMs: Long): PetCareState = advance(nowMs).let {
        if (!it.careEnabled) it else it.copy(energy = (it.energy + .40f).coerceAtMost(1f), happiness = (it.happiness + .05f).coerceAtMost(1f))
    }

    fun withCare(enabled: Boolean, nowMs: Long): PetCareState {
        if (enabled == careEnabled) return advance(nowMs)
        return PetCareState(careEnabled = enabled, updatedAtMs = maxOf(nowMs, updatedAtMs, 0L))
    }

    /** Compact versioned preference payload; invalid payloads return a self-sufficient pet. */
    fun encode(): String {
        val clean = advance(updatedAtMs)
        return "1|${clean.careEnabled}|${clean.food}|${clean.energy}|${clean.happiness}|${clean.updatedAtMs}"
    }

    companion object {
        private const val HOUR_MS = 3_600_000.0
        private const val DAY_MS = HOUR_MS * 24
        private fun meter(value: Float) = if (value.isFinite()) value.coerceIn(.1f, 1f) else 1f

        fun decode(value: String?): PetCareState {
            val parts = value?.split('|') ?: return PetCareState()
            if (parts.size != 6 || parts[0] != "1") return PetCareState()
            val enabled = when (parts[1]) { "true" -> true; "false" -> false; else -> return PetCareState() }
            val food = parts[2].toFloatOrNull()?.takeIf { it.isFinite() } ?: return PetCareState()
            val energy = parts[3].toFloatOrNull()?.takeIf { it.isFinite() } ?: return PetCareState()
            val happiness = parts[4].toFloatOrNull()?.takeIf { it.isFinite() } ?: return PetCareState()
            val updated = parts[5].toLongOrNull()?.takeIf { it >= 0 } ?: return PetCareState()
            return PetCareState(enabled, meter(food), meter(energy), meter(happiness), updated).advance(updated)
        }
    }
}
