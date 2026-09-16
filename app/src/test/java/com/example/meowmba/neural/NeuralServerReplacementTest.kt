package com.example.meowmba.neural

import com.example.meowmba.core.neural.*
import org.junit.Assert.*
import org.junit.Test

class NeuralServerReplacementTest {
    @Test fun serverReplacementStopsOldCoreAndKeepsSelectedModeWithFreshReadout() {
        val cores = mutableListOf<FakeServer>()
        val controller = NeuralBehaviorController(coreFactory = {
            FakeServer().also { core -> cores += core }
        }, initialMode = NeuralMode.NEURAL, initialSubstrate = NeuralSubstrate.MALECNS_REMOTE,
            telemetry = NeuralTelemetry.NONE)
        controller.tick(200)
        val old = cores.single()
        assertEquals(1, old.steps)
        controller.recreateCurrentSubstrate()
        assertEquals(1, old.pauses)
        assertEquals(2, cores.size)
        assertEquals(NeuralMode.NEURAL, controller.mode)
        assertEquals(NeuralSubstrate.MALECNS_REMOTE, controller.substrate)
        controller.tick(200)
        assertEquals(1, old.steps)
        assertEquals(1, cores.last().steps)
        assertEquals(FallbackReason.REMOTE_UNAVAILABLE, controller.fallback)
        assertFalse(controller.flyMotorNow().active)
    }

    private class FakeServer : NeuralCore {
        var steps = 0; var pauses = 0
        override val substrate = NeuralSubstrate.MALECNS_REMOTE
        override val inputLayout = NeuralFeatures.INPUT
        override val outputLayout = NeuralFeatures.OUTPUT
        override fun step(input: FloatArray, dtMs: Float) { steps++ }
        override fun readout() = FloatArray(outputLayout.size)
        override fun reset() { steps = 0 }
        override fun pause() { pauses++ }
        override fun ready() = false
        override fun diagnostics() = CoreDiagnostics(substrate, 176422, 0f, 0f, 0, 176422, 0, 0)
    }
}
