package com.example.meowmba.neural

import com.example.meowmba.core.behavior.Stimulus
import com.example.meowmba.core.neural.*
import org.junit.Assert.*
import org.junit.Test

class NeuralTraceGateTest {
    @Test fun disabledRecorderReceivesNoSnapshotsAndTogglingItPreservesControllerOutput() {
        for (substrate in listOf(NeuralSubstrate.RATE, NeuralSubstrate.SPIKING, NeuralSubstrate.BANC_EXPERIMENTAL)) {
            for (mode in listOf(NeuralMode.SHADOW, NeuralMode.NEURAL)) {
                var enabled = false
                val recorded = mutableListOf<FeatureTraceSample>()
                val reference = mutableListOf<FeatureTraceSample>()
                val gated = NeuralBehaviorController(initialMode = mode, initialSubstrate = substrate,
                    telemetry = NeuralTelemetry { _, _ -> }, monotonicNanos = { 0L },
                    traceSink = { recorded += it }, traceEnabled = { enabled })
                val alwaysRecorded = NeuralBehaviorController(initialMode = mode, initialSubstrate = substrate,
                    telemetry = NeuralTelemetry { _, _ -> }, monotonicNanos = { 0L },
                    traceSink = { reference += it })
                repeat(30) { step ->
                    enabled = step in 10..19
                    if (step % 3 == 0) {
                        gated.onStimulus(Stimulus.TOUCH, .7f)
                        alwaysRecorded.onStimulus(Stimulus.TOUCH, .7f)
                    }
                    gated.tick(200)
                    alwaysRecorded.tick(200)
                    assertEquals(alwaysRecorded.bias(), gated.bias())
                    assertEquals(alwaysRecorded.currentAction(), gated.currentAction())
                    assertArrayEquals(alwaysRecorded.lastReadout(), gated.lastReadout(), 0f)
                    assertEquals((step - 9).coerceIn(0, 10), recorded.size)
                }
                // Compare after further ticks: recorded arrays must remain detached snapshots.
                for ((index, actual) in recorded.withIndex()) {
                    val expected = reference[index + 10]
                    assertEquals(expected.tMs, actual.tMs)
                    assertEquals(expected.dtMs, actual.dtMs)
                    assertEquals(expected.stepIndex, actual.stepIndex)
                    assertEquals(expected.resetGeneration, actual.resetGeneration)
                    assertEquals(expected.mode, actual.mode)
                    assertArrayEquals(expected.input, actual.input, 0f)
                    assertArrayEquals(expected.legacyBias, actual.legacyBias, 0f)
                    assertArrayEquals(expected.neuralReadout, actual.neuralReadout, 0f)
                    assertArrayEquals(expected.neuralBias, actual.neuralBias, 0f)
                    assertArrayEquals(expected.legacyDrives, actual.legacyDrives, 0f)
                }
            }
        }
    }

    @Test fun absentTraceSinkDoesNotConsultTheRecordingGate() {
        val controller = NeuralBehaviorController(
            telemetry = NeuralTelemetry { _, _ -> }, monotonicNanos = { 0L },
            traceEnabled = { error("No trace consumer exists") },
        )
        controller.tick(200)
        assertEquals(1L, controller.diagnostics().steps)
    }
}
