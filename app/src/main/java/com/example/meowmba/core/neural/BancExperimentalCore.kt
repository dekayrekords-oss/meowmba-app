package com.example.meowmba.core.neural

import kotlin.math.exp
import kotlin.math.max

/**
 * Experimental, deterministic BANC anatomical-path adapter.
 *
 * The group-to-group count table is copied from the audited 76-neuron / 515-pair
 * BANC v888 Figure 3h extract (CC BY 4.0), recorded in
 * docs/neural/BANC_IDENTIFIED_CIRCUIT_2026-09-13.json. Counts determine only
 * the relative positive influence used here; their signs, conductances, delays,
 * external inputs and muscle mapping were not supplied by the dataset. Those
 * terms below are deliberately bounded authored assumptions, not biology.
 *
 * The two defensible sensor paths are touch -> leg nociceptors and alarm ->
 * thorax nociceptors. A visual-novelty adapter also enters ANXXX139, whose
 * functional role is not inferred from its name. DNg27's documented 265-count
 * projection to wing power motor neurons produces a measured `wing_power`
 * diagnostic and changes the live motor/attention readout. It does not claim
 * to reconstruct flight biomechanics.
 */
class BancExperimentalCore : NeuralCore {
    override val substrate = NeuralSubstrate.BANC_EXPERIMENTAL
    override val inputLayout = NeuralFeatures.INPUT
    override val outputLayout = NeuralFeatures.OUTPUT

    private val state = FloatArray(GROUPS.size)
    private val output = FloatArray(outputLayout.size)
    private var resets = 0L
    private var lastSubsteps = 0
    private var lastVisualX = 0f
    private var lastVisualY = 0f

    override fun step(input: FloatArray, dtMs: Float) {
        require(input.size == inputLayout.size) { "BANC input layout mismatch" }
        if (!dtMs.isFinite() || dtMs <= 0f) return
        val dt = dtMs.coerceAtMost(100f)
        val substeps = ((dt / 10f).toInt() + 1).coerceIn(1, 10)
        lastSubsteps = substeps
        val h = dt / substeps
        fun feature(name: String): Float = input[inputLayout.index(name)].takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
        // Sensed inputs are sampled once; the deterministic integrator carries them.
        val touch = feature(NeuralFeatures.TOUCH).coerceAtLeast(0f)
        val alarm = max(feature(NeuralFeatures.THREAT), feature(NeuralFeatures.LOUD_SOUND)).coerceAtLeast(0f)
        val novelty = max(feature(NeuralFeatures.NOVELTY), feature(NeuralFeatures.VISUAL_MOTION)).coerceAtLeast(0f)
        lastVisualX = feature(NeuralFeatures.FACE_OFFSET_X)
        lastVisualY = feature(NeuralFeatures.FACE_OFFSET_Y)
        repeat(substeps) {
            val next = FloatArray(GROUPS.size)
            for (post in GROUPS.indices) {
                var recurrent = 0f
                for (pre in GROUPS.indices) recurrent += state[pre] * NORMALIZED_COUNTS[pre][post]
                val sensory = when (post) {
                    LEG_NOCICEPTOR -> touch * .95f
                    THORAX_NOCICEPTOR -> alarm * .95f
                    ANXXX139 -> novelty * .55f
                    else -> 0f
                }
                // Synthetic leak/gain: bounds an otherwise unlabelled anatomical count graph.
                val target = (sensory + recurrent * .72f).coerceIn(0f, 1.15f)
                val blend = 1f - exp(-h / TAU_MS[post])
                next[post] = (state[post] + (target - state[post]) * blend).coerceIn(0f, 1f)
            }
            next.copyInto(state)
        }
        writeOutput()
    }

    private fun writeOutput() {
        val an = state[ANXXX139]
        val dng = state[DNG27]
        val nsc = state[NSC]
        val danger = max(state[LEG_NOCICEPTOR], state[THORAX_NOCICEPTOR])
        val wing = state[WING_POWER]
        fun put(name: String, value: Float, lo: Float = 0f, hi: Float = 1f) {
            output[outputLayout.index(name)] = value.coerceIn(lo, hi)
        }
        put(NeuralFeatures.OUT_APPROACH, .50f + .18f * dng - .40f * danger)
        put(NeuralFeatures.OUT_SOCIAL, .50f + .10f * nsc - .08f * danger)
        put(NeuralFeatures.OUT_CURIOSITY, .28f + .45f * an)
        put(NeuralFeatures.OUT_CAUTION, .10f + .78f * danger)
        put(NeuralFeatures.OUT_ENERGY, .82f - .16f * wing)
        put(NeuralFeatures.OUT_VALENCE, .10f * nsc - .22f * danger, -1f, 1f)
        put(NeuralFeatures.OUT_ORIENT_X, lastVisualX * (.18f + .62f * an), -1f, 1f)
        put(NeuralFeatures.OUT_ORIENT_Y, lastVisualY * (.18f + .62f * an), -1f, 1f)
        put(NeuralFeatures.OUT_ATTEND_USER, .12f + .20f * nsc)
        put(NeuralFeatures.OUT_ATTEND_MOTION, an)
        put(NeuralFeatures.OUT_ATTEND_SOUND, danger)
        put(NeuralFeatures.OUT_STARTLE, danger)
        put(NeuralFeatures.OUT_INSPECT, an * (1f - danger * .65f))
    }

    override fun readout(): FloatArray = output.copyOf()

    override fun reset() {
        state.fill(0f); output.fill(0f); lastVisualX = 0f; lastVisualY = 0f
        lastSubsteps = 0; resets++
    }

    override fun diagnostics(): CoreDiagnostics {
        val mean = state.average().toFloat()
        val max = state.maxOrNull() ?: 0f
        return CoreDiagnostics(
            substrate = substrate, units = GROUPS.size, meanAbsActivity = mean,
            maxAbsActivity = max, saturatedUnits = state.count { it >= .98f },
            silentUnits = state.count { it <= .01f }, substeps = lastSubsteps,
            nanResets = resets,
            extra = mapOf(
                "banc.neurons" to 76f, "banc.directed_pairs" to 515f,
                "banc.synapse_counts" to 3316f, "banc.descending_dng27" to state[DNG27],
                "banc.wing_power" to state[WING_POWER], "banc.leg_nociception" to state[LEG_NOCICEPTOR],
                "banc.thorax_nociception" to state[THORAX_NOCICEPTOR],
            ),
        )
    }

    companion object {
        private val GROUPS = arrayOf("ANXXX139", "DNg27", "ISN", "l_NSC_CRZ", "leg_nociceptor", "thorax_nociceptor", "wing_power_motor_neuron")
        private const val ANXXX139 = 0; private const val DNG27 = 1; private const val ISN = 2
        private const val NSC = 3; private const val LEG_NOCICEPTOR = 4; private const val THORAX_NOCICEPTOR = 5; private const val WING_POWER = 6
        private val TAU_MS = floatArrayOf(110f, 90f, 110f, 180f, 70f, 70f, 55f)
        // Raw anatomical counts from the audited group summary. Each row divides by its
        // own total before integration; this preserves relative topology without treating
        // a raw count as a physical weight.
        private val RAW_COUNTS = arrayOf(
            intArrayOf(222, 31, 6, 215, 17, 1, 0),
            intArrayOf(61, 135, 0, 104, 0, 0, 265),
            intArrayOf(17, 116, 150, 4, 0, 0, 0),
            intArrayOf(0, 33, 0, 28, 0, 0, 0),
            intArrayOf(58, 0, 0, 25, 733, 2, 0),
            intArrayOf(9, 0, 0, 0, 2, 21, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 1061),
        )
        private val NORMALIZED_COUNTS: Array<FloatArray> = Array(RAW_COUNTS.size) { pre ->
            val total = RAW_COUNTS[pre].sum().coerceAtLeast(1)
            FloatArray(GROUPS.size) { post -> RAW_COUNTS[pre][post].toFloat() / total }
        }
    }
}
