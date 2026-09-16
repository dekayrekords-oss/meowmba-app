package com.example.meowmba.neural

import com.example.meowmba.core.neural.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MaleCnsRemoteTest {
    private val response = MaleCnsSnapshot(50f, 40f, 1000, 170000, 7000,
        5f, 400f, 80f, 300f, 60f, 150f, 10f, 10f, 30f)

    private class Transport(val reply: suspend (MaleCnsRequest) -> Result<MaleCnsSnapshot>) : MaleCnsTransport {
        val requests = mutableListOf<MaleCnsRequest>()
        val stops = mutableListOf<String>()
        override suspend fun step(request: MaleCnsRequest): Result<MaleCnsSnapshot> { requests += request; return reply(request) }
        override suspend fun stop(session: String) { stops += session }
    }

    @Test fun freshMeasuredOutputExpiresAndFoodUsesAnExplicitEvent() = runTest {
        var clock = 0L
        val transport = Transport { Result.success(response) }
        val core = MaleCnsRemoteCore(this, transport, { clock }, StandardTestDispatcher(testScheduler))
        val x = FloatArray(NeuralFeatures.INPUT.size)
        core.noteFood(); core.step(x, 200f)
        assertFalse(core.ready()); runCurrent()
        assertTrue(core.ready()); assertEquals(1f, transport.requests.single().food)
        assertEquals(1f, core.readout()[NeuralFeatures.OUTPUT.index(NeuralFeatures.OUT_STARTLE)])
        assertEquals(176422, core.diagnostics().units)
        clock = 1801
        assertFalse(core.ready()); assertTrue(core.readout().all { it == 0f })
        assertEquals(0f, core.diagnostics().extra.getValue("malecns.wing_power"))
        core.pause(); runCurrent()
    }

    @Test fun onlyOneRequestIsInFlightAndPauseRejectsLateResults() = runTest {
        var clock = 0L
        val gate = CompletableDeferred<Unit>()
        val transport = Transport { withContext(NonCancellable) { gate.await(); Result.success(response) } }
        val core = MaleCnsRemoteCore(this, transport, { clock }, StandardTestDispatcher(testScheduler))
        val input = FloatArray(NeuralFeatures.INPUT.size)
        core.step(input, 200f); runCurrent()
        clock = 1000; repeat(20) { core.step(input, 200f) }; runCurrent()
        assertEquals(1, transport.requests.size)
        core.pause(); gate.complete(Unit); runCurrent()
        assertFalse(core.ready()); assertEquals(1, transport.stops.size)
    }

    @Test fun slowResponsesAndConnectionErrorsBackOffWithoutQueuing() = runTest {
        var clock = 0L
        val transport = Transport { clock += 1900; Result.success(response) }
        val core = MaleCnsRemoteCore(this, transport, { clock }, StandardTestDispatcher(testScheduler))
        val input = FloatArray(NeuralFeatures.INPUT.size)
        core.step(input, 200f); runCurrent()
        assertFalse(core.ready())
        repeat(10) { core.step(input, 200f) }; runCurrent()
        assertEquals(1, transport.requests.size)
        clock += 2500; core.step(input, 200f); runCurrent()
        assertEquals(2, transport.requests.size)
        core.pause(); runCurrent()
    }

    @Test fun resetCreatesANewRemoteSessionAndClearsFood() = runTest {
        var clock = 0L
        val transport = Transport { Result.success(response) }
        val core = MaleCnsRemoteCore(this, transport, { clock }, StandardTestDispatcher(testScheduler))
        val input = FloatArray(NeuralFeatures.INPUT.size)
        core.noteFood(); core.step(input, 200f); runCurrent()
        core.reset(); runCurrent(); core.step(input, 200f); runCurrent()
        assertNotEquals(transport.requests[0].session, transport.requests[1].session)
        assertEquals(1L, transport.requests[1].seq); assertEquals(0f, transport.requests[1].food)
        core.pause(); runCurrent()
    }

    @Test fun controllerAutomaticallyRecoversAndSuspendsExternalWork() {
        var ready = false; var pauses = 0; var tier = 0
        val c = object : NeuralCore {
            override val substrate = NeuralSubstrate.MALECNS_REMOTE
            override val inputLayout = NeuralFeatures.INPUT
            override val outputLayout = NeuralFeatures.OUTPUT
            override fun step(input: FloatArray, dtMs: Float) {}
            override fun readout() = FloatArray(outputLayout.size) { .5f }
            override fun reset() {}
            override fun pause() { pauses++ }
            override fun ready() = ready
            override fun diagnostics() = CoreDiagnostics(substrate, 176422, 0f, 0f, 0, 0, 100, 0)
        }
        val controller = NeuralBehaviorController(coreFactory = { c }, initialMode = NeuralMode.NEURAL,
            initialSubstrate = NeuralSubstrate.MALECNS_REMOTE, thermalTier = { tier }, telemetry = NeuralTelemetry.NONE)
        controller.tick(200); assertEquals(FallbackReason.REMOTE_UNAVAILABLE, controller.fallback)
        ready = true; controller.tick(200); assertEquals(FallbackReason.NONE, controller.fallback)
        tier = 2; controller.tick(200); assertEquals(1, pauses)
        assertEquals(FallbackReason.THERMAL_SUSPENDED, controller.fallback)
        controller.setMode(NeuralMode.LEGACY); assertEquals(2, pauses)
        assertEquals(0f, controller.wingMotorNow())
    }

    @Test fun parserRejectsOtherSessionWrongGraphAndNonFiniteRates() {
        val request = MaleCnsRequest("a".repeat(36), 1, 0f, 0f, 0f, 0f, 0f, 0f)
        val valid = """{"protocol":1,"session":"${request.session}","seq":1,"asset_sha256":"${MaleCnsSnapshot.ASSET}","neurons":176422,"pairs":6287749,"synapses":90296905,"sim_ms":50,"compute_ms":30,"active":3000,"silent":170000,"spikes":1000,"mean_hz":1,"max_hz":300,"feed_hz":60,"escape_hz":0,"jump_hz":0,"groom_hz":0,"wing_hz":0,"left_hz":0,"right_hz":0}"""
        assertEquals(60f, MaleCnsSnapshot.parse(valid, request).feedHz)
        for (bad in listOf(valid.replace(request.session, "b".repeat(36)), valid.replace("176422", "7"),
            valid.replace("\"feed_hz\":60", "\"feed_hz\":\"NaN\""), valid.replace("\"seq\":1", "\"seq\":2"))) {
            assertTrue(runCatching { MaleCnsSnapshot.parse(bad, request) }.isFailure)
        }
    }
}
