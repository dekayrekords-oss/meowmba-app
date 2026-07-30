package com.example.meowmba.core.agent.tools

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Calendar permission check.
 *
 * The manifest declaring READ_CALENDAR is not enough — it is a runtime permission, and
 * querying CalendarProvider2 without it throws SecurityException from a ContentResolver
 * deep inside a coroutine, which CRASHED THE APP when the user asked for their calendar.
 * A tool must never throw across the boundary; it returns a Failure instead.
 */
private fun hasCalendarAccess(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

private const val NO_CALENDAR_PERMISSION =
    "I don't have calendar permission yet. Allow calendar access in Settings and ask me again."

/**
 * `calendar.list_events` — reads upcoming events from the user's default
 * calendar for the next N days. Requires READ_CALENDAR permission.
 *
 * SAFE, read-only, no confirmation needed.
 */
class CalendarListTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "calendar.list_events",
        displayName = "List calendar events",
        description = "List upcoming events from the default calendar for the next N days.",
        inputSchema = mapOf("days" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 10_000
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        if (!hasCalendarAccess(context)) {
            return@withContext ToolResult.Failure(NO_CALENDAR_PERMISSION)
        }
        val days = args["days"]?.toIntOrNull()?.coerceIn(1, 90) ?: 7
        val resolver = context.contentResolver

        val now = System.currentTimeMillis()
        val end = now + days * 24L * 60 * 60 * 1000

        val primaryId = getPrimaryCalendarId(resolver)
        if (primaryId == null) {
            return@withContext ToolResult.Failure("No writable calendar found on this device.")
        }

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
            "${CalendarContract.Events.DTSTART} >= ? AND " +
            "${CalendarContract.Events.DTSTART} <= ? AND " +
            "${CalendarContract.Events.DELETED} = 0"
        val selArgs = arrayOf(primaryId.toString(), now.toString(), end.toString())

        val cursor = resolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, selArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )
        if (cursor == null) {
            return@withContext ToolResult.Failure("Could not query calendar.")
        }

        val fmt = SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault())
        fmt.timeZone = TimeZone.getDefault()

        val events = cursor.use { c ->
            val list = mutableListOf<String>()
            while (c.moveToNext()) {
                val title = c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: "(no title)"
                val start = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                val loc = c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)) ?: ""
                val locPart = if (loc.isNotBlank()) " at $loc" else ""
                list.add("${fmt.format(Date(start))} — $title$locPart")
            }
            list
        }

        if (events.isEmpty()) {
            ToolResult.Success("No events in the next $days days.")
        } else {
            ToolResult.Success("Upcoming events (next $days days):\n" + events.joinToString("\n"))
        }
    }

    /**
     * Returns null instead of throwing when the provider refuses us. Permission is checked
     * before we get here, but a revoked-mid-session grant would otherwise crash the app.
     */
    private fun getPrimaryCalendarId(resolver: ContentResolver): Long? = try {
        primaryCalendarIdOrThrow(resolver)
    } catch (e: SecurityException) {
        null
    }

    private fun primaryCalendarIdOrThrow(resolver: ContentResolver): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1"
        val cursor = resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null)
        cursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                val ownerSel = "${CalendarContract.Calendars._ID} = ?"
                val ownerCur = resolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(CalendarContract.Calendars.OWNER_ACCOUNT),
                    ownerSel, arrayOf(id.toString()), null
                )
                ownerCur?.use { oc ->
                    if (oc.moveToFirst()) {
                        val owner = oc.getString(0)
                        if (!owner.isNullOrBlank() && !owner.contains("gmail-readonly", ignoreCase = true)) {
                            return id
                        }
                    }
                }
            }
            // Fallback: first visible calendar
            c.moveToFirst()
            return c.getLong(c.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
        }
        return null
    }
}

/**
 * `calendar.create_event` — creates a new event on the user's default
 * calendar. Requires WRITE_CALENDAR permission.
 *
 * MODERATE risk, user confirms before creation.
 */
class CalendarCreateTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "calendar.create_event",
        displayName = "Create calendar event",
        description = "Creates a new calendar event with title, date/time, and optional description.",
        inputSchema = mapOf(
            "title" to "string",
            "start_time" to "string",
            "end_time" to "string",
            "description" to "string"
        ),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.ALWAYS,
        timeoutMs = 10_000
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        // Writing needs the same runtime grant; see hasCalendarAccess.
        if (!hasCalendarAccess(context)) {
            return@withContext ToolResult.Failure(NO_CALENDAR_PERMISSION)
        }
        val title = args["title"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'title'")
        val startStr = args["start_time"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'start_time'")
        val endStr = args["end_time"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'end_time'")
        val description = args["description"] ?: ""

        val resolver = context.contentResolver
        val primaryId = getPrimaryCalendarId(resolver)
        if (primaryId == null) {
            return@withContext ToolResult.Failure("No writable calendar found on this device.")
        }

        val startMs = parseTime(startStr)
        val endMs = parseTime(endStr)
        if (startMs == null) {
            return@withContext ToolResult.Failure(
                "Could not parse start_time \"$startStr\". Use ISO format like 2025-04-15T14:00."
            )
        }
        if (endMs == null) {
            return@withContext ToolResult.Failure(
                "Could not parse end_time \"$endStr\". Use ISO format like 2025-04-15T15:00."
            )
        }
        if (endMs <= startMs) {
            return@withContext ToolResult.Failure("End time must be after start time.")
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, primaryId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.ALL_DAY, 0)
        }

        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
        if (uri != null) {
            ToolResult.Success("Event \"$title\" created in your calendar.")
        } else {
            ToolResult.Failure("Failed to create event.")
        }
    }

    private fun getPrimaryCalendarId(resolver: ContentResolver): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1"
        val cursor = resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null)
        cursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                val ownerSel = "${CalendarContract.Calendars._ID} = ?"
                val ownerCur = resolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(CalendarContract.Calendars.OWNER_ACCOUNT),
                    ownerSel, arrayOf(id.toString()), null
                )
                ownerCur?.use { oc ->
                    if (oc.moveToFirst()) {
                        val owner = oc.getString(0)
                        if (!owner.isNullOrBlank() && !owner.contains("gmail-readonly", ignoreCase = true)) {
                            return id
                        }
                    }
                }
            }
            c.moveToFirst()
            return c.getLong(c.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
        }
        return null
    }

    private fun parseTime(timeStr: String): Long? {
        // Try ISO format: yyyy-MM-ddTHH:mm
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        isoFmt.timeZone = TimeZone.getDefault()
        try {
            return isoFmt.parse(timeStr)?.time
        } catch (_: Exception) { }

        // Try ISO date only: yyyy-MM-dd
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateFmt.timeZone = TimeZone.getDefault()
        try {
            return dateFmt.parse(timeStr)?.time
        } catch (_: Exception) { }

        // Try epoch millis
        return timeStr.toLongOrNull()
    }
}
