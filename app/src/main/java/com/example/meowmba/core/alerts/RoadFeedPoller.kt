package com.example.meowmba.core.alerts

import android.content.Context
import android.util.Log
import com.example.meowmba.core.nav.CurrentLocation
import com.example.meowmba.core.thermal.ThermalGovernor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Nearby reports are reevaluated against current GPS independently of slow
 * provider requests. A temporary foreground/driving pause must not kill the
 * job: the user used to have to toggle Road alerts off/on after backgrounding. */
object RoadFeedPoller {
    const val POLL_MS = 5_000L
    const val WAZE_REFRESH_MS = 45_000L
    const val SEEN_TTL_MS = 8 * 60_000L
    const val OSM_TTL_MS = 5 * 60_000L

    /**
     * Each tick costs a location fix plus up to two network round trips. Five
     * seconds is right for a cool device in a car; on a hot one it is wasted
     * heat, so the governor stretches it. Stays 1x (and therefore keeps every
     * existing timing assertion in RoadFeedPollerTest true) while cool.
     */
    internal fun pollIntervalMs(): Long = POLL_MS * ThermalGovernor.pollIntervalScale()

    suspend fun run(
        context: Context,
        onStatus: (String) -> Unit = {},
        enabled: () -> Boolean,
    ) = runWithSources(
        enabled = enabled,
        onStatus = onStatus,
        resolveFix = { resolveFix(context) },
        fetchWaze = { fix -> withContext(Dispatchers.IO) { if (ArizonaWorkZones.covers(fix.lat, fix.lon)) ArizonaWorkZones.fetch(fix.lat, fix.lon)
            else RoadFeed.fetchWazeResult(fix.lat, fix.lon) } },
        fetchOsm = { fix -> withContext(Dispatchers.IO) { RoadFeed.fetchOsmResult(fix.lat, fix.lon) } },
    )

    internal suspend fun runWithSources(
        enabled: () -> Boolean,
        onStatus: (String) -> Unit = {},
        resolveFix: suspend () -> CurrentLocation.Fix?,
        fetchWaze: suspend (CurrentLocation.Fix) -> RoadFeed.FetchResult,
        fetchOsm: suspend (CurrentLocation.Fix) -> RoadFeed.FetchResult,
        nowMs: () -> Long = System::currentTimeMillis,
        postAlert: (RoadAlert) -> Unit = RoadAlertBus::post,
    ) = coroutineScope {
        val seen = mutableMapOf<String, Long>()
        var waze: RoadFeed.FetchResult? = null
        var osm: RoadFeed.FetchResult? = null
        var wazeTask: Deferred<RoadFeed.FetchResult>? = null
        var osmTask: Deferred<RoadFeed.FetchResult>? = null
        var lastWazeAttempt: Long? = null
        var lastOsmAttempt: Long? = null
        var osmLat: Double? = null
        var osmLon: Double? = null
        var previousStatus: String? = null
        fun status(message: String) {
            if (previousStatus != message) { previousStatus = message; onStatus(message) }
        }

        while (isActive) {
            if (!enabled()) {
                delay(pollIntervalMs())
                continue
            }
            try {
                if (wazeTask?.isCompleted == true) { waze = wazeTask?.await(); wazeTask = null }
                if (osmTask?.isCompleted == true) { osm = osmTask?.await(); osmTask = null }
                val fix = resolveFix()
                ensureActive()
                if (!enabled()) continue
                if (fix == null || !RoadReportPolicy.usableFix(fix, nowMs())) {
                    status("Road reports paused: waiting for a fresh, precise location. Check location permission and GPS.")
                    delay(pollIntervalMs())
                    continue
                }
                val now = nowMs()
                seen.entries.removeAll { now - it.value > SEEN_TTL_MS }
                if (wazeTask == null && retryAllowed(waze, now) &&
                    (lastWazeAttempt == null || now - lastWazeAttempt >= WAZE_REFRESH_MS)) {
                    lastWazeAttempt = now
                    wazeTask = async { safelyFetch("Waze", nowMs) { fetchWaze(fix) } }
                }
                val moved = osmLat?.let { RoadFeed.metersBetween(fix.lat, fix.lon, it, osmLon!!) > 800.0 } ?: true
                if (osmTask == null && retryAllowed(osm, now) &&
                    (lastOsmAttempt == null || now - lastOsmAttempt >= OSM_TTL_MS ||
                        (moved && now - lastOsmAttempt >= 15_000L))) {
                    lastOsmAttempt = now
                    osmLat = fix.lat
                    osmLon = fix.lon
                    osmTask = async { safelyFetch("OSM", nowMs) { fetchOsm(fix) } }
                }
                status(RoadReportPolicy.sourceStatus(waze, osm, now))
                val pool = listOfNotNull(waze, osm)
                    .filter { it.available && RoadReportPolicy.feedIsFresh(it, now) }
                    .flatMap { it.hits }
                val hit = RoadFeed.pickNearest(
                    hits = pool,
                    originLat = fix.lat,
                    originLon = fix.lon,
                    radiusM = RoadReportPolicy.radiusFor(fix),
                    seen = seen.keys,
                    headingDegrees = RoadReportPolicy.headingFor(fix),
                    nowMs = now,
                )
                ensureActive()
                // Nothing captured/fetched before a pause or toggle may announce
                // afterward unless the gate still permits it.
                if (hit != null && enabled() && RoadReportPolicy.usableFix(fix, nowMs())) {
                    seen[hit.key()] = now
                    postAlert(RoadFeed.toAlert(hit, fix.lat, fix.lon, nowMs()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("RoadFeed", "tick failed: ${e.javaClass.simpleName}")
                status("Road reports unavailable: ${e.javaClass.simpleName}.")
            }
            delay(pollIntervalMs())
        }
    }

    private fun retryAllowed(result: RoadFeed.FetchResult?, nowMs: Long): Boolean =
        result == null || result.available || nowMs - result.fetchedAtMs !in 0 until result.retryAfterMs

    /** Catch inside each async child: catching only around await is too late,
     * because a failed child has already cancelled both providers and the poller. */
    private suspend fun safelyFetch(
        source: String,
        nowMs: () -> Long,
        fetch: suspend () -> RoadFeed.FetchResult,
    ): RoadFeed.FetchResult = try {
        fetch()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RoadFeed.FetchResult(source, available = false, detail = e.javaClass.simpleName,
            fetchedAtMs = nowMs(), retryAfterMs = WAZE_REFRESH_MS)
    }

    private suspend fun resolveFix(context: Context): CurrentLocation.Fix? {
        if (!CurrentLocation.hasPermission(context)) return null
        CurrentLocation.last(context)?.takeIf { RoadReportPolicy.usableFix(it) }?.let { return it }
        val live = suspendCancellableCoroutine { cont ->
            CurrentLocation.requestOnce(context, timeoutMs = 8_000L,
                requiredAccuracyMeters = RoadReportPolicy.MAX_ACCURACY_M) { fix ->
                if (cont.isActive) cont.resume(fix, onCancellation = null)
            }
        }
        // Never fall back to an arbitrarily old location after GPS times out.
        return live?.takeIf { RoadReportPolicy.usableFix(it) }
    }
}
