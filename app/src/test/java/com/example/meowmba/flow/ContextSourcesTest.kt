package com.example.meowmba.flow

import com.example.meowmba.core.car.DrivingState
import com.example.meowmba.core.flow.Activity
import com.example.meowmba.core.flow.CalendarItem
import com.example.meowmba.core.flow.InMemoryIntentionStore
import com.example.meowmba.core.flow.Surface
import com.example.meowmba.core.flow.WorkContext
import com.example.meowmba.core.flow.WorkState
import com.example.meowmba.core.flow.WorkStateDetector
import com.example.meowmba.core.flow.sources.EvidenceMappers
import com.example.meowmba.core.flow.sources.SituationAssembler
import com.example.meowmba.flow.Fx.at
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextSourcesTest {

    @Test fun notificationsNeverCarryBodiesAndSkipNoise() {
        val e = EvidenceMappers.fromNotification("com.google.android.gm", "Gmail", "email", "Google Play Console", at(11, 52))!!
        assertEquals("mail", e.key)
        assertEquals("New from Google Play Console", e.label)
        assertFalse(e.payload.keys.any { it.contains("body") || it.contains("text") })
        assertNull(EvidenceMappers.fromNotification("com.spotify.music", "Spotify", "transport", "Song", at(12)))
        assertNull(EvidenceMappers.fromNotification("com.x", "X", "msg", "Hi", at(12), ongoing = true))
        assertNull(EvidenceMappers.fromNotification("com.example.meowmba", "Meow", "msg", "Hi", at(12)))
    }

    @Test fun drivingMapsToMovement() {
        val e = EvidenceMappers.fromDriving(DrivingState(isDriving = true, speedMph = 34f), at(16, 58))
        assertEquals(Activity.DRIVING.name, e.payload["activity"])
    }

    private fun assembler(events: List<CalendarItem> = emptyList(), ctx: WorkContext = WorkContext(activelyWorking = true)) =
        SituationAssembler(InMemoryIntentionStore(), WorkStateDetector(Fx.zone), { ctx }, { true }, { events },
            { Surface.FLOW_ON_SCREEN }, zone = Fx.zone)

    @Test fun deepWorkDateline() = runTest {
        val a = assembler(listOf(CalendarItem("Lunch", at(12), at(13), withPeople = false)))
        a.observe(EvidenceMappers.fromPlace("Office", true, at(9)))
        a.observe(EvidenceMappers.fromForegroundApp("com.jetbrains", "Android Studio · Meowmba", at(9, 15), setOf("com.jetbrains")))
        val s = a.current(at(9, 40))
        assertEquals(listOf("Office", "Android Studio · Meowmba", "Free till 12:00"), s.signals.map { it.label })
        assertEquals(WorkState.WORKING, s.work.state)
        assertEquals("Office", s.place)
    }

    @Test fun newEmailTakesTheLastSlotAndIsFresh() = runTest {
        val a = assembler()
        a.observe(EvidenceMappers.fromPlace("Office", true, at(9)))
        a.observe(EvidenceMappers.fromForegroundApp("com.jetbrains", "Android Studio", at(11), emptySet()))
        a.observe(EvidenceMappers.fromNotification("com.google.android.gm", "Gmail", "email", "Google Play", at(11, 51))!!)
        val s = a.current(at(11, 52))
        assertEquals(3, s.signals.size)
        assertEquals("New from Google Play", s.signals.last().label)
        assertTrue(s.signals.last().fresh)
    }

    @Test fun imminentMeetingIsFresh() = runTest {
        val a = assembler(listOf(CalendarItem("Tester check-in", at(15, 30), at(15, 50), withPeople = true)))
        val cal = a.current(at(15, 12)).signals.first { it.key == "cal" }
        assertEquals("Tester check-in in 18 min", cal.label)
        assertTrue(cal.fresh)
    }

    @Test fun drivingLabelUsesPlace() = runTest {
        val a = assembler()
        a.observe(EvidenceMappers.fromPlace("Main St", true, at(16, 55)))
        a.observe(EvidenceMappers.fromDriving(DrivingState(isDriving = true, speedMph = 30f), at(16, 58)))
        val s = a.current(at(16, 58))
        assertEquals(Activity.DRIVING, s.activity)
        assertEquals("Driving · Main St", s.signals.first().label)
    }
}
