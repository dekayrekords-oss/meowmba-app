package com.example.meowmba.core.neural

/** Explicit-feedback preference head, separate from transient mood and core synapses.
 * No text is interpreted as a training instruction. Persist encode() after accepted
 * feedback; reset forgets all learned values. Invalid snapshots fail closed.
 */
class AdaptivePreferenceModel {
    var enabled: Boolean = true
    private val values = linkedMapOf("feed" to 0f, "play" to 0f, "rest" to 0f, "explore" to 0f)
    private var lastFeedbackMs: Long = -1L
    private var updates: Int = 0

    @Synchronized fun preference(activity: String): Float = if (enabled) values[activity] ?: 0f else 0f

    @Synchronized fun reward(activity: String, positive: Boolean, nowMs: Long): Boolean {
        if (!enabled || activity !in values || nowMs < 0 || updates >= 1000) return false
        if (lastFeedbackMs >= 0 && (nowMs < lastFeedbackMs || nowMs - lastFeedbackMs < 60_000L)) return false
        values[activity] = (values.getValue(activity) + if (positive) .05f else -.05f).coerceIn(-1f, 1f)
        lastFeedbackMs = nowMs
        updates++
        return true
    }

    @Synchronized fun reset() {
        values.keys.toList().forEach { values[it] = 0f }
        updates = 0
        lastFeedbackMs = -1L
    }

    @Synchronized fun encode(): String = listOf("1", enabled.toString(), lastFeedbackMs.toString(), updates.toString(),
        values.values.joinToString(",")).joinToString("|")

    companion object {
        fun decode(encoded: String): AdaptivePreferenceModel {
            val clean = AdaptivePreferenceModel()
            try {
                val parts = encoded.split('|')
                require(parts.size == 5 && parts[0] == "1")
                val enabled = when(parts[1]) { "true" -> true; "false" -> false; else -> error("enabled") }
                val time = parts[2].toLong().also { require(it >= -1L) }
                val count = parts[3].toInt().also { require(it in 0..1000) }
                val scores = parts[4].split(',').map { it.toFloat().also { v -> require(v.isFinite() && v in -1f..1f) } }
                require(scores.size == clean.values.size)
                require((count == 0 && time == -1L) || (count > 0 && time >= 0L))
                clean.enabled = enabled
                clean.lastFeedbackMs = time
                clean.updates = count
                clean.values.keys.toList().forEachIndexed { i, key -> clean.values[key] = scores[i] }
            } catch (_: Exception) { return AdaptivePreferenceModel() }
            return clean
        }
    }
}
