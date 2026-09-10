package com.example.meowmba.neural

import com.example.meowmba.core.behavior.NervousSystem
import com.example.meowmba.core.behavior.NervousSystem.Companion.REST_BIAS
import com.example.meowmba.core.behavior.Stimulus
import com.example.meowmba.core.neural.BehaviorDecoder
import com.example.meowmba.core.neural.DefaultRateCheckpoint
import com.example.meowmba.core.neural.DriveBias
import com.example.meowmba.core.neural.LeakyRecurrentCore
import com.example.meowmba.core.neural.NeuralCheckpoint
import com.example.meowmba.core.neural.NeuralCore
import com.example.meowmba.core.neural.NeuralFeatures
import com.example.meowmba.core.neural.NeuralSubstrate
import com.example.meowmba.core.neural.SpikingCore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Helpers shared by the core tests. */
internal object CoreTestSupport {
    val input = NeuralFeatures.INPUT
    val output = NeuralFeatures.OUTPUT

    fun zeros() = FloatArray(input.size)

    fun oneHot(name: String, value: Float = 1f): FloatArray = zeros().also { it[input.index(name)] = value }

    fun out(y: FloatArray, name: String): Float = y[output.index(name)]

    /** Run [core] with [x] held constant for [totalMs] in steps of [stepMs]. */
    fun run(core: NeuralCore, x: FloatArray, totalMs: Float, stepMs: Float = 50f) {
        var t = 0f
        while (t < totalMs - 1e-3f) {
            val dt = minOf(stepMs, totalMs - t)
            core.step(x, dt)
            t += dt
        }
    }

    /** Feed the encoder-shaped decaying impulse for [name] and return the peak of [outName]. */
    fun impulsePeak(core: NeuralCore, name: String, magnitude: Float, outName: String, stepMs: Float = 20f, horizonMs: Float = 6_000f): Float {
        var peak = Float.NEGATIVE_INFINITY
        var t = 0f
        var level = magnitude
        val decay = kotlin.math.exp(-stepMs / DefaultRateCheckpoint.IMPULSE_TAU_MS)
        while (t < horizonMs) {
            core.step(oneHot(name, level), stepMs)
            val v = out(core.readout(), outName)
            if (v > peak) peak = v
            level *= decay
            t += stepMs
        }
        return peak
    }

    fun assertFinite(y: FloatArray) {
        assertTrue("readout has non-finite entries: ${y.toList()}", y.all { it.isFinite() })
    }
}

class LeakyRecurrentCoreTest {
    private val s = CoreTestSupport

    @Test
    fun restingReadoutDecodesToLegacyRestBias() {
        val core = LeakyRecurrentCore(NeuralCheckpoint.default())
        s.run(core, s.zeros(), 30_000f)
        val decoded = BehaviorDecoder().decode(core.readout())
        assertEquals(0f, DriveBias.divergence(REST_BIAS, decoded.bias), 1e-3f)
        assertEquals(REST_BIAS.glanceGain, decoded.bias.glanceGain, 1e-4f)
        assertEquals(REST_BIAS.valence, decoded.bias.valence, 1e-4f)
    }

    @Test
    fun driveBiasFormulaMatchesLegacyControllerAtRest() {
        val legacy = NervousSystem()
        assertEquals(0f, DriveBias.divergence(legacy.bias(), DriveBias.compute(legacy.snapshot(), 0f)), 1e-6f)
    }

    @Test
    fun driveBiasFormulaMatchesLegacyControllerAfterStimuli() {
        // The legacy controller's bias is a function of its drives plus its valence
        // impulse; with a zero valence impulse the shared formula must reproduce it.
        val legacy = NervousSystem()
        legacy.onStimulus(Stimulus.LOUD_SOUND)
        legacy.onStimulus(Stimulus.FACE_SEEN)
        legacy.tick(400L)
        val recomputed = DriveBias.compute(legacy.snapshot(), 0f)
        assertEquals(0f, DriveBias.divergence(legacy.bias(), recomputed), 1e-5f)
    }

    @Test
    fun coarseAndFineStepsAgree() {
        val coarse = LeakyRecurrentCore(NeuralCheckpoint.default())
        val fine = LeakyRecurrentCore(NeuralCheckpoint.default())
        val x = s.oneHot(NeuralFeatures.FACE_PRESENCE).also { it[s.input.index(NeuralFeatures.VOICE_ACTIVITY)] = 0.7f }
        s.run(coarse, x, 2_000f, stepMs = 200f)
        s.run(fine, x, 2_000f, stepMs = 10f)
        val a = coarse.readout()
        val b = fine.readout()
        for (i in a.indices) assertEquals("channel ${s.output.names[i]}", a[i], b[i], 0.02f)
        for (i in 0 until coarse.units) assertEquals("unit $i", coarse.activity(i), fine.activity(i), 0.03f)
    }

    @Test
    fun stateStaysBoundedUnderSaturatingInput() {
        val core = LeakyRecurrentCore(NeuralCheckpoint.default())
        val x = FloatArray(s.input.size) { 1f }
        s.run(core, x, 60_000f, stepMs = 200f)
        for (i in 0 until core.units) assertTrue(abs(core.activity(i)) <= 1f)
        val y = core.readout()
        s.assertFinite(y)
        for (o in y.indices) assertTrue(y[o] in -1f..1f)
        assertEquals(0L, core.diagnostics().nanResets)
    }

    @Test
    fun nonFiniteInputDoesNotPoisonTheState() {
        val core = LeakyRecurrentCore(NeuralCheckpoint.default())
        val x = s.zeros().also { it[0] = Float.NaN }
        core.step(x, 200f)
        // A NaN went in; the guard resets rather than propagating.
        s.assertFinite(core.readout())
        assertEquals(1L, core.diagnostics().nanResets)
        s.run(core, s.oneHot(NeuralFeatures.FACE_PRESENCE), 1_000f)
        s.assertFinite(core.readout())
    }

    @Test
    fun impulsePeaksTrackTheLegacyJumps() {
        // The encoder turns a legacy stimulus into a decaying impulse; the trace
        // units are calibrated so the neural drive peaks where legacy jumps.
        val legacyTouchSocial = 0.25f
        val core = LeakyRecurrentCore(NeuralCheckpoint.default())
        val peak = s.impulsePeak(core, NeuralFeatures.TOUCH, 1f, NeuralFeatures.OUT_SOCIAL)
        val rest = DefaultRateCheckpoint.outputBaseline(s.output)[s.output.index(NeuralFeatures.OUT_SOCIAL)]
        assertEquals(legacyTouchSocial, peak - rest, 0.04f)

        val core2 = LeakyRecurrentCore(NeuralCheckpoint.default())
        val cautionPeak = s.impulsePeak(core2, NeuralFeatures.LOUD_SOUND, 1f, NeuralFeatures.OUT_CAUTION)
        val cautionRest = DefaultRateCheckpoint.outputBaseline(s.output)[s.output.index(NeuralFeatures.OUT_CAUTION)]
        assertEquals(0.45f, cautionPeak - cautionRest, 0.07f)
    }

    @Test
    fun attentionCompetitionPicksOneWinner() {
        val core = LeakyRecurrentCore(NeuralCheckpoint.default())
        val x = s.oneHot(NeuralFeatures.FACE_PRESENCE).also {
            it[s.input.index(NeuralFeatures.FACE_CONFIDENCE)] = 0.9f
            it[s.input.index(NeuralFeatures.VISUAL_MOTION)] = 0.5f
        }
        s.run(core, x, 3_000f)
        val y = core.readout()
        val user = s.out(y, NeuralFeatures.OUT_ATTEND_USER)
        val motion = s.out(y, NeuralFeatures.OUT_ATTEND_MOTION)
        assertTrue("attention should be active: user=$user motion=$motion", user > 0.01f)
        assertTrue("attention winner should be deterministic: user=$user motion=$motion", user >= motion * 0.8f)
    }

    @Test
    fun recurrenceHoldsAttentionThroughDetectorFlicker() {
        // Ablation: the same network with the attention block's self-excitation
        // and lateral inhibition removed. Both see a face, then a 600 ms gap.
        fun holdAfterGap(ckpt: NeuralCheckpoint): Float {
            val core = LeakyRecurrentCore(ckpt)
            val face = s.oneHot(NeuralFeatures.FACE_PRESENCE).also { it[s.input.index(NeuralFeatures.FACE_CONFIDENCE)] = 0.9f }
            s.run(core, face, 4_000f)
            s.run(core, s.zeros(), 600f, stepMs = 50f)
            return s.out(core.readout(), NeuralFeatures.OUT_ATTEND_USER)
        }
        val full = holdAfterGap(NeuralCheckpoint.default())
        val ablated = holdAfterGap(withoutAttentionRecurrence(NeuralCheckpoint.default()))
        assertTrue("full network should still be attending after the gap (got $full)", full > 0.6f)
        assertTrue("ablated network should have lost attention (got $ablated)", ablated < full - 0.2f)
    }

    @Test
    fun adaptationHabituatesToSustainedAttention() {
        val core = LeakyRecurrentCore(NeuralCheckpoint.default())
        val face = s.oneHot(NeuralFeatures.FACE_PRESENCE).also { it[s.input.index(NeuralFeatures.FACE_CONFIDENCE)] = 0.9f }
        s.run(core, face, 1_500f)
        val early = core.activity(DefaultRateCheckpoint.ATTEND_USER)
        s.run(core, face, 30_000f)
        val late = core.activity(DefaultRateCheckpoint.ATTEND_USER)
        assertTrue("attention adaptation should be active: early=$early late=$late", core.adaptation(DefaultRateCheckpoint.ATTEND_USER) > 0.1f)
        assertTrue(core.adaptation(DefaultRateCheckpoint.ATTEND_USER) > 0.1f)
    }

    @Test
    fun intentApproachIsFollowed() {
        val core = LeakyRecurrentCore(NeuralCheckpoint.default())
        val x = s.zeros().also {
            it[s.input.index(NeuralFeatures.INTENT_APPROACH)] = 0.65f
            it[s.input.index(NeuralFeatures.INTENT_APPROACH_SET)] = 1f
            it[s.input.index(NeuralFeatures.INTENT_USER)] = 1f
        }
        s.run(core, x, 20_000f)
        assertEquals(0.65f, s.out(core.readout(), NeuralFeatures.OUT_APPROACH), 0.03f)
        s.run(core, s.zeros(), 30_000f)
        assertEquals(0.3f, s.out(core.readout(), NeuralFeatures.OUT_APPROACH), 0.03f)
    }

    @Test
    fun sustainedExcitementDrainsEnergy() {
        val core = LeakyRecurrentCore(NeuralCheckpoint.default())
        val restEnergy = s.out(core.readout(), NeuralFeatures.OUT_ENERGY)
        val busy = s.oneHot(NeuralFeatures.FACE_PRESENCE).also {
            it[s.input.index(NeuralFeatures.VISUAL_MOTION)] = 1f
            it[s.input.index(NeuralFeatures.NOVELTY)] = 0.8f
            it[s.input.index(NeuralFeatures.LOUD_SOUND)] = 0.5f
        }
        s.run(core, busy, 90_000f, stepMs = 200f)
        assertTrue(s.out(core.readout(), NeuralFeatures.OUT_ENERGY) < restEnergy - 0.05f)
    }

    @Test
    fun resetReturnsToBaseline() {
        val core = LeakyRecurrentCore(NeuralCheckpoint.default())
        s.run(core, s.oneHot(NeuralFeatures.THREAT), 1_000f)
        assertNotEquals(0f, core.activity(DefaultRateCheckpoint.TRACE_CAUTION), 1e-4f)
        core.reset()
        assertArrayEquals(DefaultRateCheckpoint.outputBaseline(s.output).toFloatArray(), core.readout(), 1e-6f)
    }

    @Test
    fun diagnosticsReportPopulations() {
        val core = LeakyRecurrentCore(NeuralCheckpoint.default())
        s.run(core, s.oneHot(NeuralFeatures.FACE_PRESENCE), 1_000f)
        val d = core.diagnostics()
        assertEquals(NeuralSubstrate.RATE, d.substrate)
        assertEquals(DefaultRateCheckpoint.UNITS, d.units)
        assertTrue(d.extra.containsKey("pop.attention"))
        assertTrue(d.substeps >= 1)
    }

    @Test
    fun stepCostFitsThePhoneBudget() {
        val core = LeakyRecurrentCore(NeuralCheckpoint.default())
        val x = s.oneHot(NeuralFeatures.FACE_PRESENCE)
        repeat(50) { core.step(x, 200f) }
        val t0 = System.nanoTime()
        repeat(200) { core.step(x, 200f); core.readout() }
        val perStepMicros = (System.nanoTime() - t0) / 200 / 1_000
        assertTrue("rate core step took $perStepMicros us on the host", perStepMicros < 5_000)
    }

    companion object {
        /** Zero the recurrent weights inside the attention block only; relays and readouts stay. */
        fun withoutAttentionRecurrence(ckpt: NeuralCheckpoint): NeuralCheckpoint {
            val r = ckpt.rate!!
            val n = r.units
            val rec = r.recurrentWeights.toMutableList()
            val block = DefaultRateCheckpoint.ATTEND_USER..DefaultRateCheckpoint.ATTEND_SPARE
            for (i in block) for (j in block) rec[i * n + j] = 0f
            return ckpt.copy(name = ckpt.name + "-no-attention-recurrence", rate = r.copy(recurrentWeights = rec))
        }
    }
}

class NeuralCheckpointTest {

    @Test
    fun defaultCheckpointIsValidAndRoundTrips() {
        val ckpt = NeuralCheckpoint.default()
        assertTrue(ckpt.validate().isEmpty())
        val json = ckpt.toJson()
        val back = NeuralCheckpoint.fromJson(json)
        assertEquals(ckpt, back)
        assertEquals(NeuralFeatures.INPUT.names, back.inputLayout)
        assertEquals(NeuralFeatures.OUTPUT.names, back.outputLayout)
    }

    @Test
    fun defaultSpikingCheckpointIsValid() {
        assertTrue(NeuralCheckpoint.defaultSpiking().validate().isEmpty())
    }

    @Test
    fun shapeMismatchIsRejected() {
        val ckpt = NeuralCheckpoint.default()
        val bad = ckpt.copy(rate = ckpt.rate!!.copy(inputWeights = ckpt.rate!!.inputWeights.drop(1)))
        assertTrue(bad.validate().any { it.contains("inputWeights") })
    }

    @Test
    fun nonFiniteWeightsAreRejected() {
        val ckpt = NeuralCheckpoint.default()
        val weights = ckpt.rate!!.recurrentWeights.toMutableList().also { it[5] = Float.NaN }
        val bad = ckpt.copy(rate = ckpt.rate!!.copy(recurrentWeights = weights))
        assertTrue(bad.validate().any { it.contains("non-finite") })
    }

    @Test
    fun outOfBoundWeightsAreRejected() {
        val ckpt = NeuralCheckpoint.default()
        val weights = ckpt.rate!!.recurrentWeights.toMutableList().also { it[0] = 40f }
        val bad = ckpt.copy(rate = ckpt.rate!!.copy(recurrentWeights = weights))
        assertTrue(bad.validate().any { it.contains("beyond") })
    }

    @Test
    fun baselineOutsideBoundsIsRejected() {
        val ckpt = NeuralCheckpoint.default()
        val bad = ckpt.copy(outputBaseline = ckpt.outputBaseline.toMutableList().also { it[0] = 2f })
        assertTrue(bad.validate().any { it.contains("baseline") })
    }

    @Test
    fun wrongVersionIsRejected() {
        assertTrue(NeuralCheckpoint.default().copy(version = 99).validate().any { it.contains("version") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun garbageJsonIsRejected() {
        NeuralCheckpoint.fromJson("{not json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun tauOutOfRangeIsRejectedOnLoad() {
        val ckpt = NeuralCheckpoint.default()
        val bad = ckpt.copy(rate = ckpt.rate!!.copy(tauMs = ckpt.rate!!.tauMs.map { 1f }))
        LeakyRecurrentCore(bad)
    }

    @Test
    fun exportedDefaultFixtureHasNotDrifted() {
        // scripts/neural/default_core.json is what the offline trainer starts from.
        // Regenerate it (see docs/NEURAL_LAYER_2026-09-10.md) when the default wiring changes.
        val fixture = File("../scripts/neural/default_core.json")
        val fresh = NeuralCheckpoint.default()
        val exportDir = File("build/neural").also { it.mkdirs() }
        File(exportDir, "default_core.json").writeText(fresh.toJson())
        File(exportDir, "default_spiking.json").writeText(NeuralCheckpoint.defaultSpiking().toJson())
        if (!fixture.isFile) return
        val pinned = NeuralCheckpoint.fromJson(fixture.readText())
        assertEquals("default checkpoint drifted from scripts/neural/default_core.json; re-export it", pinned, fresh)
    }
}

/** The behaviours every substrate must share, run against both cores. */
class NeuralCoreContractTest {
    private val s = CoreTestSupport

    private fun cores(): List<NeuralCore> = listOf(
        LeakyRecurrentCore(NeuralCheckpoint.default()),
        SpikingCore(NeuralCheckpoint.defaultSpiking()),
    )

    @Test
    fun layoutsMatchTheSharedFeatureSets() {
        for (core in cores()) {
            assertEquals(core.substrate.name, NeuralFeatures.INPUT, core.inputLayout)
            assertEquals(core.substrate.name, NeuralFeatures.OUTPUT, core.outputLayout)
        }
    }

    @Test
    fun restingReadoutIsTheBaseline() {
        for (core in cores()) {
            s.run(core, s.zeros(), 5_000f)
            assertArrayEquals(core.substrate.name, DefaultRateCheckpoint.outputBaseline(s.output).toFloatArray(), core.readout(), 1e-5f)
        }
    }

    @Test
    fun readoutIsFiniteAndBoundedUnderMaxInput() {
        for (core in cores()) {
            s.run(core, FloatArray(s.input.size) { 1f }, 10_000f, stepMs = 200f)
            val y = core.readout()
            s.assertFinite(y)
            for (o in y.indices) assertTrue("${core.substrate} ${s.output.names[o]}=${y[o]}", y[o] in -1f..1f)
        }
    }

    @Test
    fun sameInputSequenceGivesSameOutput() {
        for (make in listOf<() -> NeuralCore>(
            { LeakyRecurrentCore(NeuralCheckpoint.default()) },
            { SpikingCore(NeuralCheckpoint.defaultSpiking()) },
        )) {
            val a = make()
            val b = make()
            val seq = listOf(
                s.oneHot(NeuralFeatures.FACE_PRESENCE), s.oneHot(NeuralFeatures.LOUD_SOUND, 0.8f),
                s.zeros(), s.oneHot(NeuralFeatures.VISUAL_MOTION, 0.6f),
            )
            for (x in seq) { a.step(x, 200f); b.step(x, 200f) }
            assertArrayEquals(a.substrate.name, a.readout(), b.readout(), 0f)
        }
    }

    @Test
    fun aFaceRaisesUserAttentionAndAThreatRaisesCaution() {
        for (core in cores()) {
            s.run(core, s.oneHot(NeuralFeatures.FACE_PRESENCE), 3_000f)
            val attend = s.out(core.readout(), NeuralFeatures.OUT_ATTEND_USER)
            assertTrue("${core.substrate} attend_user=$attend", attend > 0.2f)
            core.reset()
            val cautionRest = s.out(core.readout(), NeuralFeatures.OUT_CAUTION)
            s.run(core, s.oneHot(NeuralFeatures.THREAT), 1_000f)
            val caution = s.out(core.readout(), NeuralFeatures.OUT_CAUTION)
            assertTrue("${core.substrate} caution=$caution rest=$cautionRest", caution > cautionRest + 0.1f)
        }
    }

    @Test
    fun resetForgetsEverything() {
        for (core in cores()) {
            s.run(core, FloatArray(s.input.size) { 1f }, 2_000f)
            core.reset()
            assertArrayEquals(core.substrate.name, DefaultRateCheckpoint.outputBaseline(s.output).toFloatArray(), core.readout(), 1e-6f)
            assertEquals(0L, core.diagnostics().nanResets)
        }
    }
}

class SpikingCoreTest {
    private val s = CoreTestSupport

    @Test
    fun refractoryPeriodIsRespected() {
        val ckpt = NeuralCheckpoint.defaultSpiking()
        val core = SpikingCore(ckpt)
        s.run(core, FloatArray(s.input.size) { 1f }, 3_000f, stepMs = 100f)
        val minIsi = core.diagnostics().extra.getValue("isi.min_ms")
        assertTrue("min ISI $minIsi must not undercut the refractory period", minIsi >= ckpt.spiking!!.refractoryMs - 1e-3f)
    }

    @Test
    fun noRunawayAtMaximumInput() {
        val core = SpikingCore(NeuralCheckpoint.defaultSpiking())
        s.run(core, FloatArray(s.input.size) { 1f }, 5_000f, stepMs = 200f)
        val d = core.diagnostics()
        assertTrue(d.maxAbsActivity <= 1.5f)
        assertEquals(0L, d.nanResets)
        assertTrue("some neurons should be firing", d.extra.getValue("spikes.last") > 0f)
    }

    @Test
    fun silentAtRest() {
        val core = SpikingCore(NeuralCheckpoint.defaultSpiking())
        s.run(core, s.zeros(), 3_000f)
        assertEquals(0f, core.diagnostics().extra.getValue("spikes.total"), 0f)
        assertEquals(core.neurons, core.diagnostics().silentUnits)
    }

    @Test
    fun signedInputsDriveTheMatchingPopulation() {
        val core = SpikingCore(NeuralCheckpoint.defaultSpiking())
        s.run(core, s.oneHot(NeuralFeatures.FACE_OFFSET_X, 1f), 1_500f)
        val right = s.out(core.readout(), NeuralFeatures.OUT_ORIENT_X)
        core.reset()
        s.run(core, s.oneHot(NeuralFeatures.FACE_OFFSET_X, -1f), 1_500f)
        val left = s.out(core.readout(), NeuralFeatures.OUT_ORIENT_X)
        assertTrue("right=$right", right > 0.05f)
        assertTrue("left=$left", left < -0.05f)
    }

    @Test
    fun differentSeedsWireDifferently() {
        val a = SpikingCore(NeuralCheckpoint.defaultSpiking())
        val ckptB = NeuralCheckpoint.defaultSpiking().let { it.copy(spiking = it.spiking!!.copy(seed = 99L)) }
        val b = SpikingCore(ckptB)
        val x = FloatArray(s.input.size) { 0.6f }
        s.run(a, x, 1_000f)
        s.run(b, x, 1_000f)
        assertFalse(a.readout().contentEquals(b.readout()))
    }
}
