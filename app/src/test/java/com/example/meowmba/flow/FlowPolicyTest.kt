package com.example.meowmba.flow

import com.example.meowmba.core.flow.Channel
import com.example.meowmba.core.flow.FlowPolicy
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.InterruptLedger
import com.example.meowmba.core.flow.Surface
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.flow.Fx.a
import com.example.meowmba.flow.Fx.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FlowPolicyTest {
    private val policy = FlowPolicy(Fx.zone)
    private val today = LocalDate.of(2026, 9, 22)
    private val storeAssess = listOf(a("bulb", Urgency.WORTH_IT, relevant = 0.9, chance = 0.92))

    @Test fun partsStoreInterruptsWithDefaults() {
        val r = policy.decide(Fx.drivingPastStore(), listOf(Fx.bulb), storeAssess, FlowSettings(), InterruptLedger())
        val i = assertNotNull(r.snapshot.interrupt).let { r.snapshot.interrupt!! }
        assertEquals(Channel.SPEAK_AND_SHOW, i.channel)
        assertEquals("Main Street Auto Parts is on your right. They usually carry your brake-light bulb.", i.item.headline)
        assertTrue(i.spokenLine!!.contains("usually carries"))
        assertEquals("Main Street Auto Parts", i.countdown!!.label)
        assertEquals(1, r.ledger.countToday)
        assertEquals(at(16, 58), r.ledger.lastAt)
    }

    @Test fun neverClaimsStockUnlessConfirmed() {
        val r = policy.decide(Fx.drivingPastStore(), listOf(Fx.bulb), storeAssess, FlowSettings(), InterruptLedger())
        val text = listOfNotNull(r.snapshot.interrupt?.item?.headline, r.snapshot.interrupt?.item?.because, r.snapshot.interrupt?.spokenLine)
            .joinToString(" ").lowercase()
        assertFalse(text.contains("in stock"))
        assertFalse(text.contains(" have "))
    }

    @Test fun heldWhenBelowUserUrgency() {
        val r = policy.decide(Fx.drivingPastStore(), listOf(Fx.bulb), storeAssess, FlowSettings(minUrgency = Urgency.URGENT), InterruptLedger())
        assertNull(r.snapshot.interrupt)
        assertEquals("Held: this is \"Worth it\", and you only allow \"Urgent\" to interrupt.", r.snapshot.more.first().heldReason)
        assertEquals(0, r.ledger.countToday)
    }

    @Test fun heldWhenDailyCapReached() {
        val ledger = InterruptLedger(today, 3, at(9))
        val r = policy.decide(Fx.drivingPastStore(), listOf(Fx.bulb), storeAssess, FlowSettings(maxPerDay = 3), ledger)
        assertNull(r.snapshot.interrupt)
        assertEquals("Held: you've had 3 of your 3 interruptions today.", r.snapshot.more.first().heldReason)
    }

    @Test fun capResetsOnNewDay() {
        val ledger = InterruptLedger(today.minusDays(1), 3, at(9) - 86_400_000L)
        val r = policy.decide(Fx.drivingPastStore(), listOf(Fx.bulb), storeAssess, FlowSettings(maxPerDay = 3), ledger)
        assertNotNull(r.snapshot.interrupt)
        assertEquals(1, r.ledger.countToday)
    }

    @Test fun heldWhenInsideMinimumGap() {
        val ledger = InterruptLedger(today, 1, at(16, 30))
        val r = policy.decide(Fx.drivingPastStore(), listOf(Fx.bulb), storeAssess, FlowSettings(minGapMinutes = 60), ledger)
        assertEquals("Held: less than an hour since the last interruption.", r.snapshot.more.first().heldReason)
    }

    @Test fun otherAppDeliveryMatrix() {
        val other = Fx.drivingPastStore(surface = Surface.OTHER_APP)
        fun ch(s: FlowSettings) = policy.decide(other, listOf(Fx.bulb), storeAssess, s, InterruptLedger())
        assertEquals(Channel.NOTIFY, ch(FlowSettings(voice = true, notifyOtherApps = true)).snapshot.interrupt!!.channel)
        assertNotNull(ch(FlowSettings(voice = true, notifyOtherApps = true)).snapshot.interrupt!!.spokenLine)
        assertEquals(Channel.SPEAK_ONLY, ch(FlowSettings(voice = true, notifyOtherApps = false)).snapshot.interrupt!!.channel)
        val silent = ch(FlowSettings(voice = false, notifyOtherApps = true)).snapshot.interrupt!!
        assertEquals(Channel.NOTIFY, silent.channel)
        val off = ch(FlowSettings(voice = false, notifyOtherApps = false))
        assertNull(off.snapshot.interrupt)
        assertEquals("Held: voice and notifications are off while you're in another app.", off.snapshot.more.first().heldReason)
    }

    @Test fun onScreenWithoutVoiceIsShowOnly() {
        val r = policy.decide(Fx.drivingPastStore(), listOf(Fx.bulb), storeAssess, FlowSettings(voice = false), InterruptLedger())
        assertEquals(Channel.SHOW_ONLY, r.snapshot.interrupt!!.channel)
        assertNull(r.snapshot.interrupt!!.spokenLine)
    }

    @Test fun activeInterruptIsNotCountedTwice() {
        val first = policy.decide(Fx.drivingPastStore(), listOf(Fx.bulb), storeAssess, FlowSettings(), InterruptLedger())
        val again = policy.decide(Fx.drivingPastStore(), listOf(Fx.bulb), storeAssess, FlowSettings(), first.ledger, activeInterrupt = "bulb")
        assertNotNull(again.snapshot.interrupt)
        assertEquals(1, again.ledger.countToday)
    }

    @Test fun suppressedIntentionDoesNotInterruptAgain() {
        val r = policy.decide(Fx.drivingPastStore(), listOf(Fx.bulb), storeAssess, FlowSettings(), InterruptLedger(), suppressed = setOf("bulb"))
        assertNull(r.snapshot.interrupt)
        assertEquals("bulb", r.snapshot.main?.intentionId)
    }

    @Test fun detourLongerThanBufferDoesNotInterrupt() {
        val r = policy.decide(Fx.drivingPastStore(buffer = 2), listOf(Fx.bulb), storeAssess, FlowSettings(), InterruptLedger())
        assertNull(r.snapshot.interrupt)
    }

    @Test fun leisureHiddenWhileWorking() {
        val s = Fx.drivingPastStore().copy(opportunities = emptyList())
        val r = policy.decide(s, listOf(Fx.walk, Fx.handoff), listOf(a("walk", Urgency.WORTH_IT), a("handoff", Urgency.WORTH_IT)), FlowSettings(), InterruptLedger())
        assertEquals("handoff", r.snapshot.main?.intentionId)
        assertTrue(r.snapshot.more.none { it.intentionId == "walk" })
        assertFalse(r.snapshot.evening)
    }

    @Test fun eveningShowsLeisureAndOnlyOneWorkItem() {
        val r = policy.decide(
            Fx.evening(), listOf(Fx.plan, Fx.handoff, Fx.walk, Fx.album),
            listOf(a("plan", Urgency.USEFUL, 0.6), a("handoff", Urgency.USEFUL, 0.5), a("walk", Urgency.USEFUL, 0.8), a("album", Urgency.USEFUL, 0.7)),
            FlowSettings(), InterruptLedger(),
        )
        val ids = listOfNotNull(r.snapshot.main) .plus(r.snapshot.more).map { it.intentionId }
        assertTrue(r.snapshot.evening)
        assertTrue("walk" in ids && "album" in ids)
        assertEquals(1, ids.count { it == "plan" || it == "handoff" })
    }

    @Test fun atMostOneMainAndThreeMore() {
        val many = (1..8).map { Fx.handoff.copy(id = "w$it", text = "Task $it") }
        val r = policy.decide(Fx.evening().copy(work = Fx.drivingPastStore().work), many,
            many.mapIndexed { i, it -> a(it.id, Urgency.USEFUL, 0.5 + i * 0.05) }, FlowSettings(), InterruptLedger())
        assertNotNull(r.snapshot.main)
        assertEquals(FlowPolicy.MAX_MORE, r.snapshot.more.size)
    }

    @Test fun notNowAndLowRelevanceStayQuiet() {
        val r = policy.decide(Fx.drivingPastStore().copy(opportunities = emptyList()), listOf(Fx.handoff, Fx.bill),
            listOf(a("handoff", Urgency.NOT_NOW, 0.9), a("bill", Urgency.USEFUL, 0.2)), FlowSettings(), InterruptLedger())
        assertNull(r.snapshot.main)
        assertTrue(r.snapshot.more.isEmpty())
    }

    @Test fun dueItemsExplainWhenTheyAreDue() {
        val s = Fx.drivingPastStore().copy(now = at(9), opportunities = emptyList())
        val r = policy.decide(s, listOf(Fx.bill), listOf(a("bill", Urgency.WORTH_IT)), FlowSettings(), InterruptLedger())
        assertEquals("Due Wed at 11:00 AM.", r.snapshot.main!!.because)
        assertTrue("cal" in r.snapshot.main!!.sources)
    }
}
