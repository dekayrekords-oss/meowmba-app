package com.example.meowmba

import com.example.meowmba.core.alerts.RoadAlert
import com.example.meowmba.core.alerts.RoadFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadFeedTest {

    private val hereLat = 30.271
    private val hereLon = -97.743

    private val wazeBody = """
        {
          "alerts": [
            {
              "type": "POLICE",
              "subtype": "POLICE_HIDING",
              "street": "N Lamar Blvd",
              "city": "Austin",
              "uuid": "p1",
              "location": { "x": -97.7435, "y": 30.272 }
            },
            {
              "type": "JAM",
              "street": "I-35",
              "location": { "x": -97.740, "y": 30.270 }
            },
            {
              "type": "ACCIDENT",
              "street": "6th St",
              "uuid": "a1",
              "location": { "x": -97.744, "y": 30.2705 }
            }
          ],
          "cameras": [
            { "id": 99, "speed": 35, "location": { "x": -97.744, "y": 30.269 } }
          ]
        }
    """.trimIndent()

    @Test
    fun `waze police is kept and jams are dropped`() {
        val hits = RoadFeed.parseWaze(wazeBody)
        assertEquals(3, hits.size)
        assertEquals(1, hits.count { it.kind == RoadAlert.Kind.POLICE })
        assertEquals(1, hits.count { it.kind == RoadAlert.Kind.CAMERA })
        assertEquals(1, hits.count { it.kind == RoadAlert.Kind.HAZARD })
        assertEquals("Waze", hits.first { it.kind == RoadAlert.Kind.POLICE }.source)
        assertEquals("N Lamar Blvd", hits.first { it.kind == RoadAlert.Kind.POLICE }.street)
    }

    @Test
    fun `osm speed camera becomes a camera hit`() {
        val body = """
            {"elements":[
              {"type":"node","id":1,"lat":30.269,"lon":-97.744,
               "tags":{"highway":"speed_camera","maxspeed":"35 mph"}}
            ]}
        """.trimIndent()
        val hits = RoadFeed.parseOsm(body)
        assertEquals(1, hits.size)
        assertEquals(RoadAlert.Kind.CAMERA, hits[0].kind)
        assertEquals("OSM", hits[0].source)
        assertEquals("35 mph zone", hits[0].street)
    }

    @Test
    fun `html or empty bodies parse as nothing`() {
        assertTrue(RoadFeed.parseWaze("").isEmpty())
        assertTrue(RoadFeed.parseWaze("<html>403</html>").isEmpty())
        assertTrue(RoadFeed.parseOsm("<!doctype html>").isEmpty())
    }

    @Test
    fun `north america uses na shard`() {
        assertEquals("na", RoadFeed.wazeEnv(30.27, -97.74))
        assertEquals("row", RoadFeed.wazeEnv(51.50, -0.12))
        assertEquals("il", RoadFeed.wazeEnv(32.08, 34.78))
    }

    @Test
    fun `nearby camera is not starved by distant police`() {
        val hits = listOf(
            RoadFeed.Hit(RoadAlert.Kind.CAMERA, hereLat + 0.001, hereLon, source = "OSM", id = "close"),
            RoadFeed.Hit(RoadAlert.Kind.POLICE, hereLat + 0.02, hereLon, source = "Waze", id = "far"),
        )
        val pick = RoadFeed.pickNearest(hits, hereLat, hereLon)!!
        assertEquals(RoadAlert.Kind.CAMERA, pick.kind)
    }

    @Test
    fun `seen keys are skipped`() {
        val hits = RoadFeed.parseWaze(wazeBody)
        val police = hits.first { it.kind == RoadAlert.Kind.POLICE }
        val pick = RoadFeed.pickNearest(hits, hereLat, hereLon, seen = setOf(police.key()))!!
        assertEquals(RoadAlert.Kind.HAZARD, pick.kind)
    }

    @Test
    fun `a report 3km away is still in range`() {
        val mid = listOf(
            RoadFeed.Hit(
                kind = RoadAlert.Kind.POLICE,
                lat = hereLat + 0.027,
                lon = hereLon,
                source = "Waze",
                id = "3km",
            ),
        )
        val pick = RoadFeed.pickNearest(mid, hereLat, hereLon)
        assertEquals(RoadAlert.Kind.POLICE, pick?.kind)
    }

    @Test
    fun `far reports are ignored`() {
        val far = listOf(
            RoadFeed.Hit(
                kind = RoadAlert.Kind.POLICE,
                lat = 30.40,
                lon = -97.90,
                source = "Waze",
                id = "far",
            ),
        )
        assertNull(RoadFeed.pickNearest(far, hereLat, hereLon))
    }

    @Test
    fun `summary includes distance and street`() {
        val hit = RoadFeed.Hit(
            kind = RoadAlert.Kind.POLICE,
            lat = 30.272,
            lon = -97.7435,
            street = "N Lamar Blvd",
            source = "Waze",
            id = "p1",
        )
        val alert = RoadFeed.toAlert(hit, hereLat, hereLon)
        assertTrue(alert.summary.startsWith("Police "))
        assertTrue(alert.summary.contains("N Lamar Blvd"))
        assertEquals("Waze", alert.source)
    }

    @Test
    fun `waze url is the public live-map feed`() {
        val url = RoadFeed.wazeUrl(30.27, -97.74)
        assertTrue(url.startsWith("https://www.waze.com/live-map/api/georss?"))
        assertTrue(url.contains("env=na"))
        assertTrue(url.contains("types=alerts,traffic"))

    }
}
