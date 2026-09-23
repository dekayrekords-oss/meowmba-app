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
