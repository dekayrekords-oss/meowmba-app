package com.example.meowmba.core.routines

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.meowmba.core.database.RoutineDao
import com.example.meowmba.core.database.RoutineEntity
import java.util.Calendar

/**
 * Phase E routines engine: schedules exact alarms for time-of-day or
 * interval routines. All time math is pure ([nextTrigger]) and unit-tested.
 */
object RoutineScheduler {

    const val EXTRA_ROUTINE_ID = "meowmba.routine_id"

    /** Default templates, seeded once on first run. */
    val DEFAULTS = listOf(
        RoutineEntity(name = "Morning brief", timeOfDay = "08:00", action = "morning_brief", enabled = true),
        RoutineEntity(name = "Memory follow-ups", timeOfDay = "20:00", action = "memory_followup", enabled = true),
    )

    /**
     * Next fire time for [routine] strictly after [now] (epoch millis).
     * Time-of-day routines fire daily; interval routines fire relative to
     * their last run (or now when never run).
     */
    fun nextTrigger(routine: RoutineEntity, now: Long): Long {
        routine.timeOfDay?.let { tod ->
            val (h, m) = tod.split(":").map { it.toInt() }
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
        val interval = routine.intervalMinutes ?: return 0L
        if (interval <= 0) return 0L
        val base = if (routine.lastRunAt > 0) routine.lastRunAt else now
        val step = interval * 60_000L
        var next = base + step
        while (next <= now) next += step
        return next
    }

    /** Seed the default templates when the table is empty. */
    suspend fun seedDefaults(dao: RoutineDao) {
        if (dao.count() == 0) DEFAULTS.forEach { dao.insert(it) }
    }

    fun scheduleAll(context: Context, routines: List<RoutineEntity>) {
        routines.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, routine: RoutineEntity) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = nextTrigger(routine, System.currentTimeMillis())
        if (at <= 0L) return
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context, routine.id))
    }

    fun cancel(context: Context, routineId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(context, routineId))
    }

    private fun pendingIntent(context: Context, routineId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            routineId.toInt(),
            Intent(context, RoutineAlarmReceiver::class.java)
                .putExtra(EXTRA_ROUTINE_ID, routineId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
