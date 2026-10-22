package com.example.meowmba.flow

import com.example.meowmba.core.flow.*
import com.example.meowmba.core.flow.sources.AppIntentions
import com.example.meowmba.ui.flow.AccessSetupState
import org.junit.Assert.*
import org.junit.Test

class FlowCommentsTest {
    @Test fun explicitReferenceCancellationOnly() {
        listOf("this event is canceled", "This event is cancelled!", "cancel it", "please cancel this occurrence",
            "this meeting has been canceled", "this even is canceled").forEach {
            assertTrue(it, FlowComments.requestsCancellation(it))
        }
        listOf("is this event canceled?", "this event is not canceled", "if this event is canceled",
            "maybe cancel it", "don't cancel it", "cancel all events", "cancel the series", "cancel tomorrow's other event",
            "she said this event is canceled", "\"this event is canceled\"", "this event is canceled but keep it",
            "I will be late", "please cancel Gmail").forEach { assertFalse(it, FlowComments.requestsCancellation(it)) }
    }
    private val access = AppIntentions.Access(true, true, true)
    private fun intentions(events: List<CalendarItem>) = AppIntentions.build(Fx.at(9), Fx.zone, emptyList(), emptyList(), events, access)
    @Test fun identicalTitlesAndTimesStillHaveDistinctProviderTargets() {
        val title = "Long title ".repeat(12)
        val items = intentions(listOf(CalendarItem(title, Fx.at(12), Fx.at(13), false, 11),
            CalendarItem(title, Fx.at(12), Fx.at(13), false, 12)))
        assertEquals(2, items.map { it.id }.distinct().size)
        assertEquals(listOf(11L, 12L), items.map { FlowComments.target(it)!!.eventId })
        assertEquals(title, FlowComments.target(items.first())!!.title)
    }
    @Test fun legacyAndNonCalendarItemsCannotSupplyDestructiveTarget() {
        val legacy = intentions(listOf(CalendarItem("Test", Fx.at(12), Fx.at(13), false))).single()
        assertNull(FlowComments.target(legacy))
        val current = intentions(listOf(CalendarItem("Test", Fx.at(12), Fx.at(13), false, 1))).single()
        assertNull(FlowComments.target(current.copy(id = "msg-1")))
        assertNull(FlowComments.target(current.copy(facts = current.facts + ("calendar_event_id" to "-1"))))
    }
    @Test fun readOnlyAccessKeepsCalendarSetupVisible() {
        val items = AppIntentions.build(Fx.at(9), Fx.zone, emptyList(), emptyList(), emptyList(), access.copy(calendarWrite = false))
        assertEquals(listOf(AppIntentions.SETUP_CALENDAR), items.map { it.id })
        assertTrue(AccessSetupState(true, false, false, false, false, true).calendarStatus.contains("Read only"))
        assertTrue(AccessSetupState(true, true, false, false, false, false).calendarStatus.startsWith("Ready"))
    }
    @Test fun sourceRefreshRetainsOnlyUserCommentFields() {
        val merged = FlowComments.retainComment(mapOf("calendar_title" to "New"),
            mapOf(FlowComments.TEXT to "Bring notes", FlowComments.AT to "1", "calendar_title" to "Old"))
        assertEquals("New", merged["calendar_title"])
        assertEquals("Bring notes", merged[FlowComments.TEXT])
        assertEquals("1", merged[FlowComments.AT])
    }
}
