package com.example.meowmba.ui.companion

import com.example.meowmba.core.personality.MoodEngine
import com.example.meowmba.core.personality.MoodEvent
import java.util.Calendar

/**
 * Injects time-of-day mood biases into the existing MoodEngine.
 * Only applies if no user interaction in the last 5 minutes.
 * This is an ADDITIVE time source — it feeds events into MoodEngine,
 * never replaces it.
 */
class CircadianMoodEngine(private val moodEngine: MoodEngine) {

    @Volatile
    private var lastUserEventMs: Long = System.currentTimeMillis()

    /** Call whenever the user actively interacts (speech, touch, etc). */
    fun noteUserEvent() {
        lastUserEventMs = System.currentTimeMillis()
    }

    /**
     * Evaluate time of day and inject appropriate mood if idle long enough.
     * Call from the 10-minute tick loop after moodEngine.tick().
     * Returns true if a circadian mood was injected.
     */
    fun evaluateAndApply(now: Long): Boolean {
        val idleMs = now - lastUserEventMs
        if (idleMs < 5 * 60 * 1000L) return false // User active recently, skip

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val event: MoodEvent = when (hour) {
            in 6..11 -> MoodEvent.WakeGreeting   // EXCITED in morning
            in 12..17 -> MoodEvent.UserSpoke     // CURIOUS in afternoon
            in 18..22 -> MoodEvent.Settled       // CONTENT in evening
            else -> return false // let natural SLEEPY drift handle night
        }
        moodEngine.onEvent(event, now)
        return true
    }
}
