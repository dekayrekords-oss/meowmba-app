package com.example.meowmba.flow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.car.DrivingState
import com.example.meowmba.core.flow.Assessment
import com.example.meowmba.core.flow.FlowRuntime
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.InMemoryIntentionStore
import com.example.meowmba.core.flow.Intention
import com.example.meowmba.core.flow.Opportunity
import com.example.meowmba.core.flow.RouteFacts
import com.example.meowmba.core.flow.Situation
import com.example.meowmba.core.flow.Surface
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.core.flow.decide.Assessor
import com.example.meowmba.core.flow.delivery.FlowActionBus
import com.example.meowmba.core.flow.delivery.FlowNotifier
import com.example.meowmba.core.flow.sources.EvidenceMappers
import com.example.meowmba.core.flow.Certainty
import com.example.meowmba.core.flow.FlowInterrupt
import com.example.meowmba.core.flow.IntentionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlowRuntimeTest {
    @Test fun providerIdentityUpgradeKeepsUsersLaterChoice() = runTest {
        val start = Fx.at(12)
        val old = Fx.bulb.copy(id = "cal-$start-${"Dentist".hashCode().toUInt()}", text = "Dentist at noon",
            status = IntentionStatus.SNOOZED, snoozedUntil = start)
        val fresh = com.example.meowmba.core.flow.sources.AppIntentions.build(Fx.at(9), Fx.zone, emptyList(), emptyList(),
            listOf(com.example.meowmba.core.flow.CalendarItem("Dentist", start, Fx.at(13), false, 7)),
            com.example.meowmba.core.flow.sources.AppIntentions.Access(true, true, true)).single()
        val store = InMemoryIntentionStore(listOf(old))
        val rt = FlowRuntime(ctx, backgroundScope, { true }, store, notifier = FakeNotifier())
        rt.syncManaged(listOf(fresh), listOf("cal-"))
        assertEquals(IntentionStatus.SNOOZED, store.find(fresh.id)?.status)
        assertEquals(start, store.find(fresh.id)?.snoozedUntil)
    }
    @Test fun commentsSurviveManagedRefreshAndDoNotCloseTheFlow() = runTest {
        val original = Fx.bulb.copy(id = "cal-event-7-123", facts = mapOf("calendar_title" to "First"))
        val store = InMemoryIntentionStore(listOf(original))
        val rt = FlowRuntime(ctx, backgroundScope, { true }, store, notifier = FakeNotifier())
        assertTrue(rt.saveComment(original.id, "Bring notes"))
        rt.syncManaged(listOf(original.copy(facts = mapOf("calendar_title" to "Updated"))), listOf("cal-"))
        assertEquals("Bring notes", store.find(original.id)?.facts?.get(com.example.meowmba.core.flow.FlowComments.TEXT))
        assertEquals("Updated", store.find(original.id)?.facts?.get("calendar_title"))
        assertEquals(IntentionStatus.OPEN, store.find(original.id)?.status)
        store.setStatus(original.id, IntentionStatus.DROPPED)
        org.junit.Assert.assertFalse(rt.saveComment(original.id, "Late comment"))
    }
    @Test fun exactCalendarRetirementDoesNotDropDuplicateTitle() = runTest {
        val apps = com.example.meowmba.core.flow.sources.AppIntentions
        val events = apps.build(Fx.at(9), Fx.zone, emptyList(), emptyList(), listOf(
            com.example.meowmba.core.flow.CalendarItem("Dentist", Fx.at(12), Fx.at(13), false, 1),
            com.example.meowmba.core.flow.CalendarItem("Dentist", Fx.at(12), Fx.at(13), false, 2)),
            com.example.meowmba.core.flow.sources.AppIntentions.Access(true, true, true))
        val store = InMemoryIntentionStore(events)
        val rt = FlowRuntime(ctx, backgroundScope, { true }, store, notifier = FakeNotifier())
        rt.cancelCalendarOccurrence("Dentist", Fx.at(12), 2)
        assertEquals(IntentionStatus.OPEN, store.find(events[0].id)?.status)
        assertEquals(IntentionStatus.DROPPED, store.find(events[1].id)?.status)
    }
    @Test fun confirmedCalendarCancellationDropsOnlyThatOccurrence() = runTest {
        val title = "Dentist"
        val start = Fx.at(16)
        val id = "cal-$start-${title.hashCode().toUInt()}"
        val store = InMemoryIntentionStore(listOf(Fx.bulb.copy(id = id, text = "Dentist at 4 PM", dueAt = start), Fx.bulb))
        val rt = FlowRuntime(ctx, backgroundScope, { true }, store, notifier = FakeNotifier())
        rt.cancelCalendarOccurrence(title, start)
        assertEquals(IntentionStatus.DROPPED, store.find(id)?.status)
        assertEquals(IntentionStatus.OPEN, store.find(Fx.bulb.id)?.status)
    }
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private class FakeLaya : Assessor {
        var calls = 0
        override val id = "laya-test"
        override suspend fun available() = true
        override suspend fun assess(situation: Situation, intentions: List<Intention>): List<Assessment> {
            calls++
            return intentions.map { Assessment(it.id, 0.9, 0.92, Urgency.WORTH_IT, 0.8) }
        }
    }

    private class FakeNotifier : FlowNotifier {
        val posted = mutableListOf<FlowInterrupt>(); var cancelled = 0
        override fun post(interrupt: FlowInterrupt) { posted += interrupt }
        override fun cancel() { cancelled++ }
    }

    @Test fun endToEndStoreInterruptFromEvidenceToNotificationAndAction() = runTest {
        ctx.getSharedPreferences("meow_flow", Context.MODE_PRIVATE).edit().clear().commit()
        val spoken = mutableListOf<String>()
        val notifier = FakeNotifier()
        val laya = FakeLaya()
        val store = InMemoryIntentionStore(listOf(Fx.bulb))
        val rt = FlowRuntime(
            ctx, backgroundScope, { spoken += it; true }, store,
            surface = { Surface.OTHER_APP },
            route = { RouteFacts("pickup", 6, 12) },
            opportunities = { _, _ -> listOf(Opportunity("bulb", "Main Street Auto Parts", 180, 4, true, "right", Certainty.LIKELY)) },
            laya = laya, notifier = notifier, clock = { Fx.at(16, 58) },
        )
        rt.start(); runCurrent()
        rt.publish(EvidenceMappers.fromDriving(DrivingState(isDriving = true, speedMph = 30f), Fx.at(16, 58)))
        advanceTimeBy(2_100); runCurrent()

        val i = assertNotNull(rt.snapshot.value.interrupt).let { rt.snapshot.value.interrupt!! }
        assertEquals("bulb", i.item.intentionId)
        assertEquals(1, notifier.posted.size)
        assertTrue(spoken.single().contains("usually carries"))
        assertEquals("laya-test", rt.controller.engineUsed)

        FlowActionBus.emit(FlowActionBus.Action("bulb", "later")); runCurrent()
        assertNull(rt.snapshot.value.interrupt)
        assertEquals(IntentionStatus.SNOOZED, store.intentions.value.single().status)
        assertEquals(1, notifier.cancelled)
        rt.stop()
    }

    @Test fun settingsPersistAndJevStaysOffUnlessAllowed() = runTest {
        ctx.getSharedPreferences("meow_flow", Context.MODE_PRIVATE).edit().clear().commit()
        var jevCalls = 0
        val jev = object : Assessor {
            override val id = "jev"; override suspend fun available() = true
            override suspend fun assess(situation: Situation, intentions: List<Intention>): List<Assessment>? { jevCalls++; return null }
        }
        val rt = FlowRuntime(ctx, backgroundScope, { true }, InMemoryIntentionStore(listOf(Fx.bill)), jev = jev,
            notifier = FakeNotifier(), clock = { Fx.at(9) })
        rt.start(); runCurrent()
        assertEquals(0, jevCalls)
        rt.updateSettings(FlowSettings(allowJev = true, maxPerDay = 5)); runCurrent()
        rt.controller.recompute()
        assertEquals(1, jevCalls)
        val again = FlowRuntime(ctx, backgroundScope, { true }, notifier = FakeNotifier())
        assertEquals(5, again.settings.value.maxPerDay)
        assertTrue(again.settings.value.allowJev)
        rt.stop()
    }
}
