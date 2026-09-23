package com.example.meowmba.core.flow.sources

import com.example.meowmba.core.flow.CalendarItem
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowPolicy
import com.example.meowmba.core.flow.Intention
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns data the app already has into Flow intentions, so Flow and the Desk are useful
 * without extra permissions: your reminders, the project you're in, meetings coming up,
 * people who messaged you (with notification access), and plain "connect" items for any
 * missing access. Pure, so it is unit-tested; FlowRuntime.syncManaged applies the result
 * while preserving the user's Done/Later choices.
 */
object AppIntentions {
    data class Reminder(val id: Long, val name: String, val timeOfDay: String?, val enabled: Boolean, val userMade: Boolean, val lastRunAt: Long)
    data class Project(val id: Long, val name: String, val kindLabel: String, val lastOpenedAt: Long)
    data class Access(val notifications: Boolean, val usage: Boolean, val calendar: Boolean)

    /** Id prefixes this object owns; anything with them that disappears from the list is closed. */
    val MANAGED_PREFIXES = listOf(REMINDER, PROJECT, CALENDAR, SETUP)
    const val REMINDER = "reminder-"
    const val PROJECT = "project-"
    const val CALENDAR = "cal-"
    const val SETUP = "setup-"
    const val MESSAGE = "msg-"

    const val SETUP_NOTIFICATIONS = "setup-notifications"
    const val SETUP_USAGE = "setup-usage"
    const val SETUP_CALENDAR = "setup-calendar"

    private val HM = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun build(
        now: Long, zone: ZoneId,
        reminders: List<Reminder>, projects: List<Project>, calendar: List<CalendarItem>, access: Access,
    ): List<Intention> = buildList {
        val today = Instant.ofEpochMilli(now).atZone(zone)

        // Your own reminders due later today (seeded system routines stay in Routines).
        reminders.filter { it.enabled && it.userMade }.forEach { r ->
            val t = r.timeOfDay?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return@forEach
            val due = today.toLocalDate().atTime(t).atZone(zone).toInstant().toEpochMilli()
            val ranToday = Instant.ofEpochMilli(r.lastRunAt).atZone(zone).toLocalDate() == today.toLocalDate()
            if (due < now - 30 * 60_000L || due - now > 12 * 3_600_000L || ranToday) return@forEach
            add(Intention("$REMINDER${r.id}-${today.toLocalDate()}", r.name, FlowKind.OTHER,
                "your reminder for ${HM.format(t)}", now, dueAt = due,
                facts = mapOf(FlowPolicy.FACT_ACTION to "Done", FlowPolicy.FACT_SIGNALS to "cal")))
        }

        // The project you were last in, if it was this week.
        projects.maxByOrNull { it.lastOpenedAt }?.takeIf { now - it.lastOpenedAt < 7 * 86_400_000L }?.let { p ->
            add(Intention("$PROJECT${p.id}", "Continue ${p.name}", FlowKind.WORK,
                "were last in this ${p.kindLabel.lowercase(Locale.ROOT)} ${ago(now - p.lastOpenedAt)}", now,
                facts = mapOf(FlowPolicy.FACT_ACTION to "Open", "project_id" to p.id.toString())))
        }

        // Timed calendar events in the next 24 hours.
        calendar.filter { it.startAt in (now - 10 * 60_000L)..(now + 86_400_000L) }.take(4).forEach { c ->
            val at = Instant.ofEpochMilli(c.startAt).atZone(zone)
            add(Intention("$CALENDAR${c.startAt}-${c.title.hashCode().toUInt()}",
                "${c.title} at ${HM.format(at)}", if (c.withPeople) FlowKind.PEOPLE else FlowKind.WORK,
                if (c.withPeople) "on your calendar with other people" else "on your calendar", now, dueAt = c.startAt,
                facts = mapOf(FlowPolicy.FACT_ACTION to "Open", FlowPolicy.FACT_SIGNALS to "cal")))
        }

        // Plain, dismissible "connect" items. They disappear as soon as access is granted.
        if (!access.notifications) add(setup(SETUP_NOTIFICATIONS, "Let Meow see who's waiting on you",
            "turn on notification access. Meow only uses the app and sender name, never the message text", now))
        if (!access.calendar) add(setup(SETUP_CALENDAR, "Connect your calendar",
            "let Meow bring up meetings before they start", now))
        if (!access.usage) add(setup(SETUP_USAGE, "Let Meow know what you're working in",
            "turn on usage access so Meow can tell when work starts and ends. Only app names are used", now))
    }

    /** A message notification from a person becomes "Reply to <sender>" until the notification is cleared. */
    fun fromMessage(packageName: String, appLabel: String, sender: String, at: Long): Intention? {
        val who = sender.trim().take(40).ifEmpty { return null }
        return Intention("$MESSAGE${packageName}-${who.lowercase(Locale.ROOT).hashCode().toUInt()}", "Reply to $who",
            FlowKind.PEOPLE, "messaged you in $appLabel", at,
            facts = mapOf(FlowPolicy.FACT_ACTION to "Reply", FlowPolicy.FACT_SIGNALS to "msg", "package" to packageName,
                FlowPolicy.FACT_BECAUSE to "Messaged you in $appLabel."),
            waitingPeople = listOf(who))
    }

    private fun setup(id: String, text: String, why: String, now: Long) =
        Intention(id, text, FlowKind.OTHER, why, now, facts = mapOf(FlowPolicy.FACT_ACTION to "Allow",
            FlowPolicy.FACT_BECAUSE to why.replaceFirstChar { it.uppercase() } + "."))

    private fun ago(ms: Long): String = when {
        ms < 3_600_000L -> "a few minutes ago"
        ms < 86_400_000L -> "earlier today"
        ms < 2 * 86_400_000L -> "yesterday"
        else -> "${ms / 86_400_000L} days ago"
    }
}
