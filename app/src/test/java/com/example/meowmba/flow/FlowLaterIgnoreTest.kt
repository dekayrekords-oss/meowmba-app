package com.example.meowmba.flow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.flow.Assessment
import com.example.meowmba.core.flow.EvidenceBus
import com.example.meowmba.core.flow.FlowController
import com.example.meowmba.core.flow.FlowPolicy
import com.example.meowmba.core.flow.FlowRuntime
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.core.flow.InMemoryIntentionStore
import com.example.meowmba.core.flow.InMemoryLedgerStore
import com.example.meowmba.core.flow.Intention
import com.example.meowmba.core.flow.IntentionStatus
import com.example.meowmba.core.flow.Situation
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.core.flow.decide.Assessor
import com.example.meowmba.core.flow.delivery.FlowNotifier
import com.example.meowmba.flow.Fx.a
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Later" and "Ignore" on the Flow page.
 *
 * Later used to change the store and then wait for a debounced, model-backed recompute before the
 * card left the screen; on a phone where the model pass takes many seconds it looked like the button
 * did nothing. These tests hold the model pass open to prove the card leaves at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlowLaterIgnoreTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private class Gated : Assessor {
        override val id = "gated"
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun available() = true
        override suspend fun assess(situation: Situation, intentions: List<Intention>): List<Assessment> {
            gate?.await()
            return intentions.map { a(it.id, Urgency.USEFUL, 0.6) }
        }
    }

    private fun shown(s: FlowSnapshot) = (listOfNotNull(s.interrupt?.item, s.main) + s.more).map { it.intentionId }

    private fun TestScope.rig(vararg items: Intention): Triple<FlowController, InMemoryIntentionStore, Gated> {
        val store = InMemoryIntentionStore(items.toList())
        val assessor = Gated()
        val c = FlowController(backgroundScope, store, EvidenceBus(), { Fx.evening() }, assessor, MutableStateFlow(FlowSettings()),
            InMemoryLedgerStore(), policy = FlowPolicy(Fx.zone), clock = { Fx.at(18, 40) })
        return Triple(c, store, assessor)
    }

    @Test fun laterClearsTheCardAtOnceEvenWhileAModelPassIsRunning() = runTest {
        val (c, store, assessor) = rig(Fx.handoff, Fx.walk)
        c.start(); runCurrent()
        assertTrue("handoff" in shown(c.snapshot.value))

        assessor.gate = CompletableDeferred()          // the next model pass hangs, like a slow phone
        c.schedule(immediate = true); runCurrent()
        c.onLater("handoff")

        assertFalse("handoff" in shown(c.snapshot.value))
        assertEquals(IntentionStatus.SNOOZED, store.find("handoff")?.status)
        assertTrue("walk" in shown(c.snapshot.value))
    }

    @Test fun aPassThatStartedBeforeTheTapCannotBringTheCardBack() = runTest {
        val (c, _, assessor) = rig(Fx.handoff, Fx.walk)
        c.start(); runCurrent()

        val gate = CompletableDeferred<Unit>().also { assessor.gate = it }
        c.schedule(immediate = true); runCurrent()      // pass begins with handoff still open
        c.onLater("handoff")
        gate.complete(Unit); runCurrent()               // ...and finishes after the tap

        assertFalse("handoff" in shown(c.snapshot.value))
        advanceTimeBy(FlowController.DEBOUNCE_MS + 100); runCurrent()
        assertFalse("handoff" in shown(c.snapshot.value))
    }

    @Test fun doneAlsoClearsTheCardAtOnce() = runTest {
        val (c, store, assessor) = rig(Fx.handoff, Fx.walk)
        c.start(); runCurrent()
        assessor.gate = CompletableDeferred()
        c.schedule(immediate = true); runCurrent()
        c.onPrimary("handoff")
        assertFalse("handoff" in shown(c.snapshot.value))
        assertEquals(IntentionStatus.DONE, store.find("handoff")?.status)
    }

    @Test fun anActionThatOnlyOpensSomethingKeepsTheCard() = runTest {
        val (c, _, _) = rig(Fx.handoff, Fx.walk)
        c.start(); runCurrent()
        c.onPrimary("handoff", completes = false)
        assertTrue("handoff" in shown(c.snapshot.value))
    }

    @Test fun ignoreClearsTheCardAndMarksItIgnored() = runTest {
        val (c, store, assessor) = rig(Fx.handoff, Fx.walk)
        c.start(); runCurrent()
        assessor.gate = CompletableDeferred()
        c.schedule(immediate = true); runCurrent()
        c.onIgnore("handoff")
        assertFalse("handoff" in shown(c.snapshot.value))
        assertEquals(IntentionStatus.IGNORED, store.find("handoff")?.status)
        assertEquals(listOf("handoff"), store.ignored().map { it.id })
    }

    @Test fun anIgnoredItemDoesNotComeBackAfterTheHourOrAfterMoreEvidence() = runTest {
        val (c, store, _) = rig(Fx.handoff, Fx.walk)
        c.start(); runCurrent()
        c.onIgnore("handoff")
        advanceTimeBy(FlowController.DEBOUNCE_MS + 100); runCurrent()
        c.recompute()
        assertFalse("handoff" in shown(c.snapshot.value))
        assertFalse(store.open(Fx.at(18, 40) + 3 * FlowController.SNOOZE_MS).any { it.id == "handoff" })
    }

    @Test fun restoreBringsIgnoredItemsBack() = runTest {
        val (c, store, _) = rig(Fx.handoff, Fx.walk)
        c.start(); runCurrent()
        c.onIgnore("handoff"); c.onIgnore("walk")
        assertEquals(2, c.restoreIgnored())
        runCurrent(); advanceTimeBy(FlowController.DEBOUNCE_MS + 100); runCurrent()
        assertEquals(IntentionStatus.OPEN, store.find("handoff")?.status)
        assertTrue("handoff" in shown(c.snapshot.value))
        assertEquals(0, c.restoreIgnored())
    }

    @Test fun laterStillReturnsAfterItsHour() = runTest {
        val (c, store, _) = rig(Fx.handoff)
        c.start(); runCurrent()
        c.onLater("handoff")
        assertFalse(store.open(Fx.at(18, 40)).any { it.id == "handoff" })
        assertTrue(store.open(Fx.at(18, 40) + FlowController.SNOOZE_MS + 1).any { it.id == "handoff" })
    }

    // ---- managed re-adds -------------------------------------------------------------------

    private class Quiet : FlowNotifier {
        override fun post(interrupt: com.example.meowmba.core.flow.FlowInterrupt) {}
        override fun cancel() {}
    }

    private fun TestScope.runtime(store: InMemoryIntentionStore) =
        FlowRuntime(ctx, backgroundScope, { true }, store, notifier = Quiet(), clock = { Fx.at(18, 40) })

    @Test fun anIgnoredManagedItemIsNotReAddedOrRevivedBySync() = runTest {
        val source = Fx.plan.copy(id = "project-build157", text = "Plan build 157")
        val store = InMemoryIntentionStore(listOf(source))
        val rt = runtime(store)
        rt.onIgnore(source.id)

        // The source keeps producing it, first unchanged, then with new text and due time.
        rt.syncManaged(listOf(source), listOf("project-"))
        assertEquals(IntentionStatus.IGNORED, store.find(source.id)?.status)
        rt.syncManaged(listOf(source.copy(text = "Plan build 158", dueAt = source.dueAt!! + 1_000)), listOf("project-"))
        assertEquals(IntentionStatus.IGNORED, store.find(source.id)?.status)
        assertEquals("Plan build 158", store.find(source.id)?.text)

        // And when the source stops producing it, it is not closed into DONE (which a new message would reopen).
        rt.syncManaged(emptyList(), listOf("project-"))
        assertEquals(IntentionStatus.IGNORED, store.find(source.id)?.status)
    }

    @Test fun closingSatisfiedItemsNeverTurnsIgnoredIntoDone() = runTest {
        val store = InMemoryIntentionStore(listOf(Fx.walk.copy(status = IntentionStatus.IGNORED)))
        store.closeSatisfied(listOf("walk"))
        assertEquals(IntentionStatus.IGNORED, store.find("walk")?.status)
    }

    @Test fun runtimeExposesTheIgnoredCountAndRestore() = runTest {
        val store = InMemoryIntentionStore(listOf(Fx.walk, Fx.album))
        val rt = runtime(store)
        rt.onIgnore("walk")
        assertEquals(1, rt.ignoredCount())
        assertEquals(1, rt.restoreIgnored())
        assertEquals(0, rt.ignoredCount())
        assertEquals(IntentionStatus.OPEN, store.find("walk")?.status)
    }
}
