package com.example.meowmba.behavior

import com.example.meowmba.core.behavior.BehaviorBias
import com.example.meowmba.core.behavior.DriveVector
import com.example.meowmba.core.behavior.NervousSystem
import com.example.meowmba.core.behavior.SocialTarget
import com.example.meowmba.core.behavior.Stimulus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM tests: [NervousSystem] is pure Kotlin with no Android imports, so it
 * runs without Robolectric.
 */
class NervousSystemTest {

    // baseline defaults to the standard resting drives, NOT to `drives`: making
    // the starting state the resting state is what stops anything from moving.
    private fun nervous(
        drives: DriveVector = DriveVector(),
        baseline: DriveVector = DriveVector(),
    ) = NervousSystem(initial = drives, baseline = baseline)

    @Test
    fun `drives relax toward baseline over time`() {
        val n = nervous(drives = DriveVector(curiosity = 1f))
        repeat(200) { n.tick(50) } // 10s, ~3 time constants
        val curiosity = n.snapshot().curiosity
        assertTrue("curiosity should fall toward 0.40, was $curiosity", curiosity < 0.45f)
    }

    @Test
    fun `one long step equals many short steps`() {
        val coarse = nervous(drives = DriveVector(curiosity = 1f))
        coarse.tick(1_000)

        val fine = nervous(drives = DriveVector(curiosity = 1f))
        repeat(100) { fine.tick(10) }

        assertEquals(
            "frame-rate independence",
            coarse.snapshot().curiosity,
            fine.snapshot().curiosity,
            1e-3f,
        )
    }

    @Test
    fun `novelty raises curiosity and clamps at one`() {
        val n = nervous(drives = DriveVector(curiosity = 0.9f))
        n.onStimulus(Stimulus.NOVELTY, 1f)
        n.onStimulus(Stimulus.NOVELTY, 1f)
        assertEquals(1f, n.snapshot().curiosity, 1e-5f)
    }

    @Test
    fun `threat raises caution and suppresses approach`() {
        val before = nervous(drives = DriveVector(approach = 0.8f, caution = 0.0f))
        val n = nervous(drives = DriveVector(approach = 0.8f, caution = 0.0f))
        n.onStimulus(Stimulus.THREAT, 1f)
        assertTrue(n.snapshot().caution > before.snapshot().caution)
        assertTrue(n.snapshot().approach < before.snapshot().approach)
    }

    @Test
    fun `invalid stimulus magnitude is ignored`() {
        val n = nervous(drives = DriveVector(curiosity = 0.5f))
        n.onStimulus(Stimulus.NOVELTY, -3f)
        n.onStimulus(Stimulus.NOVELTY, Float.NaN)
        n.onStimulus(Stimulus.NOVELTY, Float.POSITIVE_INFINITY)
        assertEquals(0.5f, n.snapshot().curiosity, 1e-5f)
    }

    @Test
    fun `reasoning layer intent sets target and eases approach toward it`() {
        val n = nervous(drives = DriveVector(approach = 0.1f))
        n.setIntent(SocialTarget.USER, approach = 0.9f, valenceBias = 0.5f)
        assertEquals(SocialTarget.USER, n.socialTarget)
        repeat(400) { n.tick(50) } // 20s ~ 5 time constants
        val approach = n.snapshot().approach
        assertTrue("approach should ease toward 0.9, was $approach", approach > 0.85f)
    }

    @Test
    fun `clearing intent returns approach to baseline`() {
        val n = nervous(drives = DriveVector(approach = 0.2f))
        n.setIntent(SocialTarget.USER, approach = 0.9f)
        repeat(100) { n.tick(50) }
        n.clearIntent()
        assertEquals(SocialTarget.NONE, n.socialTarget)
        repeat(400) { n.tick(50) }
        // Back to the documented resting approach of 0.30, not to the 0.2 it started at.
        assertEquals(0.30f, n.snapshot().approach, 0.02f)
    }

    @Test
    fun `sustained arousal drains energy and rest restores it`() {
        // Drives relax in seconds, so arousal has to be re-fed to stay high:
        // a single stimulus cannot tire the character out over minutes.
        val n = nervous()
        repeat(600) {
            n.onStimulus(Stimulus.THREAT, 1f)
            n.onStimulus(Stimulus.NOVELTY, 1f)
            n.tick(200)
        }
        val drained = n.snapshot().energy
        assertTrue("energy should drain, was $drained", drained < 0.8f)

        repeat(600) { n.tick(200) } // 2 min of calm
        val rested = n.snapshot().energy
        assertTrue("energy should recover, was $rested", rested > drained)
    }

    @Test
    fun `bias stays finite and within range`() {
        val n = nervous(drives = DriveVector())
        n.onStimulus(Stimulus.LOUD_SOUND, 1f)
        val b: BehaviorBias = n.bias()
        assertTrue(b.glanceGain.isFinite() && b.glanceGain >= 0f)
        assertTrue(b.followGain.isFinite() && b.followGain >= 0f)
        assertTrue(b.arousal in 0f..1f)
        assertTrue(b.valence in -1f..1f)
        assertTrue(b.blinkRateScale in 0.5f..2f)
        assertTrue(b.restWeight >= 0f && b.inspectWeight >= 0f && b.verticalWeight >= 0f)
    }

    @Test
    fun `caution quiets the character down`() {
        val calm = nervous(drives = DriveVector(caution = 0f, curiosity = 0.8f))
        val wary = nervous(drives = DriveVector(caution = 0.9f, curiosity = 0.8f))
        assertTrue(wary.bias().glanceGain < calm.bias().glanceGain)
        assertTrue(wary.bias().arousal > calm.bias().arousal)
    }

    @Test
    fun `drives never leave zero to one`() {
        val n = nervous(drives = DriveVector(approach = 0.5f, caution = 0.5f))
        repeat(50) { n.onStimulus(Stimulus.THREAT, 1f) }
        repeat(50) { n.onStimulus(Stimulus.SPOKEN_TO, 1f) }
        n.tick(1_000)
        val d = n.snapshot()
        listOf(d.approach, d.social, d.curiosity, d.caution, d.energy).forEach {
            assertTrue("drive out of range: $d", it in 0f..1f)
        }
    }

    @Test
    fun `resting bias is a safe divisor for the motor layer`() {
        // LumenRig divides live gains by these. If a retune ever pushed one to
        // zero the ratios would become Infinity and quietly NaN the animation,
        // so the resting values must stay strictly positive and finite.
        val b = NervousSystem.REST_BIAS
        listOf(
            "glanceGain" to b.glanceGain, "followGain" to b.followGain,
            "earGain" to b.earGain, "blinkRateScale" to b.blinkRateScale,
            "restWeight" to b.restWeight, "inspectWeight" to b.inspectWeight,
            "verticalWeight" to b.verticalWeight,
        ).forEach { (name, value) ->
            assertTrue("$name must be finite and > 0, was $value", value.isFinite() && value > 0f)
        }
    }

    @Test
    fun `a resting character reproduces the authored animation`() {
        // The whole point of normalising against REST_BIAS: at rest every ratio
        // is 1.0, so the hand-tuned idle score is untouched.
        val resting = nervous().bias()
        assertEquals(resting.glanceGain, NervousSystem.REST_BIAS.glanceGain, 1e-5f)
        assertEquals(resting.followGain, NervousSystem.REST_BIAS.followGain, 1e-5f)
        assertEquals(resting.earGain, NervousSystem.REST_BIAS.earGain, 1e-5f)
        assertEquals(resting.blinkRateScale, NervousSystem.REST_BIAS.blinkRateScale, 1e-5f)
    }

    @Test
    fun `non finite drives are clamped on construction`() {
        val n = nervous(drives = DriveVector(curiosity = Float.NaN, energy = 5f))
        val d = n.snapshot()
        assertTrue(d.isFinite())
        assertTrue(d.energy <= 1f)
    }
}
