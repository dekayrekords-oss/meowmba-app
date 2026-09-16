package com.example.meowmba.ui.companion

import android.os.PowerManager
import org.junit.Assert.*
import org.junit.Test

class AvatarWorkPolicyTest {
    @Test fun hiddenScreenAndSevereHeatStopAnimationWhileModerateGetsSparseMotion() {
        assertTrue(allowAvatarWork(true, true, 0))
        assertTrue(allowAvatarWork(true, true, 1))
        assertFalse(allowAvatarWork(false, true, 0))
        assertFalse(allowAvatarWork(true, false, 0))
        assertTrue(allowAvatarWork(true, true, PowerManager.THERMAL_STATUS_MODERATE))
        for (status in PowerManager.THERMAL_STATUS_SEVERE..6) assertFalse(allowAvatarWork(true, true, status))
    }

    @Test fun noneAndLightNeverShowThermalUi() {
        for (status in listOf(
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT,
        )) {
            val (warn, since) = resolveThermalUiWarning(
                thermalStatus = status,
                elevatedSinceMs = 0L,
                nowMs = 50_000L,
                previouslyShowing = false,
            )
            assertEquals(ThermalUiWarning.NONE, warn)
            assertEquals(0L, since)
            assertNull(thermalUiBannerText(warn))
        }
    }

    @Test fun moderateNeedsHoldBeforeMildBanner() {
        val t0 = 10_000L
        val (early, since) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_MODERATE,
            elevatedSinceMs = 0L,
            nowMs = t0,
            previouslyShowing = false,
        )
        assertEquals(ThermalUiWarning.NONE, early)
        assertEquals(t0, since)

        val (stillEarly, _) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_MODERATE,
            elevatedSinceMs = since,
            nowMs = t0 + THERMAL_UI_HOLD_MS - 1,
            previouslyShowing = false,
        )
        assertEquals(ThermalUiWarning.NONE, stillEarly)

        val (mild, _) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_MODERATE,
            elevatedSinceMs = since,
            nowMs = t0 + THERMAL_UI_HOLD_MS,
            previouslyShowing = false,
        )
        assertEquals(ThermalUiWarning.MILD, mild)
        assertEquals(
            "Cooling animations — camera and motion ease until the device settles.",
            thermalUiBannerText(mild),
        )
    }

    @Test fun severeAfterHoldShowsPhoneIsHot() {
        val (hot, _) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_SEVERE,
            elevatedSinceMs = 1_000L,
            nowMs = 1_000L + THERMAL_UI_HOLD_MS,
            previouslyShowing = false,
        )
        assertEquals(ThermalUiWarning.HOT, hot)
        assertTrue(thermalUiBannerText(hot)!!.startsWith("Phone is hot"))
        assertFalse(thermalUiBannerText(hot)!!.contains("overheat", ignoreCase = true))
    }

    @Test fun flickerBelowModerateResetsHold() {
        val (warn, since) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_LIGHT,
            elevatedSinceMs = 5_000L,
            nowMs = 20_000L,
            previouslyShowing = true,
        )
        assertEquals(ThermalUiWarning.NONE, warn)
        assertEquals(0L, since)
    }

    @Test fun onceShowingSeverityTracksWithoutRehold() {
        val (hot, _) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_SEVERE,
            elevatedSinceMs = 1_000L,
            nowMs = 1_500L,
            previouslyShowing = true,
        )
        assertEquals(ThermalUiWarning.HOT, hot)
        val (mild, _) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_MODERATE,
            elevatedSinceMs = 1_000L,
            nowMs = 1_600L,
            previouslyShowing = true,
        )
        assertEquals(ThermalUiWarning.MILD, mild)
    }
}
