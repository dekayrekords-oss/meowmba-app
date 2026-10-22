package com.example.meowmba

import com.example.meowmba.ui.companion.*
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationSampling138Test {
    @Test fun allocationFreeSamplingExactlyPreservesEveryBuiltInAtBoundariesAndBetweenKeys() {
        val edge = CustomAnimationSpec("sparse and duplicate keys", listOf(
            AnimFrame(0), AnimFrame(100, eyeOpen = .2f, happyEyes = false),
            AnimFrame(100, eyeOpen = .7f, happyEyes = true), AnimFrame(200),
            AnimFrame(500, eyeOpen = 1.1f, tongueOut = .8f), AnimFrame(800),
        ))
        for (spec in CustomAnimations.BUILT_INS.values + edge) {
            val times = sortedSetOf(-1L, 0L, spec.durationMs.toLong(), spec.durationMs + 1L)
            for (frame in spec.frames) for (delta in -1L..1L) times += frame.atMs + delta
            for (pair in spec.frames.zipWithNext()) times += (pair.first.atMs + pair.second.atMs) / 2L
            for (t in times) assertEquals("${spec.name} at $t", reference(spec, t), CustomAnimations.sample(spec, t))
        }
    }

    /** Previous implementation retained as a differential oracle, including sparse-field semantics. */
    private fun reference(spec: CustomAnimationSpec, time: Long): AnimFrame? {
        if (time < 0 || time > spec.durationMs) return null
        val t = time.toFloat()
        fun num(pick: (AnimFrame) -> Float?): Float? {
            val keyed = spec.frames.mapNotNull { f -> pick(f)?.let { f.atMs to it } }
            if (keyed.isEmpty()) return null
            val after = keyed.firstOrNull { it.first >= t } ?: return keyed.last().second
            val before = keyed.lastOrNull { it.first <= t } ?: return after.second
            if (after.first == before.first) return before.second
            val u = (t - before.first) / (after.first - before.first)
            val eased = u * u * (3f - 2f * u)
            return before.second + (after.second - before.second) * eased
        }
        fun <T> discrete(pick: (AnimFrame) -> T?): T? {
            val keyed = spec.frames.mapNotNull { f -> pick(f)?.let { f.atMs to it } }
            return (keyed.lastOrNull { it.first <= t } ?: keyed.firstOrNull())?.second
        }
        return AnimFrame(time.toInt(), num { it.eyeOpen }, num { it.pupilSize }, num { it.pupilGazeUp },
            num { it.headTiltDeg }, num { it.earPerk }, num { it.earDroopDeg },
            discrete { it.happyEyes }, discrete { it.whiskerFlutter }, discrete { it.mouth },
            discrete { it.glowColorHex }, num { it.tongueOut }, num { it.bodyBounce },
            num { it.tailWag }, num { it.sparkle })
    }
}
