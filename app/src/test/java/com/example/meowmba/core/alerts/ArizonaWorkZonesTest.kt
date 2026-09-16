package com.example.meowmba.core.alerts

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ArizonaWorkZonesTest {
    private val now = Instant.parse("2026-09-10T00:30:00Z").toEpochMilli()
    private fun fixture(start: String = "2026-09-01T00:00:00Z", end: String = "2026-10-01T00:00:00Z", geometry: String = "[[-112.1,33.5],[-111.9,33.5]]") = """
        {"type":"FeatureCollection","feed_info":{"update_date":"2026-09-10T00:29:00Z"},"features":[
        {"id":"zone-1","properties":{"core_details":{"event_type":"work-zone","road_names":["Test Road"],"description":"Road work"},
        "start_date":"$start","end_date":"$end"},"geometry":{"type":"LineString","coordinates":$geometry}}]}
    """.trimIndent()
    @Test fun routeFreeLookupIncludesMiddleOfLongZone() {
        val feed = ArizonaWorkZones.parse(fixture())
        val hit = ArizonaWorkZones.nearby(feed.zones,33.5,-112.0,now,feed.updatedAt).single()
        assertEquals(-112.0, hit.lon, .00001)
        assertEquals(RoadAlert.Kind.HAZARD,hit.kind)
        assertEquals("AZ511 work zones",hit.source)
        assertTrue(RoadFeed.reportIsCurrent(hit,now))
        assertFalse(RoadFeed.reportIsCurrent(hit,hit.validUntilMs!!))
        assertTrue(ArizonaWorkZones.nearby(feed.zones,35.5,-112.0,now,feed.updatedAt).isEmpty())
    }
    @Test fun futureAndExpiredZonesDoNotAlert() {
        for (body in listOf(fixture(start="2026-09-11T00:00:00Z"),fixture(end="2026-09-09T00:00:00Z"))) {
            val feed = ArizonaWorkZones.parse(body)
            assertTrue(ArizonaWorkZones.nearby(feed.zones,33.5,-112.0,now,feed.updatedAt).isEmpty())
        }
    }
    @Test fun invalidResponseDoesNotBecomeEmptyHealthyFeed() {
        for (body in listOf("<html>not found</html>", "{}", fixture(geometry="[[200,95],[201,96]]")))
            assertTrue(runCatching { ArizonaWorkZones.parse(body) }.isFailure)
    }
    @Test fun cachedOfficialFeedHasExplicitCoverageAndExpires() {
        val result = RoadFeed.FetchResult("AZ511 work zones", available=true, fetchedAtMs=now)
        assertTrue(RoadReportPolicy.feedIsFresh(result,now+300_000))
        assertFalse(RoadReportPolicy.feedIsFresh(result,now+601_000))
        assertTrue(RoadReportPolicy.sourceStatus(result,null,now).contains("no police reports"))
    }
}
