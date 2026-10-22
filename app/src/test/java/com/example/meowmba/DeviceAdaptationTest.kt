package com.example.meowmba

import com.example.meowmba.core.setup.*
import com.example.meowmba.core.security.SecureStorageManager
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.anyString

class DeviceAdaptationTest {
    @Test fun olderPhoneGetsLightDefaultsAndNoComputerRecommendation() {
        val p = SetupDeviceProfile(3.5, 30_000_000_000L, 4, false, true)
        assertEquals(DeviceTier.LIGHT, p.tier)
        assertEquals("15", SetupDeviceScan.defaults(p)["avatar_fps_target"])
        assertEquals("classic", SetupDeviceScan.defaults(p)["avatar_style"])
        assertFalse(p.recommendPhoneComputer)
    }
    @Test fun ramAloneCannotRecommendComputerWithoutStorageOrArchitecture() {
        val p = SetupDeviceProfile(12.0, 20_000_000_000L, 8, false, true)
        assertTrue(p.recommendPhoneComputer)
        assertFalse(p.copy(freeBytes = 1_000_000_000L).recommendPhoneComputer)
        assertFalse(p.copy(arm64 = false).recommendPhoneComputer)
        assertFalse(p.copy(lowRam = true).recommendPhoneComputer)
        assertFalse(p.copy(cores = 4).recommendPhoneComputer)
        assertEquals(24, p.copy(ramGb = 6.0).recommendedFps)
    }
    @Test fun rerunningSetupPreservesExplicitAppearanceChoices() {
        val values = mutableMapOf("avatar_style" to "lumen", "avatar_fps_target" to "45", "background_kind" to "ANIMATION", "background_value" to "rain")
        val storage = mock(SecureStorageManager::class.java)
        `when`(storage.getPreference(anyString())).thenAnswer { values[it.getArgument<String>(0)] }
        doAnswer { values[it.getArgument(0)] = it.getArgument(1); null }.`when`(storage).savePreference(anyString(), anyString())
        SetupDeviceScan.applyDefaults(SetupDeviceProfile(3.0, 2_000_000_000L, 4, true, true), storage)
        assertEquals("lumen", values["avatar_style"])
        assertEquals("45", values["avatar_fps_target"])
        assertEquals("ANIMATION", values["background_kind"])
        assertEquals("rain", values["background_value"])
    }
}
