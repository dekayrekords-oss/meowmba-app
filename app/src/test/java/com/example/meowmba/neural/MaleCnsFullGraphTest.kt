package com.example.meowmba.neural

import com.example.meowmba.core.neural.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MaleCnsFullGraphTest {
    private val request = MaleCnsRequest("a".repeat(36), 1, 0f, 0f, 0f, 0f, 0f, 0f, MaleCnsGraphProfile.FULL)
    private val response = MaleCnsSnapshot(50f, 250f, 1000, 170000, 7000,
        5f, 400f, 80f, 300f, 60f, 150f, 10f, 10f, 30f,
        MaleCnsGraphProfile.FULL, 160f, 50f, 25f, 10f, 20f, 40f)

    private fun wire(): JsonObject = buildJsonObject {
        put("protocol", 2); put("graph", "full"); put("session", request.session); put("seq", 1)
        put("asset_sha256", request.graph.asset); put("neurons", 176422)
        put("pairs", 25862574); put("synapses", 125024863)
        put("sim_ms", 50); put("compute_ms", 250); put("active", 1000); put("silent", 170000); put("spikes", 7000)
        put("mean_hz", 5); put("max_hz", 400)
        for (key in listOf("feed", "escape", "jump", "groom", "wing", "left", "right", "groom2", "forward", "backward", "halt", "landing", "takeoff")) put("${key}_hz", 0)
    }

    @Test fun fullGraphRequiresExactIdentityAndEveryMeasuredMotorPopulation() {
        assertEquals(MaleCnsGraphProfile.FULL, MaleCnsSnapshot.parse(wire().toString(), request).graph)
        val changes = mapOf("graph" to JsonPrimitive("retained"), "protocol" to JsonPrimitive(1),
            "pairs" to JsonPrimitive(6287749), "synapses" to JsonPrimitive(90296905),
            "asset_sha256" to JsonPrimitive(MaleCnsSnapshot.ASSET), "forward_hz" to JsonPrimitive("NaN"),
            "landing_hz" to JsonPrimitive(-1), "takeoff_hz" to JsonPrimitive(2001))
        for ((key, value) in changes) assertTrue(key, runCatching {
            MaleCnsSnapshot.parse(JsonObject(wire() + (key to value)).toString(), request)
        }.isFailure)
        for (key in listOf("graph", "groom2_hz", "forward_hz", "backward_hz", "halt_hz", "landing_hz", "takeoff_hz"))
            assertTrue("missing $key", runCatching { MaleCnsSnapshot.parse(JsonObject(wire() - key).toString(), request) }.isFailure)
        assertTrue(runCatching { MaleCnsSnapshot.parse(wire().toString(), request.copy(graph = MaleCnsGraphProfile.RETAINED)) }.isFailure)
    }

    @Test fun fullGraphUsesSlowerCadenceAndMeasuredBodyOutputExpires() = runTest {
        var now = 0L; var calls = 0
        val transport = object : MaleCnsTransport {
            override suspend fun step(request: MaleCnsRequest): Result<MaleCnsSnapshot> {
                calls++; assertEquals("full", request.parameters()["graph"]); return Result.success(response)
            }
            override suspend fun stop(session: String) {}
        }
        val core = MaleCnsRemoteCore(this, transport, { now }, StandardTestDispatcher(testScheduler), MaleCnsGraphProfile.FULL)
        val input = FloatArray(NeuralFeatures.INPUT.size)
        core.step(input, 200f); runCurrent()
        val motor = core.flyMotorNow()
        assertTrue(motor.active); assertEquals(.25f, motor.turn); assertEquals(.5f, motor.forward)
        assertEquals(.8f, motor.grooming); assertEquals(1f, motor.escape)
        now = 200; core.step(input, 200f); runCurrent(); assertEquals(1, calls)
        now = 400; core.step(input, 200f); runCurrent(); assertEquals(2, calls)
        now = 2201
        assertFalse(core.ready()); assertEquals(FlyMotorIntent.NONE, core.flyMotorNow())
        assertFalse(core.diagnostics().extra.containsKey("malecns.forward_hz"))
        core.pause(); runCurrent()
    }

    @Test fun oldGraphCannotMasqueradeAsFullThroughACustomTransport() = runTest {
        val transport = object : MaleCnsTransport {
            override suspend fun step(request: MaleCnsRequest) = Result.success(response.copy(graph = MaleCnsGraphProfile.RETAINED))
            override suspend fun stop(session: String) {}
        }
        val core = MaleCnsRemoteCore(this, transport, { 0L }, StandardTestDispatcher(testScheduler), MaleCnsGraphProfile.FULL)
        core.step(FloatArray(NeuralFeatures.INPUT.size), 200f); runCurrent()
        assertFalse(core.ready()); assertEquals(FlyMotorIntent.NONE, core.flyMotorNow())
        core.pause(); runCurrent()
    }

    @Test fun legacyRequestAndMissingBodyMeasurementsRemainCompatible() {
        val old = request.copy(graph = MaleCnsGraphProfile.RETAINED)
        assertFalse(old.parameters().containsKey("graph"))
        val legacy = wire().toMutableMap().apply {
            put("protocol", JsonPrimitive(1)); remove("graph"); put("asset_sha256", JsonPrimitive(MaleCnsSnapshot.ASSET))
            put("pairs", JsonPrimitive(6287749)); put("synapses", JsonPrimitive(90296905))
            for (key in listOf("groom2", "forward", "backward", "halt", "landing", "takeoff")) remove("${key}_hz")
        }
        assertNull(MaleCnsSnapshot.parse(JsonObject(legacy).toString(), old).forwardHz)
    }

    @Test fun shadowClassicAndCoolingHaveNoBodyAuthority() = runTest {
        var tier = 0
        val transport = object : MaleCnsTransport {
            override suspend fun step(request: MaleCnsRequest) = Result.success(response)
            override suspend fun stop(session: String) {}
        }
        val core = MaleCnsRemoteCore(this, transport, { 0L }, StandardTestDispatcher(testScheduler), MaleCnsGraphProfile.FULL)
        val controller = NeuralBehaviorController(coreFactory = { core }, initialMode = NeuralMode.SHADOW,
            initialSubstrate = NeuralSubstrate.MALECNS_REMOTE, thermalTier = { tier }, telemetry = NeuralTelemetry.NONE)
        controller.tick(200); runCurrent(); controller.tick(200)
        assertTrue(core.ready()); assertEquals(FlyMotorIntent.NONE, controller.flyMotorNow())
        controller.setMode(NeuralMode.NEURAL); controller.tick(200); runCurrent(); controller.tick(200)
        assertTrue(controller.flyMotorNow().active)
        tier = 2; controller.tick(200); runCurrent(); assertEquals(FlyMotorIntent.NONE, controller.flyMotorNow())
        controller.setMode(NeuralMode.LEGACY); assertEquals(FlyMotorIntent.NONE, controller.flyMotorNow())
        core.pause(); runCurrent()
    }
}
