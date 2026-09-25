package com.example.meowmba.core.flow.sources

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import com.example.meowmba.core.flow.CalendarItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Upcoming calendar items for Flow (READ_CALENDAR is already declared for CalendarTools).
 * Reads titles, times and whether anyone else attends — never descriptions or attendee
 * details. Returns an empty list without permission instead of asking; the permission
 * prompt belongs to the integrator's first-use flow.
 */
class CalendarSource(private val context: Context, private val horizonMs: Long = 36 * 60 * 60 * 1000L) {

    suspend fun upcoming(now: Long): List<CalendarItem> = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 60 * 60 * 1000L)
            ContentUris.appendId(it, now + horizonMs)
        }.build()
        runCatching {
            context.contentResolver.query(uri, PROJECTION, SELECTION, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { mapRows(it, now) }
        }.getOrNull().orEmpty()
    }

    companion object {
        val PROJECTION = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.HAS_ATTENDEE_DATA,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )
        /** Skip events the user declined. */
        private val SELECTION = "(${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL OR ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}) AND " +
            com.example.meowmba.core.calendar.CalendarEventSelection.active

        /** Pure mapping from a cursor over [PROJECTION]; all-day and finished events are skipped. */
        fun mapRows(c: Cursor, now: Long, limit: Int = 12): List<CalendarItem> {
            val out = ArrayList<CalendarItem>()
            while (c.moveToNext() && out.size < limit) {
                val allDay = c.getInt(3) == 1
                val end = c.getLong(2)
                if (allDay || end < now) continue
                val title = c.getString(0)?.trim().orEmpty().ifEmpty { "Busy" }.take(60)
                out += CalendarItem(title, c.getLong(1), end, withPeople = c.getInt(4) == 1)
            }
            return out
        }
    }
}
