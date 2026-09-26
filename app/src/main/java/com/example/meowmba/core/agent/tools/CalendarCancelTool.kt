package com.example.meowmba.core.agent.tools

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.example.meowmba.core.calendar.CalendarOccurrence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Cancels one uniquely resolved occurrence, never a whole recurring series. */
class CalendarCancelTool(private val context: Context,
    private val onCanceled: suspend (String, Long) -> Unit = { _, _ -> },
) : Tool {
    override val definition = ToolDefinition(
        id = "calendar.cancel_event", displayName = "Cancel calendar occurrence",
        description = "Cancel one calendar occurrence from an explicit cancellation request. Requires an unambiguous title/date; never cancels a series. query is the user's original cancellation request.",
        inputSchema = mapOf("query" to "string"), riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE), confirmationPolicy = ConfirmationPolicy.ALWAYS, timeoutMs = 15_000)

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val request = CalendarCancellation.parse(args["query"].orEmpty())
            ?: return ToolResult.Failure("Tell me the event title and today, tomorrow, or a date like 2026-09-26. Nothing was changed.")
        return cancel(request, null)
    }

    /** Only the deliberate Flow comment submission calls this; IDs are not model tool arguments. */
    suspend fun cancelOccurrence(target: CalendarOccurrence): ToolResult = cancel(null, target)

    private suspend fun cancel(request: CalendarCancellation.Request?, expected: CalendarOccurrence?): ToolResult = withContext(Dispatchers.IO) {
        if (listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).any {
                context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED })
            return@withContext ToolResult.Failure("Allow calendar read/write access in Settings so I can cancel the actual event. Nothing was changed.")
        try {
            val zone = ZoneId.systemDefault()
            val date = expected?.let { Instant.ofEpochMilli(it.begin).atZone(zone).toLocalDate() }
                ?: request?.day ?: LocalDate.now()
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(if (expected == null && request?.day == null) 30 else 1).atStartOfDay(zone).toInstant().toEpochMilli()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, start - 86_400_000L); ContentUris.appendId(it, end + 86_400_000L)
            }.build()
            val projection = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.RRULE, CalendarContract.Instances.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME, CalendarContract.Instances.RDATE)
            data class Match(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean,
                val recurring: Boolean, val access: Int, val calendar: String)
            val matches = mutableListOf<Match>()
            context.contentResolver.query(uri, projection,
                com.example.meowmba.core.calendar.CalendarEventSelection.active,
                null, null)?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(1).orEmpty()
                    val allDay = c.getInt(4) == 1
                    val begin = c.getLong(2)
                    val localDay = Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()
                    val selected = if (expected != null) c.getLong(0) == expected.eventId && begin == expected.begin &&
                        c.getLong(3) == expected.end && title == expected.title
                    else request != null && (request.day?.let { localDay == it } ?: (begin in start until end)) &&
                        CalendarCancellation.matchesTitle(request, title)
                    if (selected) {
                        matches += Match(c.getLong(0), title, begin,
                            c.getLong(3), allDay, !c.getString(5).isNullOrBlank() || !c.getString(8).isNullOrBlank(), c.getInt(6), c.getString(7).orEmpty())
                    }
                }
            } ?: return@withContext ToolResult.Failure("Calendar could not be read; nothing was changed.")
            val unique = matches.distinctBy { it.id to it.begin }
            if (unique.isEmpty()) return@withContext ToolResult.Failure(if (expected != null)
                "This calendar event changed or is no longer available. Nothing was changed. Refresh Flow and comment on the current event."
                else "No matching calendar occurrence was found. Nothing was changed; give me its exact title and date.")
            if (unique.size != 1) return@withContext ToolResult.Failure("More than one event matches; nothing was changed. Which one? " +
                unique.take(6).joinToString("; ") { "${it.title} (${it.calendar}, ${Instant.ofEpochMilli(it.begin).atZone(zone)})" })
            val target = unique.single()
            if (target.access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)
                return@withContext ToolResult.Failure("${target.calendar} is read-only. I couldn't cancel ${target.title}.")
            val values = ContentValues().apply { put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED) }
            val changedUri = if (target.recurring) {
                values.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, target.begin)
                values.put(CalendarContract.Events.DTSTART, target.begin)
                values.put(CalendarContract.Events.DTEND, target.end)
                values.put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (target.allDay) 1 else 0)
                context.contentResolver.insert(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, target.id), values)
            } else {
                val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, target.id)
                if (context.contentResolver.update(eventUri, values, null, null) == 1) eventUri else null
            }
            val verified = changedUri?.let { eventUri ->
                context.contentResolver.query(eventUri, arrayOf(CalendarContract.Events.STATUS), null, null, null)?.use {
                    it.moveToFirst() && it.getInt(0) == CalendarContract.Events.STATUS_CANCELED
                }
            } == true
            if (!verified) return@withContext ToolResult.Failure("The calendar did not confirm cancellation. Check the event before relying on it being canceled.")
            onCanceled(target.title, target.begin)
            ToolResult.Success("Canceled ${target.title} on ${request?.day ?: Instant.ofEpochMilli(target.begin).atZone(zone).toLocalDate()} in ${target.calendar}. " +
                if (target.recurring) "Only that occurrence was canceled; the rest of the series is unchanged." else "Calendar cancellation verified.")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { ToolResult.Failure("Calendar cancellation could not be completed or verified. Check calendar access and the event; I won't claim it is canceled.") }
    }
}
