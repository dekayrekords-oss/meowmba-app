package com.example.meowmba.core.alerts

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.math.cos

/** Official, route-free AZDOT work zones. No police, congestion or radar claims.
 * Cache the statewide feed; moving the phone never triggers a statewide download. */
internal object ArizonaWorkZones {
    private const val SOURCE = "AZ511 work zones"
    private const val REFRESH_MS = 5 * 60_000L
    private const val MAX_FEED_AGE_MS = 30 * 60_000L
    private var cached: List<Zone>? = null
    private var fetchedAt = 0L
    private var updatedAt = 0L
    private var attemptedAt = 0L
    private var failure = "Not checked"

    internal data class Point(val lat: Double, val lon: Double)
    internal data class Zone(val id: String, val street: String, val description: String,
        val start: Long, val end: Long, val points: List<Point>) {
        val north = points.maxOf { it.lat }; val south = points.minOf { it.lat }
        val west = points.minOf { it.lon }; val east = points.maxOf { it.lon }
    }
    internal data class Feed(val updatedAt: Long, val zones: List<Zone>)

    // This search envelope includes border roads; the source label states AZ coverage.
    fun covers(lat: Double, lon: Double) = lat in 31.2..37.1 && lon in -114.9..-109.0

    @Synchronized fun fetch(lat: Double, lon: Double, now: Long = System.currentTimeMillis()): RoadFeed.FetchResult {
        if (attemptedAt == 0L || now - attemptedAt !in 0 until REFRESH_MS) {
            attemptedAt = now
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("https://az511.com/api/wzdx").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6_000; readTimeout = 12_000
                    setRequestProperty("Accept", "application/geo+json, application/json")
                    setRequestProperty("User-Agent", "Meowmba/1.3 (road work reports)")
                }
                val code = conn.responseCode
                check(code in 200..299) { "HTTP $code" }
                val bytes = conn.inputStream.use { it.readNBytes(12_000_001) }
                check(bytes.size <= 12_000_000) { "Feed exceeds size limit" }
                val feed = parse(bytes.toString(Charsets.UTF_8))
                check(now - feed.updatedAt in -60_000L..MAX_FEED_AGE_MS) { "Feed timestamp is stale" }
                cached = feed.zones; updatedAt = feed.updatedAt; fetchedAt = now
                failure = ""
            } catch (e: Exception) {
                // No response body, URL parameters or location in diagnostic text.
                failure = if (e is IllegalStateException) e.message ?: "Invalid feed" else e.javaClass.simpleName
                cached = null
            } finally { conn?.disconnect() }
        }
        val zones = cached
        if (zones == null || now - updatedAt !in -60_000L..MAX_FEED_AGE_MS) {
            return RoadFeed.FetchResult(SOURCE, available = false, detail = failure.ifBlank { "Feed is stale" },
                fetchedAtMs = attemptedAt, retryAfterMs = REFRESH_MS)
        }
        return RoadFeed.FetchResult(SOURCE, nearby(zones, lat, lon, now, updatedAt), true,
            detail = "Arizona work zones only; no police reports", fetchedAtMs = fetchedAt)
    }

    internal fun parse(body: String): Feed {
        val root = JSONObject(body)
        require(root.getString("type") == "FeatureCollection")
        val updated = time(root.getJSONObject("feed_info").getString("update_date")) ?: error("Missing feed timestamp")
        val features = root.getJSONArray("features")
        require(features.length() <= 20_000)
        val zones = (0 until features.length()).mapNotNull { i -> runCatching {
            val feature = features.getJSONObject(i)
            val props = feature.getJSONObject("properties")
            val core = props.getJSONObject("core_details")
            if (core.optString("event_type") != "work-zone") return@mapNotNull null
            val geometry = feature.getJSONObject("geometry")
            if (geometry.optString("type") != "LineString") return@mapNotNull null
            val coordinates = geometry.getJSONArray("coordinates")
            if (coordinates.length() !in 2..20_000) return@mapNotNull null
            val points = (0 until coordinates.length()).map { j ->
                val p = coordinates.getJSONArray(j)
                Point(p.getDouble(1), p.getDouble(0)).also { require(RoadFeed.validCoordinates(it.lat, it.lon)) }
            }
            val roads = core.optJSONArray("road_names")
            Zone(feature.getString("id"), roads?.let { (0 until it.length()).joinToString(" / ") { n -> it.getString(n) } } ?: "Road work",
                core.optString("description").take(500), time(props.getString("start_date")) ?: error("start"),
                time(props.getString("end_date")) ?: error("end"), points)
        }.getOrNull() }
        // Malformed feature data must not masquerade as an empty healthy feed.
        require(features.length() == 0 || zones.isNotEmpty())
        return Feed(updated, zones)
    }

    internal fun nearby(zones: List<Zone>, lat: Double, lon: Double, now: Long, updated: Long): List<RoadFeed.Hit> {
        val box = RoadFeed.bboxAround(lat, lon)
        return zones.asSequence().filter { now in it.start until it.end && it.north >= box.bottom &&
            it.south <= box.top && it.east >= box.left && it.west <= box.right }.mapNotNull { zone ->
            // Project onto each segment, so long work zones aren't only visible at their endpoints.
            val scale = cos(Math.toRadians(lat)).coerceAtLeast(.2)
            val nearest = zone.points.zipWithNext().map { (a,b) ->
                val ax = (a.lon-lon)*scale; val ay = a.lat-lat
                val dx = (b.lon-a.lon)*scale; val dy = b.lat-a.lat
                val denominator = dx*dx+dy*dy
                val t = if (denominator == 0.0) 0.0 else (-(ax*dx+ay*dy)/denominator).coerceIn(0.0,1.0)
                Point(a.lat+(b.lat-a.lat)*t, a.lon+(b.lon-a.lon)*t)
            }.minByOrNull { RoadFeed.metersBetween(lat,lon,it.lat,it.lon) } ?: return@mapNotNull null
            if (RoadFeed.metersBetween(lat,lon,nearest.lat,nearest.lon) > RoadFeed.BOX_RADIUS_M) return@mapNotNull null
            RoadFeed.Hit(RoadAlert.Kind.HAZARD, nearest.lat, nearest.lon, zone.street, zone.description,
                SOURCE, zone.id, reportedAtMs = updated, validUntilMs = zone.end)
        }.toList()
    }
    private fun time(value: String): Long? = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}
