package com.example.meowmba.core.context

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Heuristic focus detection: determines whether the user is in deep work
 * and should not be interrupted with proactive nudges.
 *
 * Signals:
 * - Same app in foreground for >10 min (deep focus on one task)
 * - Screen context classified as "document" or "code"
 * - No interaction with Meow for >15 min
 * - Time of day: work hours (9am-5pm) bias toward focus
 */
enum class FocusLevel {
    /** User is idle / browsing / between tasks. All nudges OK. */
    IDLE,
    /** User appears lightly engaged. Normal nudges allowed. */
    LIGHT,
    /** User is in deep work. Only HIGH-priority nudges (imminent meetings). */
    DEEP,
}

class FocusDetector {

    companion object {
        private const val TAG = "FocusDetector"
        /** Same app for this long = DEEP focus (ms). */
        private const val DEEP_FOCUS_APP_MS = 10 * 60_000L
        /** No Meow interaction for this long = at least LIGHT focus (ms). */
        private const val LIGHT_FOCUS_IDLE_MS = 15 * 60_000L
        /** Work hours range. */
        private const val WORK_HOUR_START = 9
        private const val WORK_HOUR_END = 17
    }

    private val _focusLevel = MutableStateFlow(FocusLevel.IDLE)
    val focusLevel: StateFlow<FocusLevel> = _focusLevel.asStateFlow()

    private var currentForegroundApp: String? = null
    private var appStartedAt: Long = 0L
    private var lastMeowInteractionAt: Long = System.currentTimeMillis()
    private var currentScreenContext: String = "general"

    /** Called when the foreground app changes (from accessibility service). */
    fun onForegroundAppChanged(packageName: String, now: Long = System.currentTimeMillis()) {
        if (packageName != currentForegroundApp) {
            currentForegroundApp = packageName
            appStartedAt = now
        }
        recalculate(now)
    }

    /** Called when ContextDirector classifies the screen. */
    fun onScreenContextChanged(kind: String) {
        currentScreenContext = kind
        recalculate(System.currentTimeMillis())
    }

    /** Called whenever the user interacts with Meow (speech, tap, etc.). */
    fun onUserInteraction() {
        lastMeowInteractionAt = System.currentTimeMillis()
        // Immediate downgrade: user is engaging with us, not deep in work
        _focusLevel.value = FocusLevel.IDLE
    }

    /** Periodic recalculation (called from ProactiveEngine heartbeat too). */
    fun recalculate(now: Long = System.currentTimeMillis()) {
        val appDuration = if (currentForegroundApp != null) now - appStartedAt else 0L
        val idleDuration = now - lastMeowInteractionAt
        val isWorkHours = isWorkHours(now)
        val isDeepContext = currentScreenContext in setOf("document", "code")

        val level = when {
            // Deep: same app >10min AND (work hours OR deep-work context)
            appDuration >= DEEP_FOCUS_APP_MS && (isWorkHours || isDeepContext) -> FocusLevel.DEEP
            // Light: no interaction for >15min OR (same app >5min in work hours)
            idleDuration >= LIGHT_FOCUS_IDLE_MS -> FocusLevel.LIGHT
            appDuration >= 5 * 60_000L && isWorkHours -> FocusLevel.LIGHT
            else -> FocusLevel.IDLE
        }

        if (level != _focusLevel.value) {
            Log.d(TAG, "Focus level: ${_focusLevel.value} -> $level (app=${currentForegroundApp}, ${appDuration/1000}s)")
            _focusLevel.value = level
        }
    }

    private fun isWorkHours(now: Long): Boolean {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        // Weekdays only
        if (dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY) return false
        return hour in WORK_HOUR_START until WORK_HOUR_END
    }
}
