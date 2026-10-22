package com.example.meowmba.core.neural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BancExperimentalCoreTest {
    private fun input(vararg values: Pair<String, Float>): FloatArray = FloatArray(NeuralFeatures.INPUT.size).also { x ->
        values.forEach { (name, value) -> x[NeuralFeatures.INPUT.index(name)] = value }
    }

    @Test fun auditedNociceptorPathChangesBoundedLiveReadout() {
        val core = BancExperimentalCore()
        core.step(input(), 40f)
        val resting = core.readout()
        core.reset()
        core.step(input(NeuralFeatures.TOUCH to 1f), 40f)
        val touched = core.readout()
        assertTrue(touched[NeuralFeatures.OUTPUT.index(NeuralFeatures.OUT_CAUTION)] > resting[NeuralFeatures.OUTPUT.index(NeuralFeatures.OUT_CAUTION)])
        assertTrue(touched[NeuralFeatures.OUTPUT.index(NeuralFeatures.OUT_STARTLE)] > 0f)
        assertTrue(core.diagnostics().extra.getValue("banc.leg_nociception") > 0f)
    }

    @Test fun visualAdapterReachesDngAndWingPowerAndResetIsDeterministic() {
        val x = input(NeuralFeatures.VISUAL_MOTION to 1f, NeuralFeatures.FACE_OFFSET_X to .6f)
        val first = BancExperimentalCore().also { repeat(8) { _ -> it.step(x, 30f) } }.readout()
        val secondCore = BancExperimentalCore().also { repeat(8) { _ -> it.step(x, 30f) } }
        val second = secondCore.readout()
        assertEquals(first.toList(), second.toList())
        assertTrue(second[NeuralFeatures.OUTPUT.index(NeuralFeatures.OUT_INSPECT)] > 0f)
        assertTrue(secondCore.diagnostics().extra.getValue("banc.wing_power") > 0f)
        secondCore.reset()
        assertEquals(0f, secondCore.diagnostics().extra.getValue("banc.wing_power"), 0f)
    }
}
