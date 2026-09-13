package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.neural.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Same scripted inputs on the real handset; cost/reproducibility, not intelligence quality. */
class NeuralPhoneReplayTest {
    @Test fun compareRealDeviceCoreLatencyAndReplay() {
        val rows = mutableListOf<String>()
        for (kind in NeuralSubstrate.values()) {
            fun core(): NeuralCore = if (kind == NeuralSubstrate.RATE) LeakyRecurrentCore(NeuralCheckpoint.default()) else SpikingCore(NeuralCheckpoint.defaultSpiking())
            val model = core()
            val input = List(400) { i -> FloatArray(model.inputLayout.size).also { x ->
                x[model.inputLayout.index("face_presence")] = if (i in 40..119) 1f else 0f
                x[model.inputLayout.index("touch")] = if (i % 61 == 0) 1f else 0f
                x[model.inputLayout.index("threat")] = if (i == 250) 1f else 0f
            } }
            val first = input.map { model.step(it, 200f); model.readout() }
            model.reset()
            input.forEachIndexed { i, x -> model.step(x, 200f); assertArrayEquals(first[i], model.readout(), 0f) }
            val times = mutableListOf<Long>()
            repeat(5) {
                model.reset()
                for (x in input) {
                    val start = System.nanoTime(); model.step(x, 200f); val y = model.readout()
                    times += System.nanoTime() - start
                    assertTrue(y.all { it.isFinite() })
                }
            }
            times.sort()
            val p95 = times[(times.size * .95).toInt()] / 1000.0
            rows += """{"substrate":"$kind","samples":${times.size},"p50Micros":${times[times.size/2]/1000.0},"p95Micros":$p95,"maxMicros":${times.last()/1000.0},"p95Under20ms":${p95 < 20000},"reproducible":true}"""
        }
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "neural-phone-comparison.json")
        output.writeText("""{"scope":"Real phone synthetic replay; no biological weights or learned quality proof","models":[${rows.joinToString(",") }]}""")
    }
}
