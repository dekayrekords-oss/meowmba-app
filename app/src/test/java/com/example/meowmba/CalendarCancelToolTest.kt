package com.example.meowmba

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.agent.tools.CalendarCancelTool
import com.example.meowmba.core.agent.tools.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarCancelToolTest {
    private lateinit var ctx: Context
    private lateinit var provider: FakeCalendar
    private val begin get() = LocalDate.now().plusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    @Before fun setup() {
        ctx = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(ctx as Application).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        provider = FakeCalendar(begin)
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
    }
    @Test fun uniqueOccurrenceIsWrittenVerifiedAndRetired() = runTest {
        var retired: Pair<String, Long>? = null
        val result = CalendarCancelTool(ctx) { title, at -> retired = title to at }.execute(mapOf("query" to "the event tomorrow is canceled"))
        assertTrue(result.toString(), result is ToolResult.Success)
        assertEquals(1, provider.updates)
        assertEquals("Dentist" to begin, retired)
        assertEquals(CalendarContract.Events.STATUS_CANCELED, provider.status)
    }
    @Test fun ambiguousCandidatesDoNotWrite() = runTest {
        provider.duplicate = true
        assertTrue(CalendarCancelTool(ctx).execute(mapOf("query" to "cancel the event tomorrow")) is ToolResult.Failure)
        assertEquals(0, provider.updates + provider.inserts)
    }
    @Test fun recurrenceCreatesOnlyAnException() = runTest {
        provider.recurring = true
        assertTrue(CalendarCancelTool(ctx).execute(mapOf("query" to "cancel dentist tomorrow")) is ToolResult.Success)
        assertEquals(0, provider.updates)
        assertEquals(1, provider.inserts)
        assertTrue(provider.insertUri.toString().contains("exception"))
        assertEquals(begin, provider.insertValues?.getAsLong(CalendarContract.Events.ORIGINAL_INSTANCE_TIME))
    }
    @Test fun providerRefusalCannotBeReportedAsSuccess() = runTest {
        provider.refuseUpdate = true
        var retired = false
        assertTrue(CalendarCancelTool(ctx) { _, _ -> retired = true }.execute(mapOf("query" to "cancel dentist tomorrow")) is ToolResult.Failure)
        assertFalse(retired)
    }
    @Test fun readOnlyAndMissingPermissionCannotWrite() = runTest {
        provider.readOnly = true
        assertTrue(CalendarCancelTool(ctx).execute(mapOf("query" to "cancel dentist tomorrow")) is ToolResult.Failure)
        Shadows.shadowOf(ctx as Application).denyPermissions(Manifest.permission.WRITE_CALENDAR)
        assertTrue(CalendarCancelTool(ctx).execute(mapOf("query" to "cancel dentist tomorrow")) is ToolResult.Failure)
        assertEquals(0, provider.updates + provider.inserts)
    }
    @Test fun missingStatusRemainsVisibleWhileCanceledRowsAreExcluded() {
        android.database.sqlite.SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE events (${CalendarContract.Events.STATUS} INTEGER)")
            db.execSQL("INSERT INTO events VALUES (NULL), (0), (1), (2)")
            db.rawQuery("SELECT COUNT(*) FROM events WHERE ${com.example.meowmba.core.calendar.CalendarEventSelection.active}", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(3, it.getInt(0))
            }
        }
    }
    private class FakeCalendar(val begin: Long) : ContentProvider() {
        var duplicate = false; var recurring = false; var readOnly = false; var refuseUpdate = false
        var updates = 0; var inserts = 0; var status = CalendarContract.Events.STATUS_CONFIRMED
        var insertUri: Uri? = null; var insertValues: ContentValues? = null
        override fun onCreate() = true
        override fun getType(uri: Uri) = "vnd.android.cursor.item/event"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            val cols = projection!!.map { it }.toTypedArray()
            return MatrixCursor(cols).apply {
                if (uri.toString().contains("instances")) {
                    repeat(if (duplicate) 2 else 1) { index -> addRow(cols.map { col -> when (col) {
                        CalendarContract.Instances.EVENT_ID -> index + 1L
                        CalendarContract.Instances.TITLE -> "Dentist"
                        CalendarContract.Instances.BEGIN -> begin
                        CalendarContract.Instances.END -> begin + 3600000L
                        CalendarContract.Instances.ALL_DAY -> 0
                        CalendarContract.Instances.RRULE -> if (recurring) "FREQ=WEEKLY" else null
                        CalendarContract.Instances.RDATE -> null
                        CalendarContract.Instances.CALENDAR_ACCESS_LEVEL -> if (readOnly) CalendarContract.Calendars.CAL_ACCESS_READ else CalendarContract.Calendars.CAL_ACCESS_OWNER
                        CalendarContract.Instances.CALENDAR_DISPLAY_NAME -> "Personal"
                        else -> null
                    } }.toTypedArray()) }
                } else addRow(arrayOf(status))
            }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            inserts++; insertUri = uri; insertValues = values
            status = values!!.getAsInteger(CalendarContract.Events.STATUS)
            return Uri.parse("content://${CalendarContract.AUTHORITY}/events/1001")
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
            updates++
            if (refuseUpdate) return 0
            status = values!!.getAsInteger(CalendarContract.Events.STATUS)
            return 1
        }
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = error("No deletions permitted in this test")
    }
}
