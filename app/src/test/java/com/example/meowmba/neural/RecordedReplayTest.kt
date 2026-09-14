package com.example.meowmba.neural

import com.example.meowmba.core.behavior.Stimulus
import com.example.meowmba.core.neural.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class RecordedReplayTest {
    @Test fun recordedIntervalsReproduceLocalCoresAcrossThermalCadenceAndReset() {
        // Remote replies do not contain a locally replayable neural checkpoint.
        for (substrate in listOf(NeuralSubstrate.RATE, NeuralSubstrate.SPIKING, NeuralSubstrate.BANC_EXPERIMENTAL)) {
            var tier = 0
            val samples = mutableListOf<FeatureTraceSample>()
            val controller = NeuralBehaviorController(initialSubstrate = substrate,
                thermalTier = { tier }, telemetry = NeuralTelemetry { _, _ -> },
                monotonicNanos = { 0L }, traceSink = { samples += it })
            // Start after a previous session and an odd economy tick, as real recording does.
            tier = 1; controller.tick(173); controller.resetNeuralState(); samples.clear()
            repeat(120) { i ->
                tier = when (i) { in 15..39 -> 1; in 40..49 -> 2; else -> 0 }
                if (i % 9 == 0) controller.onStimulus(Stimulus.TOUCH, .7f)
                if (i % 17 == 0) controller.onStimulus(Stimulus.LOUD_SOUND, .4f)
                controller.tick(listOf(50L, 173L, 200L, 250L)[i % 4])
            }
            val core: NeuralCore = when (substrate) {
                NeuralSubstrate.RATE -> LeakyRecurrentCore(NeuralCheckpoint.default())
                NeuralSubstrate.SPIKING -> SpikingCore(NeuralCheckpoint.defaultSpiking())
                NeuralSubstrate.BANC_EXPERIMENTAL -> BancExperimentalCore()
                NeuralSubstrate.MALECNS_REMOTE -> error("Remote state requires the server replay harness")
            }
            assertTrue(samples.isNotEmpty())
            for ((index, sample) in samples.withIndex()) {
                assertEquals(index + 1L, sample.stepIndex)
                assertEquals(0L, sample.resetGeneration)
                core.step(sample.input, requireNotNull(sample.dtMs))
                assertArrayEquals(sample.neuralReadout, core.readout(), 0f)
            }
        }
    }

    @Test fun rotationMarksContinuationAndNeverOverwritesSameMillisecondFiles() {
        val dir = Files.createTempDirectory("trace-test").toFile()
        try {
            val header = FeatureTraceRecorder.headerFor(NeuralCheckpoint.default()).copy(startsFromReset = true)
            val sample = FeatureTraceSample(200, "SHADOW", FloatArray(40), FloatArray(8), FloatArray(12), legacyDrives = FloatArray(5), dtMs = 200f)
            FeatureTraceRecorder(dir, header, maxBytesPerFile = 2200, maxFiles = 50, flushEvery = 1).use { writer ->
                repeat(30) { writer.record(sample.copy(tMs = (it + 1L) * 200L)) }
                assertEquals(30L, writer.samplesWritten)
            }
            val files = dir.listFiles()!!.toList()
            assertTrue(files.size > 1)
            val records = files.map { FeatureTraceRecorder.read(it) }
            assertEquals(1, records.count { it.first.startsFromReset })
            assertEquals(30, records.sumOf { it.second.size })
            assertTrue(files.all { it.length() <= 2200 })
        } finally { dir.deleteRecursively() }
    }

    @Test fun oversizedSampleIsDroppedWithoutRecursionOrFileFlood() {
        val dir = Files.createTempDirectory("trace-oversize").toFile()
        try {
            FeatureTraceRecorder(dir, FeatureTraceRecorder.headerFor(NeuralCheckpoint.default()), maxBytesPerFile = 32).use {
                it.record(FeatureTraceSample(1, "SHADOW", floatArrayOf(1f), floatArrayOf(), floatArrayOf(), legacyDrives = floatArrayOf(), dtMs = 1f))
                assertEquals(1L, it.dropped)
                assertEquals(0L, it.samplesWritten)
                assertTrue(it.files().isEmpty())
            }
        } finally { dir.deleteRecursively() }
    }
}
