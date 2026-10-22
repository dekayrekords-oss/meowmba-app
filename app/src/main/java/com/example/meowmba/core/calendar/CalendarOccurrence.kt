package com.example.meowmba.core.calendar

/** Provider identity captured with a Flow card; never inferred from its headline. */
data class CalendarOccurrence(val eventId: Long, val title: String, val begin: Long, val end: Long)
