package com.example.meowmba.core.context

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.example.meowmba.core.notification.NotificationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Proactive context engine: combines calendar + notifications + time signals
 * to generate nudges the companion can speak without being asked.
 *
 * Called every ~60s from the routine heartbeat. Rate-limited to max 1 nudge
 * per 10 minutes. Respects [FocusLevel] to suppress during deep work.
 */
data class ProactiveNudge(
    val text: String,
    val priority: NudgePriority,
)

enum class NudgePriority { HIGH, MEDIUM, LOW }

class ProactiveEngine(
    private val context: Context?,
    private val focusDetector: FocusDetector,
) {
    companion object {
        private const val TAG = "ProactiveEngine"
        /** Minimum interval between nudges (ms). */
        private const val NUDGE_COOLDOWN_MS = 10 * 60_000L
        /** How far ahead to check for calendar events (ms). */
        private const val CALENDAR_LOOKAHEAD_MS = 5 * 60_000L
        /** Notification count threshold to trigger a nudge. */
        private const val NOTIF_THRESHOLD = 3
    }

    private var lastNudgeAt: Long = 0L
    private var lastCalendarEventNotified: String? = null
    private var morningBriefDelivered = false
    private var lastMorningBriefDate: Int = -1

    var enabled: Boolean = true

    /**
     * Called every ~60s. Returns a nudge if conditions are met, null otherwise.
     * Never interrupts when [canSpeak] returns false (user is talking/listening).
     */
    fun tick(now: Long = System.currentTimeMillis(), canSpeak: Boolean = true): ProactiveNudge? {
        if (!enabled || !canSpeak) return null

        // Rate limiting
        if (now - lastNudgeAt < NUDGE_COOLDOWN_MS) return null

        val focus = focusDetector.focusLevel.value

        // Check priority order: calendar > notifications > morning brief
        val nudge = checkCalendar(now, focus)
            ?: checkNotifications(now, focus)
            ?: checkMorningBrief(now, focus)

        if (nudge != null) {
            lastNudgeAt = now
            Log.d(TAG, "Nudge: [${nudge.priority}] ${nudge.text}")
        }
        return nudge
    }

    private fun checkCalendar(now: Long, focus: FocusLevel): ProactiveNudge? {
        // Calendar nudges are HIGH priority — always allowed, even in DEEP focus
        val ctx = context ?: return null
        try {
            val resolver: ContentResolver = ctx.contentResolver
            val start = now
            val end = now + CALENDAR_LOOKAHEAD_MS

            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Instances.BEGIN,
            )
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(start.toString())
                .appendPath(end.toString())
                .build()

            val cursor: Cursor? = resolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
            cursor?.use {
                if (it.moveToFirst()) {
                    val title = it.getString(0) ?: "Untitled event"
                    val eventKey = "$title-${it.getLong(1)}"
                    if (eventKey == lastCalendarEventNotified) return null
                    lastCalendarEventNotified = eventKey
                    val minutesAway = ((it.getLong(1) - now) / 60_000L).coerceAtLeast(1)
                    return ProactiveNudge(
                        text = "You have \"$title\" in $minutesAway minute${if (minutesAway > 1) "s" else ""}.",
                        priority = NudgePriority.HIGH,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Calendar check failed: ${e.message}")
        }
        return null
    }

    private fun checkNotifications(now: Long, focus: FocusLevel): ProactiveNudge? {
        // Skip during DEEP focus
        if (focus == FocusLevel.DEEP) return null

        val summary = NotificationStore.unreadSummary()
        if (summary.isEmpty()) return null

        // Find apps with NOTIF_THRESHOLD+ unread
        val notable = summary.filter { it.value >= NOTIF_THRESHOLD }
        if (notable.isEmpty()) return null

        val text = notable.entries.joinToString(", ") { "${it.value} from ${it.key}" }
        return ProactiveNudge(
            text = "You have new messages: $text.",
            priority = NudgePriority.MEDIUM,
        )
    }

    private fun checkMorningBrief(now: Long, focus: FocusLevel): ProactiveNudge? {
        if (focus == FocusLevel.DEEP) return null

        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val dayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)

        // Reset flag at midnight
        if (dayOfYear != lastMorningBriefDate) {
            morningBriefDelivered = false
            lastMorningBriefDate = dayOfYear
        }

        // Morning brief: 7am-10am, not yet delivered today
        if (hour in 7..10 && !morningBriefDelivered) {
            morningBriefDelivered = true
            val unreadCount = NotificationStore.unreadCount
            val notifPart = if (unreadCount > 0) " You have $unreadCount unread notification${if (unreadCount > 1) "s" else ""}." else ""
            return ProactiveNudge(
                text = "Good morning!$notifPart",
                priority = NudgePriority.LOW,
            )
        }
        return null
    }

    /** Reset for testing. */
    fun reset() {
        lastNudgeAt = 0L
        lastCalendarEventNotified = null
        morningBriefDelivered = false
    }
}
