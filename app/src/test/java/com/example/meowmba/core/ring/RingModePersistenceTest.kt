package com.example.meowmba.core.ring

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingModePersistenceTest {
    @Test fun customRequestsAndActionsSurvivePresetSwitchesAndRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("smart_ring", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("controls_consent_v1", true).putBoolean("paused", false).apply()
        fun recreate(): AndroidSmartRing {
            AndroidSmartRing::class.java.getDeclaredField("singleton").apply { isAccessible = true }.set(null, null)
            return AndroidSmartRing.get(context)
        }
        var driver = recreate()
        // The expanded modes have a new disclosure; older consent cannot silently enable them.
        assertFalse(driver.state.value.controlsConsent); assertTrue(driver.state.value.paused)
        driver.setControlsConsent(true); driver.setPaused(false)
        driver.setCustomCommand("Single tap", "Show my next task")
        driver.setMapping("Previous", RingAction.HOME)
        assertTrue(driver.state.value.paused)
        val custom = driver.state.value.mappings
        driver.setMode(RingMode.MEDIA); assertEquals(RingAction.MEDIA_PREVIOUS, driver.state.value.mappings["Previous"])
        driver.setMode(RingMode.CAMERA); assertEquals(RingAction.CAMERA_SHUTTER, driver.state.value.mappings["Single tap"])
        driver.setMode(RingMode.CUSTOM); assertEquals(custom, driver.state.value.mappings)
        driver = recreate()
        assertEquals(RingMode.CUSTOM, driver.state.value.mode); assertEquals(custom, driver.state.value.mappings)
        assertEquals("Show my next task", driver.state.value.customCommands["Single tap"])
        assertTrue(driver.state.value.controlsConsent); assertTrue(driver.state.value.paused)
    }
}
