package com.example.meowmba.core.flow

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One observed work day: first and last work activity, minutes after midnight. */
data class WorkDay(val date: LocalDate, val startMinute: Int, val endMinute: Int)

/**
 * What the phone can tell about work right now. All optional: missing evidence
 * simply doesn't count. Built from usage access, place, desktop bridge and input.
 */
data class WorkContext(
    /** Minutes since a work app (IDE, mail, docs, …) was last in the foreground, or null if never today. */
    val minutesSinceWorkApp: Int? = null,
    /** User typed/interacted in a work app within the last few minutes. */
    val activelyWorking: Boolean = false,
    val atWorkPlace: Boolean? = null,
    /** Laptop/desktop lid closed or bridge reports idle/asleep. */
    val computerClosed: Boolean? = null,
    val onWorkNetwork: Boolean? = null,
)

/**
 * Owner decision D5: learn the schedule, but let context decide when work is over.
 *
 * - Schedule prior: per weekday median start/end from the last [HISTORY_DAYS] observed days
 *   (defaults to 9:00–17:00 on weekdays until at least [MIN_DAYS] are known).
 * - Active work signals override a schedule that says "done" (working late).
 * - Work is OFF when at least two independent "stopped" signals agree; one signal after the
 *   usual end is WRAPPING.
 * - With detection switched off, only the schedule is used.
 */
class WorkStateDetector(private val zone: ZoneId = ZoneId.systemDefault()) {

    private val days = ArrayList<WorkDay>()

    fun recordDay(day: WorkDay) {
        days.removeAll { it.date == day.date }
        days += day
        days.sortBy { it.date }
        while (days.size > HISTORY_DAYS) days.removeAt(0)
    }

    fun history(): List<WorkDay> = days.toList()

    /** Learned (start, end) minutes for [dow], or null when it's usually not a work day. */
    fun usualHours(dow: DayOfWeek): Pair<Int, Int>? {
        val same = days.filter { it.date.dayOfWeek == dow }
        if (days.size < MIN_DAYS) {
            return if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) null else DEFAULT_START to DEFAULT_END
        }
        if (same.isEmpty()) return null
        return median(same.map { it.startMinute }) to median(same.map { it.endMinute })
    }

    fun estimate(now: Long, context: WorkContext, detectWorkEnd: Boolean = true): WorkEstimate {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        val minute = t.hour * 60 + t.minute
        val hours = usualHours(t.dayOfWeek)
        val inHours = hours != null && minute in hours.first until hours.second
        val afterEnd = hours != null && minute >= hours.second
        // Morning before the usual start (or before noon on a day off) is not "evening".
        val before = if (hours != null) minute < hours.first else t.hour < 12

        val scheduleOnly = if (inHours) WorkEstimate(WorkState.WORKING, 0.8, "usual work hours")
        else WorkEstimate(WorkState.OFF, 0.8, if (hours == null) "not a usual work day" else "outside usual hours", beforeWork = before)
        if (!detectWorkEnd) return scheduleOnly

        val recentApp = context.minutesSinceWorkApp?.let { it <= ACTIVE_WINDOW_MIN } == true
        if (context.activelyWorking || recentApp) {
            val late = !inHours
            return WorkEstimate(WorkState.WORKING, if (late) 0.82 else 0.9,
                if (late) "still working past your usual finish" else "working now")
        }

        val stopped = listOf(
            context.computerClosed == true,
            context.atWorkPlace == false && (hours == null || minute >= hours.first),
            context.minutesSinceWorkApp == null || context.minutesSinceWorkApp >= IDLE_WINDOW_MIN,
            context.onWorkNetwork == false && context.atWorkPlace != true,
        ).count { it }

        return when {
            stopped >= 2 && (afterEnd || !inHours) -> WorkEstimate(WorkState.OFF, 0.93, "several signals say work has stopped")
            stopped >= 3 -> WorkEstimate(WorkState.OFF, 0.85, "several signals say work has stopped early")
            stopped >= 1 && afterEnd -> WorkEstimate(WorkState.WRAPPING, 0.6, "past your usual finish and winding down")
            inHours -> WorkEstimate(WorkState.WORKING, 0.7, "usual work hours")
            before -> WorkEstimate(WorkState.OFF, 0.7, "before your usual start", beforeWork = true)
            else -> WorkEstimate(WorkState.OFF, 0.6, "outside usual hours")
        }
    }

    private fun median(values: List<Int>): Int = values.sorted().let { it[it.size / 2] }

    companion object {
        const val HISTORY_DAYS = 42
        const val MIN_DAYS = 5
        const val DEFAULT_START = 9 * 60
        const val DEFAULT_END = 17 * 60
        const val ACTIVE_WINDOW_MIN = 5
        const val IDLE_WINDOW_MIN = 8
    }
}
