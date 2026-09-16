package com.example.meowmba.neural

import com.example.meowmba.core.neural.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Same held-out scripted feature sequence for both shipping substrates. This
 * measures reproducibility/cost/divergence, not learned task success or phone heat.
 */
class NeuralComparisonReportTest {
    private fun replay(core: NeuralCore): List<FloatArray> = (0 until 400).map { i ->
        val x = FloatArray(core.inputLayout.size)
        fun set(name: String, value: Float) { x[core.inputLayout.index(name)] = value }
        if (i in 40..119 && i % 17 != 0) { set("face_presence", 1f); set("face_confidence", .9f) }
        if (i in 120..199) set("visual_motion", .7f)
        if (i in 200..239) set("voice_activity", .8f)
        if (i == 250) set("threat", 1f)
        if (i == 280) set("touch", 1f)
        if (i == 300) set("praise", 1f)
        core.step(x, 50f)
        core.readout()
    }
    @Test fun sameReplayProducesMeasuredComparison() {
        val rows = mutableListOf<String>()
        val sequences = mutableListOf<List<FloatArray>>()
        for (kind in listOf(NeuralSubstrate.RATE, NeuralSubstrate.SPIKING)) {
            fun make(): NeuralCore = if (kind == NeuralSubstrate.RATE) LeakyRecurrentCore(NeuralCheckpoint.default())
                else SpikingCore(NeuralCheckpoint.defaultSpiking())
            repeat(2) { replay(make()) }
            val durations = mutableListOf<Long>()
            val core = make()
            val first = replay(core)
            core.reset()
            val second = replay(core)
            first.zip(second).forEach { (a,b) -> assertArrayEquals(a,b,0f) }
            repeat(5) {
                val timed = make()
                val start = System.nanoTime()
                replay(timed)
                durations += System.nanoTime() - start
            }
            val ckpt = if (kind == NeuralSubstrate.RATE) NeuralCheckpoint.default() else NeuralCheckpoint.defaultSpiking()
            for (y in first) for (o in y.indices) assertTrue(y[o].isFinite() && y[o] in ckpt.outputMin[o]..ckpt.outputMax[o])
            val diagnostics = core.diagnostics()
            assertEquals(0L, diagnostics.nanResets)
            val sorted = durations.sorted()
            val median = sorted[2] / 400.0 / 1000.0
            val slowest = sorted.last() / 400.0 / 1000.0
            rows += """{"substrate":"$kind","units":${diagnostics.units},"ticks":400,"dtMs":50,"repeats":5,"medianRunMicrosPerTick":$median,"slowestRunMicrosPerTick":$slowest,"deterministic":true,"finiteBounded":true,"nanResets":${diagnostics.nanResets}}"""
            sequences += first
        }
        var difference = 0.0
        var count = 0
        sequences[0].zip(sequences[1]).forEach { (a,b) -> a.indices.forEach { difference += abs(a[it]-b[it]); count++ } }
        val report = """{"scope":"JVM synthetic held-out replay; untrained shipping cores; no biological circuit; not phone timing or quality preference","runtime":"${System.getProperty("java.vm.name")}","models":[${rows.joinToString(",")}],"rateVsSpikingReadoutMAE":${difference/count}}"""
        File("build/reports/neural").mkdirs()
        File("build/reports/neural/comparison.json").writeText(report + "\n")
        println("NEURAL_COMPARISON $report")
    }
}
