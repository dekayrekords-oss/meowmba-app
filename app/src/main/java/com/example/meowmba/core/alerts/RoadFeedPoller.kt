package com.example.meowmba.core.alerts

import android.content.Context
import android.util.Log
import com.example.meowmba.core.nav.CurrentLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.coroutineContext

/**
 * Every [POLL_MS], read GPS and pull the live map into [RoadAlertBus].
 *
 * Does not need Waze or Maps open, and does not need a route. Stops when
 * [enabled] returns false or the calling job is cancelled.
 */
object RoadFeedPoller {

    const val POLL_MS = 45_000L
    const val SEEN_TTL_MS = 8 * 60_000L
    const val OSM_TTL_MS = 5 * 60_000L

    suspend fun run(context: Context, enabled: () -> Boolean) {
        val seen = mutableMapOf<String, Long>()
        var lastOsmAt = 0L
        var lastOsm: List<RoadFeed.Hit> = emptyList()
        var lastOsmLat = 0.0
        var lastOsmLon = 0.0
        var first = true
        while (coroutineContext.isActive && enabled()) {
            if (!first) delay(POLL_MS) else first = false
            if (!enabled()) return
            try {
                val fix = resolveFix(context)
                if (fix == null) {
                    Log.w(TAG, "skip: no GPS fix (permission or no location yet)")
                    continue
                }
                prune(seen)
                val waze = runCatching { RoadFeed.fetchWaze(fix.lat, fix.lon) }
                    .onFailure { Log.w(TAG, "waze failed: ${it.message}") }
                    .getOrElse { emptyList() }
                val now = System.currentTimeMillis()
                val osmStale = now - lastOsmAt > OSM_TTL_MS ||
                    RoadFeed.metersBetween(fix.lat, fix.lon, lastOsmLat, lastOsmLon) > 800
                if (osmStale) {
                    lastOsm = runCatching { RoadFeed.fetchOsm(fix.lat, fix.lon) }
                        .onFailure { Log.w(TAG, "osm failed: ${it.message}") }
                        .getOrElse { emptyList() }
                    lastOsmAt = now
                    lastOsmLat = fix.lat
                    lastOsmLon = fix.lon
                }
                val pool = waze + lastOsm
                val hit = RoadFeed.pickNearest(
                    hits = pool,
                    originLat = fix.lat,
                    originLon = fix.lon,
                    seen = seen.keys,
                )
                if (hit == null) {
                    Log.i(
                        TAG,
                        "skip: no hit in ${RoadFeed.ALERT_RADIUS_M.toInt()}m " +
                            "waze=${waze.size} osm=${lastOsm.size} " +
                            "fix=${"%.4f".format(fix.lat)},${"%.4f".format(fix.lon)} " +
                            "age=${fix.ageMs()}ms",
                    )
                    continue
                }
                seen[hit.key()] = now
                val alert = RoadFeed.toAlert(hit, fix.lat, fix.lon)
                Log.i(TAG, "post ${alert.kind} ${alert.summary}")
                RoadAlertBus.post(alert)
            } catch (e: Exception) {
                Log.w(TAG, "tick failed: ${e.message}")
            }
        }
    }

    private fun prune(seen: MutableMap<String, Long>) {
        val cutoff = System.currentTimeMillis() - SEEN_TTL_MS
        val stale = seen.filterValues { it < cutoff }.keys
        stale.forEach { seen.remove(it) }
    }

    private suspend fun resolveFix(context: Context): CurrentLocation.Fix? {
        if (!CurrentLocation.hasPermission(context)) {
            Log.w(TAG, "skip: location permission off")
            return null
        }
        val cached = CurrentLocation.last(context)
        if (cached != null && cached.isFresh()) {
            Log.i(TAG, "fix last-known age=${cached.ageMs()}ms")
            return cached
        }
        val live = suspendCancellableCoroutine { cont ->
            CurrentLocation.requestOnce(context, timeoutMs = 8_000L) { fix ->
                if (cont.isActive) cont.resume(fix, onCancellation = null)
            }
        }
        if (live != null) {
            Log.i(TAG, "fix live ${"%.4f".format(live.lat)},${"%.4f".format(live.lon)}")
            return live
        }
        if (cached != null) {
            Log.w(TAG, "fix stale last-known age=${cached.ageMs()}ms — using it")
            return cached
        }
        return null
    }

    private const val TAG = "RoadFeed"
}
