package com.example.meowmba.core.thermal

import android.os.PowerManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalGovernorTest {

    /**
     * The governor is a process-wide singleton and [ThermalGovernor.pollIntervalScale]
     * feeds RoadFeedPoller's delay. Leaving a tier behind would silently change
     * timing in unrelated tests, so every test cleans up.
     */
    @After fun tearDown() {
        ThermalGovernor.uninstall()
        ThermalGovernor.resetForTest()
    }

    @Test fun `status maps to tiers with severe as the cutoff for dropping the mic`() {
        assertEquals(ThermalTier.NORMAL, ThermalGovernor.tierFor(PowerManager.THERMAL_STATUS_NONE))
        assertEquals(ThermalTier.NORMAL, ThermalGovernor.tierFor(PowerManager.THERMAL_STATUS_LIGHT))
        assertEquals(ThermalTier.ECONOMY, ThermalGovernor.tierFor(PowerManager.THERMAL_STATUS_MODERATE))
        assertEquals(ThermalTier.SURVIVAL, ThermalGovernor.tierFor(PowerManager.THERMAL_STATUS_SEVERE))
        assertEquals(ThermalTier.SURVIVAL, ThermalGovernor.tierFor(PowerManager.THERMAL_STATUS_CRITICAL))
        assertEquals(ThermalTier.SURVIVAL, ThermalGovernor.tierFor(PowerManager.THERMAL_STATUS_EMERGENCY))
    }

    @Test fun `escalation is immediate so a hot device drops load now`() {
        ThermalGovernor.apply(PowerManager.THERMAL_STATUS_SEVERE, 1_000L)
        assertEquals(ThermalTier.SURVIVAL, ThermalGovernor.currentTier())
        assertFalse(ThermalGovernor.allowAlwaysOnMic())
    }

    @Test fun `recovery waits out the cooldown instead of flapping`() {
        ThermalGovernor.apply(PowerManager.THERMAL_STATUS_SEVERE, 1_000L)
        assertEquals(ThermalTier.SURVIVAL, ThermalGovernor.currentTier())

        // A device sitting exactly on the threshold reports cool for one sample.
        // Restarting the microphone on that would cost more heat than waiting.
        ThermalGovernor.apply(PowerManager.THERMAL_STATUS_NONE, 2_000L)
        assertEquals(ThermalTier.SURVIVAL, ThermalGovernor.currentTier())
        ThermalGovernor.apply(PowerManager.THERMAL_STATUS_NONE, 61_000L)
        assertEquals(ThermalTier.SURVIVAL, ThermalGovernor.currentTier())
        ThermalGovernor.apply(PowerManager.THERMAL_STATUS_NONE, 62_000L)
        assertEquals(ThermalTier.NORMAL, ThermalGovernor.currentTier())
        assertTrue(ThermalGovernor.allowAlwaysOnMic())
    }

    @Test fun `a relapse during cooldown cancels the recovery clock`() {
        ThermalGovernor.apply(PowerManager.THERMAL_STATUS_SEVERE, 1_000L)
        ThermalGovernor.apply(PowerManager.THERMAL_STATUS_NONE, 2_000L)
        ThermalGovernor.apply(PowerManager.THERMAL_STATUS_SEVERE, 3_000L)
        assertEquals(ThermalTier.SURVIVAL, ThermalGovernor.currentTier())
        // 62s of reported cool, but the clock restarted at 3_000L, so still hot.
        ThermalGovernor.apply(PowerManager.THERMAL_STATUS_NONE, 65_000L)
        assertEquals(ThermalTier.SURVIVAL, ThermalGovernor.currentTier())
    }

    @Test fun `moderate backsoff polls and speech gate but keeps the mic`() {
        assertEquals(1, ThermalGovernor.pollIntervalScale())
        assertEquals(1.0f, ThermalGovernor.asrGateScale(), 0.001f)
        assertTrue(ThermalGovernor.allowDecorativeAnimation())

        ThermalGovernor.apply(PowerManager.THERMAL_STATUS_MODERATE, 10_000L)
        assertEquals(ThermalTier.ECONOMY, ThermalGovernor.currentTier())
        assertEquals(3, ThermalGovernor.pollIntervalScale())
        assertEquals(2.0f, ThermalGovernor.asrGateScale(), 0.001f)
        assertFalse(ThermalGovernor.allowDecorativeAnimation())
        // Voice still has to work at status 2 — only a severe device loses it.
        assertTrue(ThermalGovernor.allowAlwaysOnMic())
    }

    @Test fun `severe suspends the mic and stretches polls hardest`() {
        ThermalGovernor.apply(PowerManager.THERMAL_STATUS_CRITICAL, 10_000L)
        assertEquals(6, ThermalGovernor.pollIntervalScale())
        assertFalse(ThermalGovernor.allowAlwaysOnMic())
        assertFalse(ThermalGovernor.allowDecorativeAnimation())
    }

    @Test fun `cool default leaves every consumer at full rate`() {
        ThermalGovernor.resetForTest()
        assertEquals(ThermalTier.NORMAL, ThermalGovernor.currentTier())
        assertEquals(1, ThermalGovernor.pollIntervalScale())
        assertTrue(ThermalGovernor.allowAlwaysOnMic())
        assertTrue(ThermalGovernor.allowDecorativeAnimation())
    }
}
