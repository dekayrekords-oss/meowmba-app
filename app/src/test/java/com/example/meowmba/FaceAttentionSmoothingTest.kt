package com.example.meowmba

import com.example.meowmba.core.vision.FaceAttentionSmoothing
import org.junit.Assert.*
import org.junit.Test

class FaceAttentionSmoothingTest {
    @Test fun firstObservedPositionDoesNotInventHistory() {
        assertEquals(1f, FaceAttentionSmoothing.alpha(0, 1f, false), 0f)
    }

    @Test fun realMovementCatchesUpFasterThanBoxNoise() {
        val quiet = FaceAttentionSmoothing.alpha(33, .015f, true)
        val moving = FaceAttentionSmoothing.alpha(33, .8f, true)
        assertTrue(quiet in .34f.. .36f)
        assertTrue(moving > .65f && moving < .70f)
        assertTrue(FaceAttentionSmoothing.alpha(133, .8f, true) > .98f)
    }

    @Test fun elapsedTimeNotFrameCountControlsQuietFiltering() {
        fun remaining(periodMs: Long, samples: Int): Float {
            var value = 1f
            repeat(samples) { value *= 1f - FaceAttentionSmoothing.alpha(periodMs, 0f, true) }
            return value
        }
        assertEquals(remaining(10, 12), remaining(30, 4), .00001f)
        assertEquals(remaining(10, 12), remaining(120, 1), .00001f)
    }

    @Test fun badTimingAndDisplacementCannotProduceInvalidOrOvershootingAlpha() {
        for (dt in listOf(Long.MIN_VALUE, -1L, 0L, 1L, 1000L, Long.MAX_VALUE))
            for (error in listOf(-1f, 0f, .1f, 100f, Float.NaN, Float.POSITIVE_INFINITY)) {
                val alpha = FaceAttentionSmoothing.alpha(dt, error, true)
                assertTrue(alpha.isFinite() && alpha in 0f..1f)
            }
    }
}
