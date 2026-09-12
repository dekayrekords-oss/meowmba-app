package com.example.meowmba.neural

import com.example.meowmba.core.neural.AdaptivePreferenceModel
import org.junit.Assert.*
import org.junit.Test

class AdaptivePreferenceModelTest {
    @Test fun feedbackIsExplicitRateLimitedAndBounded() {
        val model = AdaptivePreferenceModel()
        assertFalse(model.reward("permission", true, 0))
        assertTrue(model.reward("play", true, 0))
        assertFalse(model.reward("play", true, 100))
        assertFalse(model.reward("play", true, -1))
        for (i in 1..1100) model.reward("play", true, i * 60_000L)
        assertEquals(1f, model.preference("play"), 0f)
        assertFalse(model.reward("rest", true, 100_000_000))
    }
    @Test fun snapshotRoundTripsAndEnforcesClockAndOff() {
        val first = AdaptivePreferenceModel()
        first.reward("rest", false, 60_000)
        val next = AdaptivePreferenceModel.decode(first.encode())
        assertEquals(-.05f, next.preference("rest"), 0f)
        assertFalse(next.reward("play", true, 0))
        next.enabled = false
        assertEquals(0f, next.preference("rest"), 0f)
        assertFalse(next.reward("play", true, 180_000))
        assertFalse(AdaptivePreferenceModel.decode(next.encode()).enabled)
        next.reset()
        next.enabled = true
        assertEquals(0f, next.preference("rest"), 0f)
        assertTrue(next.reward("play", true, 0))
    }
    @Test fun corruptSnapshotsCannotInjectWeights() {
        for (bad in listOf("", "1|true|0|1|NaN,0,0,0", "1|true|0|1|2,0,0,0", "1|true|-1|1|0,0,0,0")) {
            assertEquals(0f, AdaptivePreferenceModel.decode(bad).preference("feed"), 0f)
        }
    }
}
