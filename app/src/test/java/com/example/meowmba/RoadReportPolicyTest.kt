package com.example.meowmba

import com.example.meowmba.core.alerts.RoadAlert
import com.example.meowmba.core.alerts.RoadFeed
import com.example.meowmba.core.alerts.RoadReportPolicy
import com.example.meowmba.core.nav.CurrentLocation
import org.junit.Assert.*
import org.junit.Test

class RoadReportPolicyTest {
    private val now = 2_000_000L
    private val fix = CurrentLocation.Fix(30.0, -97.0, now,
        speedMps = 20f, accuracyMeters = 12f, bearingDegrees = 0f, bearingAccuracyDegrees = 8f)
    private fun hit(id: String, north: Double, east: Double = 0.0) = RoadFeed.Hit(
        RoadAlert.Kind.POLICE, fix.lat + north, fix.lon + east, source = "Waze", id = id,
        reportedAtMs = now - 60_000L,
    )

    @Test fun `warnings reject stale approximate invalid and future location fixes`() {
        assertTrue(RoadReportPolicy.usableFix(fix, now))
        assertFalse(RoadReportPolicy.usableFix(fix.copy(atMs = now - 16_000L), now))
        assertFalse(RoadReportPolicy.usableFix(fix.copy(atMs = now + 60_000L), now))
        assertFalse(RoadReportPolicy.usableFix(fix.copy(accuracyMeters = 900f), now))
        assertFalse(RoadReportPolicy.usableFix(fix.copy(accuracyMeters = null), now))
        assertFalse(RoadReportPolicy.usableFix(fix.copy(lat = Double.NaN), now))
        assertFalse(RoadReportPolicy.usableFix(fix.copy(lon = 190.0), now))
    }

    @Test fun `only moving precise bearings filter direction`() {
        assertEquals(0.0, RoadReportPolicy.headingFor(fix)!!, 0.0)
        assertNull(RoadReportPolicy.headingFor(fix.copy(speedMps = 0f)))
        assertNull(RoadReportPolicy.headingFor(fix.copy(bearingAccuracyDegrees = 80f)))
        assertNull(RoadReportPolicy.headingFor(fix.copy(bearingAccuracyDegrees = null)))
        assertNull(RoadReportPolicy.headingFor(fix.copy(bearingDegrees = Float.NaN)))
    }

    @Test fun `reports behind or on a distant side road are rejected while moving north`() {
        val behind = hit("behind", -0.002)
        val across = hit("across", 0.004, 0.01)
        val ahead = hit("ahead", 0.008)
        assertEquals(ahead, RoadFeed.pickNearest(listOf(behind, across, ahead), fix.lat, fix.lon,
            headingDegrees = 0.0, nowMs = now))
        assertNull(RoadFeed.pickNearest(listOf(behind, across), fix.lat, fix.lon,
            headingDegrees = 0.0, nowMs = now))
        // Unknown heading makes no ahead/on-route claim and still permits nearby reports.
        assertEquals(behind, RoadFeed.pickNearest(listOf(behind), fix.lat, fix.lon, nowMs = now))
    }

    @Test fun `old police reports and malformed coordinates cannot win proximity selection`() {
        val stale = hit("stale", 0.001).copy(reportedAtMs = now - RoadFeed.POLICE_REPORT_TTL_MS - 1)
        val invalid = hit("bad", 0.001).copy(lat = 200.0)
        val fresh = hit("fresh", 0.005)
        assertEquals(fresh, RoadFeed.pickNearest(listOf(stale, invalid, fresh), fix.lat, fix.lon, nowMs = now))
    }

    @Test fun `distinct camera ids within a rounded location cell are not suppressed together`() {
        val a = hit("a", 0.001).copy(kind = RoadAlert.Kind.CAMERA)
        val b = a.copy(id = "b", lat = a.lat + 0.00001)
        assertNotEquals(a.key(), b.key())
        assertEquals(b, RoadFeed.pickNearest(listOf(a, b), fix.lat, fix.lon, seen = setOf(a.key()), nowMs = now))
    }

    @Test fun `summary names report source and age without inventing ahead direction`() {
        val summary = RoadFeed.toAlert(hit("near", 0.0001), fix.lat, fix.lon, now).summary
        assertTrue(summary.contains("Waze"))
        assertTrue(summary.contains("1 min ago"))
        assertFalse(summary.contains("ahead"))
        assertTrue(RoadFeed.toAlert(hit("unknown", 0.001).copy(reportedAtMs = null), fix.lat, fix.lon, now)
            .summary.contains("age unknown"))
    }

    @Test fun `successful empty feed differs from unavailable or invalid payload`() {
        assertTrue(RoadFeed.hasWazePayload("{\"alerts\":[]}"))
        assertFalse(RoadFeed.hasWazePayload("{\"error\":\"denied\"}"))
        assertFalse(RoadFeed.hasWazePayload("<html>blocked</html>"))
        assertTrue(RoadFeed.hasOsmPayload("{\"elements\":[]}"))
        assertFalse(RoadFeed.hasOsmPayload("{\"elements\":[],\"remark\":\"timed out\"}"))
        val failed = RoadFeed.FetchResult("Waze", available = false, detail = "HTTP 403", fetchedAtMs = now)
        val empty = RoadFeed.FetchResult("OSM", available = true, fetchedAtMs = now)
        val status = RoadReportPolicy.sourceStatus(failed, empty, now)
        assertTrue(status.contains("unavailable (HTTP 403)"))
        assertTrue(status.contains("no reports returned"))
    }

    @Test fun `warning radius grows with travel speed and is bounded`() {
        assertEquals(350.0, RoadReportPolicy.radiusFor(fix.copy(speedMps = 0f)), 0.0)
        assertEquals(800.0, RoadReportPolicy.radiusFor(fix), 0.0)
        assertEquals(1600.0, RoadReportPolicy.radiusFor(fix.copy(speedMps = 100f)), 0.0)
    }
}
