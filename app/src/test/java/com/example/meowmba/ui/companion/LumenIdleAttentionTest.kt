package com.example.meowmba.ui.companion

import com.example.meowmba.core.behavior.DriveVector
import com.example.meowmba.core.behavior.NervousSystem
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM tests: [LumenIdleAttention] and [LumenSpring] are pure Kotlin, so
 * these run without Robolectric.
 *
 * The point of these tests is behavioural, not structural: they assert that the
 * drive-weighted family draw actually changes what the character *does*, rather
 * than that a private field took a particular value. Every run is deterministic
 * because the score is seeded.
 */
class LumenIdleAttentionTest {

    private fun biasOf(drives: DriveVector) = NervousSystem(initial = drives).bias()

    /**
     * Run the idle score for [seconds] at 50ms steps and collect every emitted
     * point. 0.05 is the largest dt `step` accepts, matching a slow frame.
     */
    private fun samples(bias: com.example.meowmba.core.behavior.BehaviorBias?, seconds: Int = 100): List<LumenAttentionPoint> {
        val out = ArrayList<LumenAttentionPoint>()
        for (seed in 0 until 40) {
            val idle = LumenIdleAttention(seed * 7919)
            repeat(seconds * 20) { out.add(idle.step(0.05f, true, bias)) }
        }
        return out
    }

    @Test
    fun `curiosity explores vertically more than caution does`() {
        // Family 2 is the vertical-curiosity score and is the only family with a
        // strongly negative y, so counting those samples measures it directly.
        val curious = samples(biasOf(DriveVector(curiosity = 1f))).count { it.y <= -0.2f }
        val wary = samples(biasOf(DriveVector(caution = 0.9f))).count { it.y <= -0.2f }
        assertTrue(
            "a curious companion should look up more than a wary one (curious=$curious, wary=$wary)",
            curious > wary,
        )
    }

    @Test
    fun `caution shortens the glances`() {
        // glanceGain is applied to amplitude, so the same score played cautiously
        // should cover less ground. Measured on moving samples only; the resting
        // family emits exactly zero and would dilute both sides equally.
        fun meanMagnitude(bias: com.example.meowmba.core.behavior.BehaviorBias?): Float {
            val moving = samples(bias).filter { it.x != 0f || it.y != 0f }
            return moving.sumOf { kotlin.math.abs(it.x).toDouble() + kotlin.math.abs(it.y).toDouble() }
                .toFloat() / moving.size
        }
        val calm = meanMagnitude(biasOf(DriveVector()))
        val wary = meanMagnitude(biasOf(DriveVector(caution = 0.9f)))
        assertTrue("a wary companion should glance less far (calm=$calm, wary=$wary)", wary < calm)
    }

    @Test
    fun `a heavily skewed draw still rests sometimes`() {
        // Weights are floored and the draw is without replacement, so even a
        // strongly lopsided bias must not collapse into one repeated family —
        // that would read as a nervous tic rather than a character.
        val extreme = biasOf(DriveVector(curiosity = 1f, caution = 0f))
        val points = samples(extreme)
        val resting = points.count { it.x == 0f && it.y == 0f }
        val moving = points.count { it.x != 0f || it.y != 0f }
        assertTrue("the score should still return to rest (resting=$resting, moving=$moving)", resting > 0)
        assertTrue("the score should still move (resting=$resting, moving=$moving)", moving > 0)
    }

    @Test
    fun `a terrified and exhausted companion still moves`() {
        // Caution 1 + energy 0 drives vitality to zero. Without a floor on the
        // gains the amplitude would decay to nothing and the avatar would sit
        // perfectly still, which reads as a crashed renderer, not a wary
        // character. Presence is the point of the drive layer.
        val spent = biasOf(DriveVector(caution = 1f, energy = 0f, curiosity = 0f))
        val moving = samples(spent).filter { it.x != 0f || it.y != 0f }
        assertTrue("a spent companion must still move", moving.isNotEmpty())
        val furthest = moving.maxOf { kotlin.math.abs(it.x) + kotlin.math.abs(it.y) }
        assertTrue(
            "motion must stay visible, furthest was $furthest",
            furthest > 0.1f,
        )
    }

    @Test
    fun `no bias leaves the score running`() {
        // The default path must stay alive: nothing that omits the bias should
        // end up with a frozen character.
        val points = samples(null, seconds = 40)
        assertTrue("the unweighted score should still move", points.any { it.x != 0f || it.y != 0f })
    }
}
