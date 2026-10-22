package com.example.meowmba

import com.example.meowmba.ui.companion.driveRoadSourceSummary
import com.example.meowmba.ui.companion.driveSpeedLabel
import com.example.meowmba.ui.companion.driveVehicleWarning
import org.junit.Assert.*
import org.junit.Test

class DriveCardPresentationTest {
    @Test fun sourceFailuresAreInformationWithoutClaimingCompleteTraffic() {
        assertEquals("Road data limited", driveRoadSourceSummary(
            "Waze reports (unofficial): unavailable (HTTP 403). OSM mapped cameras: reports available. Coverage is incomplete."))
        assertEquals("Waiting for location", driveRoadSourceSummary(
            "Road reports paused: waiting for a fresh, precise location. Check location permission and GPS."))
        assertEquals("Checking road data", driveRoadSourceSummary("Checking road sources…"))
        assertEquals("Refreshing road data", driveRoadSourceSummary("Road reports: waiting for fresh data."))
        assertEquals("Road sources", driveRoadSourceSummary("AZ511 work zones: no reports returned. Coverage is incomplete."))
        assertEquals("Drive mode", driveRoadSourceSummary(null))
    }

    @Test fun realObdWarningsStayAvailableIndependentlyOfExpandedDetails() {
        assertEquals("⚠ Engine coolant is hot", driveVehicleWarning("RPM 1500 · 115°C · 12.8V · ⚠ Engine coolant is hot"))
        assertEquals("⚠ Low battery · Stop safely", driveVehicleWarning("⚠ Low battery · Stop safely"))
        assertNull(driveVehicleWarning("RPM 1200 · 90°C · 13.8V"))
        assertNull(driveVehicleWarning(null))
    }

    @Test fun invalidSpeedDoesNotBecomeAFabricatedReading() {
        assertEquals("55 mph", driveSpeedLabel(55.1f))
        assertEquals("0 mph", driveSpeedLabel(0f))
        assertEquals("— mph", driveSpeedLabel(Float.NaN))
        assertEquals("— mph", driveSpeedLabel(Float.POSITIVE_INFINITY))
        assertEquals("— mph", driveSpeedLabel(-1f))
    }
}
