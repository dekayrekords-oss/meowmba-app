package com.example.meowmba.core.context.live

import com.example.meowmba.core.flow.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * What Meow knows about the user's situation right now (docs/MEOW_CONTEXT_PLAN.md §1,
 * docs/MEOW_SCENARIOS.md §19). Fed by background activity transitions and location
 * (ContextSensing), the mic's existing sound level, charging and audio route. Read by
 * Flow, the journal stamp and chat. Process-wide so the background receiver can update it.
 */
data class ContextNow(
    val activity: Activity = Activity.UNKNOWN,
    val activitySince: Long = 0L,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val speedMps: Float? = null,
    val fixAt: Long = 0L,
    /** Learned place label when the fix is inside one ("Home", "Work"); set by PlaceLearner. */
    val place: String? = null,
    val charging: Boolean = false,
    val batteryPct: Int? = null,
    /** "car" / "headphones" when that audio route is connected. */
    val audio: String? = null,
) {
    val hasFix: Boolean get() = lat != null && lon != null
    val speedMph: Float? get() = speedMps?.takeIf { it >= 0f }?.times(2.23694f)
}

object LiveContext {
    private val _now = MutableStateFlow(ContextNow())
    val now: StateFlow<ContextNow> = _now

    fun update(f: (ContextNow) -> ContextNow) = _now.update(f)

    fun setActivity(a: Activity, at: Long) = _now.update { if (it.activity == a) it else it.copy(activity = a, activitySince = at) }

    fun setFix(lat: Double, lon: Double, accuracyM: Float, speedMps: Float?, at: Long) =
        _now.update { it.copy(lat = lat, lon = lon, accuracyM = accuracyM, speedMps = speedMps, fixAt = at) }

    // ---- surroundings: sound level from the mic only when it is already listening ----------

    private val loud = FloatArray(64)
    private val loudAt = LongArray(64)
    private var loudN = 0

    /** Called by InputSoundLevel on every published sample (≥100 ms apart). */
    @Synchronized fun noteLoudness(loudness: Float, nowMs: Long) {
        val i = loudN % loud.size
        loud[i] = loudness; loudAt[i] = nowMs; loudN++
    }

    /**
     * Background level over the last [windowMs]: the 20th percentile, so the user's own voice
     * (the loud part while they talk to Meow) does not count as "loud surroundings".
     * Null when the mic has not been listening recently.
     */
    @Synchronized fun noise(nowMs: Long, windowMs: Long = 15_000L): String? {
        val recent = (0 until minOf(loudN, loud.size)).filter { nowMs - loudAt[it] in 0..windowMs }.map { loud[it] }.sorted()
        if (recent.size < 5) return null
        val floor = recent[(recent.size * 0.2f).toInt()]
        return when { floor < 0.2f -> "quiet"; floor < 0.45f -> "talking"; else -> "loud" }
    }
}
