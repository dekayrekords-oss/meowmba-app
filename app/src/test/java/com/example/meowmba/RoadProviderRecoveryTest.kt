package com.example.meowmba

import com.example.meowmba.core.alerts.RoadAlert
import com.example.meowmba.core.alerts.RoadFeed
import com.example.meowmba.core.alerts.RoadFeedPoller
import com.example.meowmba.core.nav.CurrentLocation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoadProviderRecoveryTest {
    @Test fun `access denial is preserved without retired 404 fallback and requests back off`() {
        var now = 1_000_000L
        val requests = mutableListOf<String>()
        val provider = RoadFeed.WazeProvider(request = { url, _ ->
            requests += url
            RoadFeed.Response(statusCode = 403)
        }, nowMs = { now })
        val first = provider.fetch(30.27, -97.74)
        assertFalse(first.available)
        assertTrue(first.detail!!.contains("HTTP 403"))
        assertTrue(first.detail!!.contains("Use Waze navigation"))
        assertEquals(30 * 60_000L, first.retryAfterMs)
        now += 45_000L
        assertEquals(first, provider.fetch(30.29, -97.76))
        assertEquals(1, requests.size)
        assertFalse(requests.single().contains("TGeoRSS"))
        now += 30 * 60_000L
        provider.fetch(30.29, -97.76)
        assertEquals(2, requests.size)
    }

    @Test fun `removed endpoint recovers after its backoff and never looks like empty success`() {
        var now = 1_000_000L
        var calls = 0
        val provider = RoadFeed.WazeProvider(request = { _, _ ->
            calls++
            if (calls == 1) RoadFeed.Response(statusCode = 404)
            else RoadFeed.Response(body = "{\"alerts\":[]}", statusCode = 200)
        }, nowMs = { now })
        assertFalse(provider.fetch(30.27, -97.74).available)
        now += 30 * 60_000L
        assertTrue(provider.fetch(30.27, -97.74).available)
        provider.fetch(30.28, -97.75)
        assertEquals(3, calls) // Success does not reuse a previous geographic response.
    }

    @Test fun `HTML or error payload with HTTP200 is unavailable with useful reason`() {
        val provider = RoadFeed.WazeProvider(request = { _, _ ->
            RoadFeed.Response(body = "<html>sign in</html>", statusCode = 200)
        })
        val result = provider.fetch(30.27, -97.74)
        assertFalse(result.available)
        assertTrue(result.detail!!.contains("invalid report payload"))
        assertFalse(result.detail!!.contains("HTTP 200"))
    }

    @Test fun `bad coordinates never leave the device`() {
        val provider = RoadFeed.WazeProvider(request = { _, _ ->
            error("Must not request invalid GPS")
        })
        assertFalse(provider.fetch(Double.NaN, -97.74).available)
        assertFalse(provider.fetch(30.27, 181.0).available)
    }

    @Test fun `provider backoff respects Retry After seconds and HTTP dates with bounds`() {
        assertEquals(90_000L, RoadFeed.retryAfterMs("90"))
        assertEquals(86_400_000L, RoadFeed.retryAfterMs(Long.MAX_VALUE.toString()))
        assertEquals(0L, RoadFeed.retryAfterMs("-2"))
        assertEquals(0L, RoadFeed.retryAfterMs(null))
        assertEquals(0L, RoadFeed.retryAfterMs("not a date"))
        assertEquals(60_000L, RoadFeed.retryAfterMs("Thu, 1 Jan 1970 00:01:00 GMT", 0L))
        val result = RoadFeed.failedResponse("Waze", RoadFeed.Response(
            statusCode = 429, retryAfterMs = 3_600_000L), 0L)
        assertEquals(3_600_000L, result.retryAfterMs)
        assertFalse(result.available)
    }

    @Test fun `OSM list does not retry the renamed instance as a separate provider`() {
        assertEquals(listOf("https://overpass.private.coffee", "https://overpass-api.de"), RoadFeed.OSM_HOSTS)
    }

    @Test fun `provider exception cannot cancel camera provider or polling job`() = runTest {
        fun now() = 1_000_000L + testScheduler.currentTime
        val reports = mutableListOf<RoadAlert>()
        val statuses = mutableListOf<String>()
        val camera = RoadFeed.Hit(RoadAlert.Kind.CAMERA, 30.001, -97.0,
            source = "OSM", id = "camera")
        val job = launch {
            RoadFeedPoller.runWithSources(
                enabled = { true },
                resolveFix = { CurrentLocation.Fix(30.0, -97.0, now(), accuracyMeters = 10f) },
                fetchWaze = { throw IllegalStateException("provider failed") },
                fetchOsm = { RoadFeed.FetchResult("OSM", listOf(camera), available = true, fetchedAtMs = now()) },
                nowMs = ::now, onStatus = statuses::add, postAlert = reports::add,
            )
        }
        runCurrent()
        advanceTimeBy(10_000L)
        runCurrent()
        assertTrue(job.isActive)
        assertEquals("OSM", reports.single().source)
        assertTrue(statuses.any { it.contains("unavailable (IllegalStateException)") })
        job.cancel()
    }

    @Test fun `poller honors unavailable provider cooldown while other provider keeps refreshing`() = runTest {
        var wazeCalls = 0
        var osmCalls = 0
        fun now() = 1_000_000L + testScheduler.currentTime
        val job = launch {
            RoadFeedPoller.runWithSources(
                enabled = { true },
                resolveFix = { CurrentLocation.Fix(30.0, -97.0, now(), accuracyMeters = 10f) },
                fetchWaze = {
                    wazeCalls++
                    RoadFeed.FetchResult("Waze", available = false, fetchedAtMs = now(),
                        retryAfterMs = 30 * 60_000L)
                },
                fetchOsm = {
                    osmCalls++
                    RoadFeed.FetchResult("OSM", available = true, fetchedAtMs = now())
                },
                nowMs = ::now,
            )
        }
        runCurrent()
        advanceTimeBy(20 * 60_000L)
        runCurrent()
        assertTrue(job.isActive)
        assertEquals(1, wazeCalls)
        assertTrue(osmCalls >= 4)
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(2, wazeCalls)
        job.cancel()
    }
}
