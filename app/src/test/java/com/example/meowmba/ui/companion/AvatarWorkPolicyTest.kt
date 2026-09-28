package com.example.meowmba.ui.companion

import android.os.PowerManager
import org.junit.Assert.*
import org.junit.Test

class AvatarWorkPolicyTest {
    @Test fun hiddenScreenAndCriticalHeatStopAnimationWhileSevereGetsSparseMotion() {
        assertTrue(allowAvatarWork(true, true, 0))
        assertTrue(allowAvatarWork(true, true, 1))
        assertFalse(allowAvatarWork(false, true, 0))
        assertFalse(allowAvatarWork(true, false, 0))
        assertTrue(allowAvatarWork(true, true, PowerManager.THERMAL_STATUS_MODERATE))
        assertTrue(allowAvatarWork(true, true, PowerManager.THERMAL_STATUS_SEVERE))
        for (status in PowerManager.THERMAL_STATUS_CRITICAL..6) assertFalse(allowAvatarWork(true, true, status))
    }

    @Test fun limitsSitOneStepAboveWhatTheyWere() {
        assertEquals(PowerManager.THERMAL_STATUS_SEVERE, THERMAL_EASE_AT)
        assertEquals(PowerManager.THERMAL_STATUS_CRITICAL, THERMAL_STOP_AT)
    }

    @Test fun noneLightAndModerateNeverShowThermalUi() {
        for (status in listOf(
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT,
            PowerManager.THERMAL_STATUS_MODERATE,
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

    @Test fun legacyNoneAndLightNeverShowThermalUi() {
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

    @Test fun severeNeedsHoldBeforeMildBanner() {
        val t0 = 10_000L
        val (early, since) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_SEVERE,
            elevatedSinceMs = 0L,
            nowMs = t0,
            previouslyShowing = false,
        )
        assertEquals(ThermalUiWarning.NONE, early)
        assertEquals(t0, since)

        val (stillEarly, _) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_SEVERE,
            elevatedSinceMs = since,
            nowMs = t0 + THERMAL_UI_HOLD_MS - 1,
            previouslyShowing = false,
        )
        assertEquals(ThermalUiWarning.NONE, stillEarly)

        val (mild, _) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_SEVERE,
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

    @Test fun criticalAfterHoldShowsPhoneIsHot() {
        val (hot, _) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_CRITICAL,
            elevatedSinceMs = 1_000L,
            nowMs = 1_000L + THERMAL_UI_HOLD_MS,
            previouslyShowing = false,
        )
        assertEquals(ThermalUiWarning.HOT, hot)
        assertTrue(thermalUiBannerText(hot)!!.startsWith("Phone is hot"))
        assertFalse(thermalUiBannerText(hot)!!.contains("overheat", ignoreCase = true))
    }

    @Test fun flickerBelowTheEaseLevelResetsHold() {
        val (warn, since) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_MODERATE,
            elevatedSinceMs = 5_000L,
            nowMs = 20_000L,
            previouslyShowing = true,
        )
        assertEquals(ThermalUiWarning.NONE, warn)
        assertEquals(0L, since)
    }

    @Test fun onceShowingSeverityTracksWithoutRehold() {
        val (hot, _) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_CRITICAL,
            elevatedSinceMs = 1_000L,
            nowMs = 1_500L,
            previouslyShowing = true,
        )
        assertEquals(ThermalUiWarning.HOT, hot)
        val (mild, _) = resolveThermalUiWarning(
            thermalStatus = PowerManager.THERMAL_STATUS_SEVERE,
            elevatedSinceMs = 1_000L,
            nowMs = 1_600L,
            previouslyShowing = true,
        )
        assertEquals(ThermalUiWarning.MILD, mild)
    }

    @Test fun aSwipedAwayLevelStaysHiddenUntilItGetsWorse() {
        assertFalse(thermalBannerVisible(ThermalUiWarning.NONE, null))
        assertTrue(thermalBannerVisible(ThermalUiWarning.MILD, null))
        assertFalse(thermalBannerVisible(ThermalUiWarning.MILD, ThermalUiWarning.MILD))
        assertTrue(thermalBannerVisible(ThermalUiWarning.HOT, ThermalUiWarning.MILD))
        assertFalse(thermalBannerVisible(ThermalUiWarning.HOT, ThermalUiWarning.HOT))
    }

    @Test fun swipeNeedsADeliberateDistanceOrAFlingTheSameWay() {
        assertFalse(shouldDismissSwipe(0f, 0f, 400))
        assertFalse(shouldDismissSwipe(80f, 200f, 400))
        assertTrue(shouldDismissSwipe(150f, 0f, 400))
        assertTrue(shouldDismissSwipe(-150f, 0f, 400))
        assertTrue(shouldDismissSwipe(40f, 2_000f, 400))
        assertTrue(shouldDismissSwipe(-40f, -2_000f, 400))
        assertFalse("a flick back toward the start is not a dismissal", shouldDismissSwipe(40f, -2_000f, 400))
        assertFalse(shouldDismissSwipe(300f, 5_000f, 0))
    }
}
