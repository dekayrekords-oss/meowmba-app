package com.example.meowmba.flow

import android.content.Context
import android.database.MatrixCursor
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.flow.sources.CalendarSource
import com.example.meowmba.flow.Fx.at
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarSourceTest {
    @Test fun mapsRowsSkippingAllDayAndFinished() {
        val c = MatrixCursor(CalendarSource.PROJECTION).apply {
            addRow(arrayOf("Standup", at(9), at(9, 15), 0, 1, 1))            // finished
            addRow(arrayOf("Holiday", at(0), at(23, 59), 1, 0, 1))          // all day
            addRow(arrayOf("Tester check-in", at(15, 30), at(15, 50), 0, 1, 1))
            addRow(arrayOf("", at(17), at(18), 0, 0, 1))
        }
        val items = CalendarSource.mapRows(c, at(15, 12))
        assertEquals(listOf("Tester check-in", "Busy"), items.map { it.title })
        assertTrue(items.first().withPeople)
    }

    @Test fun withoutPermissionReturnsNothing() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(CalendarSource(ctx).upcoming(at(9)).isEmpty())
    }
}
