package com.example.meowmba

import com.example.meowmba.core.alerts.RoadAlert
import com.example.meowmba.core.alerts.RoadFeed
import com.example.meowmba.core.alerts.RoadFeedPoller
import com.example.meowmba.core.nav.CurrentLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoadFeedPollerTest {
    @Test fun `temporary background pause resumes without toggling feature`() = runTest {
        var enabled = false
        var fetches = 0
        val alerts = mutableListOf<RoadAlert>()
        fun now() = 1_000_000L + testScheduler.currentTime
        fun fix() = CurrentLocation.Fix(30.0, -97.0, now(), accuracyMeters = 10f)
        val camera = RoadFeed.Hit(RoadAlert.Kind.CAMERA, 30.001, -97.0, source = "OSM", id = "camera")
        val job = launch {
            RoadFeedPoller.runWithSources(
                enabled = { enabled }, resolveFix = { fix() },
                fetchWaze = { fetches++; RoadFeed.FetchResult("Waze", available = true, fetchedAtMs = now()) },
                fetchOsm = { RoadFeed.FetchResult("OSM", listOf(camera), available = true, fetchedAtMs = now()) },
                nowMs = ::now, postAlert = alerts::add,
            )
        }
        runCurrent()
        advanceTimeBy(10_000L)
        runCurrent()
        assertTrue(job.isActive)
        assertEquals(0, fetches)
        enabled = true
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(1, fetches)
        assertEquals(1, alerts.size)
        enabled = false
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(1, fetches)
        enabled = true
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(2, fetches)
        job.cancel()
    }

    @Test fun `slow police provider does not block mapped camera warnings`() = runTest {
        val alerts = mutableListOf<RoadAlert>()
        fun now() = 1_000_000L + testScheduler.currentTime
        val camera = RoadFeed.Hit(RoadAlert.Kind.CAMERA, 30.001, -97.0, source = "OSM", id = "camera")
        val job = launch {
            RoadFeedPoller.runWithSources(
                enabled = { true },
                resolveFix = { CurrentLocation.Fix(30.0, -97.0, now(), accuracyMeters = 10f) },
                fetchWaze = {
                    delay(60_000L)
                    RoadFeed.FetchResult("Waze", available = false, detail = "timeout", fetchedAtMs = now())
                },
                fetchOsm = { RoadFeed.FetchResult("OSM", listOf(camera), available = true, fetchedAtMs = now()) },
                nowMs = ::now, postAlert = alerts::add,
            )
        }
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(1, alerts.size)
        assertEquals("OSM", alerts.single().source)
        job.cancel()
        advanceTimeBy(65_000L)
        runCurrent()
        assertEquals(1, alerts.size)
    }

    @Test fun `missing or stale GPS never publishes cached reports`() = runTest {
        var stale = false
        val alerts = mutableListOf<RoadAlert>()
        fun now() = 1_000_000L + testScheduler.currentTime
        val camera = RoadFeed.Hit(RoadAlert.Kind.CAMERA, 30.001, -97.0, source = "OSM", id = "camera")
        val job = launch {
            RoadFeedPoller.runWithSources(
                enabled = { true },
                resolveFix = { CurrentLocation.Fix(30.0, -97.0, now() - if (stale) 90_000L else 0L, accuracyMeters = 10f) },
                fetchWaze = { RoadFeed.FetchResult("Waze", available = true, fetchedAtMs = now()) },
                fetchOsm = { RoadFeed.FetchResult("OSM", listOf(camera), available = true, fetchedAtMs = now()) },
                nowMs = ::now, postAlert = alerts::add,
            )
        }
        runCurrent()
        stale = true
        advanceTimeBy(20_000L)
        runCurrent()
        assertTrue(alerts.isEmpty())
        stale = false
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(1, alerts.size)
        job.cancel()
    }
}
