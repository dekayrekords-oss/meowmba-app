package com.example.meowmba.flow

import com.example.meowmba.core.flow.Assessment
import com.example.meowmba.core.flow.Evidence
import com.example.meowmba.core.flow.EvidenceBus
import com.example.meowmba.core.flow.EvidenceType
import com.example.meowmba.core.flow.FlowController
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.InMemoryIntentionStore
import com.example.meowmba.core.flow.InMemoryLedgerStore
import com.example.meowmba.core.flow.InMemoryOutcomeLog
import com.example.meowmba.core.flow.Intention
import com.example.meowmba.core.flow.IntentionStatus
import com.example.meowmba.core.flow.Outcome
import com.example.meowmba.core.flow.Situation
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.core.flow.FlowPolicy
import com.example.meowmba.core.flow.decide.Assessor
import com.example.meowmba.flow.Fx.a
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowControllerTest {

    private class CountingAssessor(val result: (List<Intention>) -> List<Assessment>) : Assessor {
        var calls = 0
        override val id = "counting"
        override suspend fun available() = true
        override suspend fun assess(situation: Situation, intentions: List<Intention>): List<Assessment> {
            calls++; return result(intentions)
        }
    }

    private fun TestScope.rig(settings: MutableStateFlow<FlowSettings> = MutableStateFlow(FlowSettings())): Rig {
        val store = InMemoryIntentionStore(listOf(Fx.bulb, Fx.handoff))
        val bus = EvidenceBus()
        val assessor = CountingAssessor { list ->
            list.map { if (it.id == "bulb") a("bulb", Urgency.WORTH_IT, 0.9, 0.92) else a(it.id, Urgency.WORTH_IT, 0.8) }
        }
        val outcomes = InMemoryOutcomeLog()
        val c = FlowController(
            scope = backgroundScope, store = store, bus = bus,
            situations = { Fx.drivingPastStore() }, assessor = assessor, settings = settings,
            ledgerStore = InMemoryLedgerStore(), outcomes = outcomes, policy = FlowPolicy(Fx.zone),
            clock = { Fx.at(16, 58) },
        )
        return Rig(c, store, bus, assessor, settings, outcomes)
    }

    private class Rig(
        val c: FlowController, val store: InMemoryIntentionStore, val bus: EvidenceBus,
        val assessor: CountingAssessor, val settings: MutableStateFlow<FlowSettings>, val outcomes: InMemoryOutcomeLog,
    )

    @Test fun startProducesSnapshotWithInterrupt() = runTest {
        val r = rig(); r.c.start(); runCurrent()
        assertNotNull(r.c.snapshot.value.interrupt)
        assertEquals(1, r.assessor.calls)
        assertEquals(Outcome.INTERRUPTED, r.outcomes.recent().single().outcome)
    }

    @Test fun evidenceBurstIsDebouncedIntoOneDecision() = runTest {
        val r = rig(); r.c.start(); runCurrent()
        repeat(5) { r.bus.publish(Evidence(EvidenceType.MOVEMENT, Fx.at(16, 58), "loc", "Main St")) ; advanceTimeBy(300) }
        advanceTimeBy(FlowController.DEBOUNCE_MS + 100); runCurrent()
        assertEquals(2, r.assessor.calls)
    }

    @Test fun settingsChangeReappliesPolicyWithoutCallingTheModel() = runTest {
        val r = rig(); r.c.start(); runCurrent()
        r.c.onLater("bulb"); advanceTimeBy(FlowController.DEBOUNCE_MS + 100); runCurrent()
        val before = r.assessor.calls
        r.settings.value = FlowSettings(minUrgency = Urgency.URGENT); runCurrent()
        assertEquals(before, r.assessor.calls)
    }

    @Test fun answeringAnInterruptSnoozesAndSuppressesIt() = runTest {
        val r = rig(); r.c.start(); runCurrent()
        r.c.onLater("bulb")
        assertNull(r.c.snapshot.value.interrupt)
        assertEquals(IntentionStatus.SNOOZED, r.store.intentions.value.first { it.id == "bulb" }.status)
        advanceTimeBy(FlowController.DEBOUNCE_MS + 100); runCurrent()
        assertNull(r.c.snapshot.value.interrupt)
        assertTrue(r.outcomes.recent().any { it.outcome == Outcome.SNOOZED })
    }

    @Test fun repeatedRecomputeDoesNotRecountTheSameInterrupt() = runTest {
        val ledger = InMemoryLedgerStore()
        val store = InMemoryIntentionStore(listOf(Fx.bulb))
        val c = FlowController(backgroundScope, store, EvidenceBus(), { Fx.drivingPastStore() },
            CountingAssessor { listOf(a("bulb", Urgency.WORTH_IT, 0.9, 0.92)) }, MutableStateFlow(FlowSettings()),
            ledger, policy = FlowPolicy(Fx.zone), clock = { Fx.at(16, 58) })
        c.recompute(); c.recompute(); c.recompute()
        assertEquals(1, ledger.load().countToday)
    }
}
