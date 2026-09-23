package com.example.meowmba.flow

import com.example.meowmba.core.flow.CalendarItem
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowPolicy
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.InMemoryIntentionStore
import com.example.meowmba.core.flow.IntentionCapture
import com.example.meowmba.core.flow.IntentionStatus
import com.example.meowmba.core.flow.InterruptLedger
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.core.flow.WorkContext
import com.example.meowmba.core.flow.WorkState
import com.example.meowmba.core.flow.WorkStateDetector
import com.example.meowmba.core.flow.sources.AppIntentions
import com.example.meowmba.flow.Fx.at
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regressions for the 157 review: empty Flow, night look in the morning, chat never captured. */
class FlowRepairTest {
    private val none = AppIntentions.Access(notifications = false, usage = false, calendar = false)
    private val all = AppIntentions.Access(notifications = true, usage = true, calendar = true)

    @Test fun appDataBecomesFlowItems() {
        val items = AppIntentions.build(at(9), Fx.zone,
            reminders = listOf(
                AppIntentions.Reminder(1, "Take vitamins", "09:30", true, userMade = true, lastRunAt = 0),
                AppIntentions.Reminder(2, "Morning brief", "09:40", true, userMade = false, lastRunAt = 0), // system seed
                AppIntentions.Reminder(3, "Late", "23:59", true, userMade = true, lastRunAt = 0),            // >12h
            ),
            projects = listOf(AppIntentions.Project(7, "Meowmba site", "Website", at(8))),
            calendar = listOf(CalendarItem("Tester check-in", at(15, 30), at(15, 50), withPeople = true)),
            access = all)
        assertEquals(listOf("reminder-1-2026-09-22", "project-7", "cal-"), items.map { it.id.take(if (it.id.startsWith("cal-")) 4 else 99) })
        assertEquals("Continue Meowmba site", items[1].text)
        assertEquals(FlowKind.PEOPLE, items[2].kind)
        assertEquals("Tester check-in at 3:30 PM", items[2].text)
    }

    @Test fun missingAccessShowsPlainConnectItemsOnly() {
        val items = AppIntentions.build(at(9), Fx.zone, emptyList(), emptyList(), emptyList(), none)
        assertEquals(setOf(AppIntentions.SETUP_NOTIFICATIONS, AppIntentions.SETUP_CALENDAR, AppIntentions.SETUP_USAGE), items.map { it.id }.toSet())
        assertTrue(items.all { it.facts[FlowPolicy.FACT_ACTION] == "Allow" })
        assertTrue(items.first().facts[FlowPolicy.FACT_BECAUSE]!!.contains("never the message text"))
        assertTrue(AppIntentions.build(at(9), Fx.zone, emptyList(), emptyList(), emptyList(), all).isEmpty())
    }

    @Test fun messageSenderBecomesReplyItem() {
        val m = AppIntentions.fromMessage("com.whatsapp", "WhatsApp", "Dana", at(12))!!
        assertEquals("Reply to Dana", m.text)
        assertEquals("Messaged you in WhatsApp.", m.facts[FlowPolicy.FACT_BECAUSE])
        assertEquals(m.id, AppIntentions.fromMessage("com.whatsapp", "", "dana", 0)!!.id) // removal matches by sender
        assertNull(AppIntentions.fromMessage("com.whatsapp", "WhatsApp", "  ", at(12)))
    }

    @Test fun rulesOnlyCaptureTakesExplicitNeedsButNotChitChat() = runTest {
        val c = IntentionCapture(null, zone = Fx.zone)
        assertEquals("Fix the rear light", c.capture("ugh, the rear light is out again", at(19))?.text)
        assertEquals("Call Dana", c.capture("remind me to call Dana", at(19))?.text)
        assertEquals("Finish the tester update", c.capture("I need to finish the tester update", at(19))?.text)
        assertNull(c.capture("call me Meow from now on", at(19)))
        assertNull(c.capture("should I watch a movie?", at(19)))
    }

    @Test fun earlyMorningIsNotEvening() {
        val d = WorkStateDetector(Fx.zone)
        val morning = d.estimate(at(8, 25), WorkContext())
        assertEquals(WorkState.OFF, morning.state)
        assertTrue(morning.beforeWork)
        val s = Fx.drivingPastStore().copy(now = at(8, 25), work = morning, opportunities = emptyList())
        val snap = FlowPolicy(Fx.zone).decide(s, listOf(Fx.walk, Fx.handoff),
            listOf(Fx.a("walk", Urgency.USEFUL), Fx.a("handoff", Urgency.WORTH_IT)), FlowSettings(), InterruptLedger()).snapshot
        assertFalse(snap.evening)
        assertTrue((listOfNotNull(snap.main) + snap.more).none { it.intentionId == "walk" }) // no leisure before work
        val evening = d.estimate(at(18, 40), WorkContext(minutesSinceWorkApp = 30, computerClosed = true))
        assertFalse(evening.beforeWork)
    }

    @Test fun syncKeepsUserChoicesAndClosesStaleItems() = runTest {
        val store = InMemoryIntentionStore()
        val rt = RuntimeHarness(store)
        val first = AppIntentions.build(at(9), Fx.zone, emptyList(), listOf(AppIntentions.Project(7, "Site", "Website", at(8))), emptyList(), none)
        rt.sync(first)
        store.setStatus(AppIntentions.SETUP_USAGE, IntentionStatus.SNOOZED, at(10))
        rt.sync(first)                                           // same list again: user's snooze survives
        assertEquals(IntentionStatus.SNOOZED, store.intentions.value.first { it.id == AppIntentions.SETUP_USAGE }.status)
        rt.sync(AppIntentions.build(at(9), Fx.zone, emptyList(), emptyList(), emptyList(), all)) // access granted, project stale
        assertTrue(store.intentions.value.filter { it.id.startsWith("setup-") || it.id.startsWith("project-") }.all { it.status == IntentionStatus.DONE })
    }

    /** Same algorithm as FlowRuntime.syncManaged, exercised without Android. */
    private class RuntimeHarness(val store: InMemoryIntentionStore) {
        suspend fun sync(fresh: List<com.example.meowmba.core.flow.Intention>) {
            val existing = store.intentions.value.associateBy { it.id }
            fresh.forEach { n ->
                val old = existing[n.id]
                if (old == null) store.upsert(n)
                else if (old.text != n.text || old.dueAt != n.dueAt || old.facts != n.facts)
                    store.upsert(n.copy(status = old.status, snoozedUntil = old.snoozedUntil, createdAt = old.createdAt))
            }
            val keep = fresh.map { it.id }.toSet()
            store.closeSatisfied(existing.values.filter { i -> i.id !in keep && AppIntentions.MANAGED_PREFIXES.any { i.id.startsWith(it) } &&
                (i.status == IntentionStatus.OPEN || i.status == IntentionStatus.SNOOZED) }.map { it.id })
        }
    }
}
