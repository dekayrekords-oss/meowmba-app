package com.example.meowmba.core.alerts

import com.example.meowmba.core.nav.CurrentLocation

/** These bounds suppress unsupported warnings; they do not turn community
 * reports into a verified detector or a route-aware navigation service. */
object RoadReportPolicy {
    const val MAX_FIX_AGE_MS = 15_000L
    const val MAX_ACCURACY_M = 100f

    fun usableFix(fix: CurrentLocation.Fix, nowMs: Long = System.currentTimeMillis()): Boolean =
        RoadFeed.validCoordinates(fix.lat, fix.lon) && fix.atMs > 0L &&
            nowMs - fix.atMs in -2_000L..MAX_FIX_AGE_MS &&
            fix.accuracyMeters?.let { it.isFinite() && it in 0f..MAX_ACCURACY_M } == true

    fun headingFor(fix: CurrentLocation.Fix): Double? {
        val speed = fix.speedMps ?: return null
        val bearing = fix.bearingDegrees ?: return null
        val accuracy = fix.bearingAccuracyDegrees ?: return null
        return bearing.toDouble().takeIf {
            speed.isFinite() && speed >= 3f && bearing.isFinite() && bearing in 0f..360f &&
                accuracy.isFinite() && accuracy in 0f..30f
        }
    }

    /** About 40 seconds of lead distance, bounded to avoid reports miles away
     * when parked. This remains straight-line distance, never a route distance. */
    fun radiusFor(fix: CurrentLocation.Fix): Double =
        fix.speedMps?.takeIf { it.isFinite() && it >= 0f }
            ?.let { (it * 40.0).coerceIn(350.0, 1_600.0) } ?: 500.0

    fun feedIsFresh(result: RoadFeed.FetchResult, nowMs: Long): Boolean =
        nowMs - result.fetchedAtMs in 0L..(if (result.source in setOf("OSM", "AZ511 work zones")) 10 * 60_000L else 2 * 60_000L)

    fun sourceStatus(waze: RoadFeed.FetchResult?, osm: RoadFeed.FetchResult?, nowMs: Long): String {
        fun describe(result: RoadFeed.FetchResult?, label: String): String = when {
            result == null -> "$label: checking"
            !result.available -> "$label: unavailable (${result.detail ?: "connection failed"})"
            !feedIsFresh(result, nowMs) -> "$label: waiting for fresh data"
            result.hits.isEmpty() -> "$label: no reports returned"
            else -> "$label: reports available"
        }
        return describe(waze, if (waze?.source == "Waze") "Waze reports (unofficial)" else waze?.source ?: "Road reports") + ". " +
            describe(osm, "OSM mapped cameras") + ". Coverage is incomplete." + if (waze?.source == "AZ511 work zones") " Arizona road work only; no police reports." else ""
    }
}
