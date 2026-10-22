package com.example.meowmba.core.calendar

import android.provider.CalendarContract

/** A missing provider status is not a cancellation. Keep SQL NULL rows visible. */
object CalendarEventSelection {
    val active = "(${CalendarContract.Events.STATUS} IS NULL OR ${CalendarContract.Events.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})"
}
