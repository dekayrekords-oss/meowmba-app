package com.example.meowmba.core.alerts

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nearby police / cameras / hazards from the **public live map**, not from
 * Waze or Maps notifications.
 *
 * Waze and Google Maps only notify when you are already navigating a route.
 * This feed uses the phone's GPS. The unofficial Waze live-map endpoint may
 * refuse app access; the supported traffic-data API requires a Waze partner
 * agreement. A public website is not a guaranteed public data API.
 *
 * The Waze endpoint is unofficial; neither it nor community-mapped OSM
 * cameras guarantees coverage. Failures remain distinguishable from an empty
 * successful response. These are reports, not radar/police detection.
 */
object RoadFeed {

    data class Hit(
        val kind: RoadAlert.Kind,
        val lat: Double,
        val lon: Double,
        val street: String? = null,
        val detail: String? = null,
        val source: String,
        val id: String,
        val reportedAtMs: Long? = null,
        val validUntilMs: Long? = null,
    ) {
        fun key(): String =
            if (id.isNotBlank()) "$source:$id" else "$kind:$lat:$lon"
    }

    data class FetchResult(
        val source: String,
        val hits: List<Hit> = emptyList(),
        val available: Boolean,
        val detail: String? = null,
        val fetchedAtMs: Long = System.currentTimeMillis(),
        /** Minimum delay from fetchedAtMs before another request after failure. */
        val retryAfterMs: Long = 0L,
    )

    internal data class Response(
        val body: String? = null,
        val error: String? = null,
        val statusCode: Int? = null,
        val retryAfterMs: Long = 0L,
    )

    data class BBox(
        val top: Double,
        val bottom: Double,
        val left: Double,
        val right: Double,
    )

    /** Search bound; live warning distance is speed-adjusted by RoadReportPolicy. */
    const val ALERT_RADIUS_M = 4_000.0
    const val BOX_RADIUS_M = 5_000.0
    const val POLICE_REPORT_TTL_MS = 20 * 60_000L
    const val HAZARD_REPORT_TTL_MS = 60 * 60_000L

    fun bboxAround(lat: Double, lon: Double, radiusM: Double = BOX_RADIUS_M): BBox {
        val dLat = radiusM / 111_320.0
        val dLon = radiusM / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.2))
        return BBox(
            top = lat + dLat,
            bottom = lat - dLat,
            left = lon - dLon,
            right = lon + dLon,
        )
    }

    /**
     * Waze splits the world into three live-map shards. Wrong [env] returns
     * an empty set, not an error.
     */
    fun wazeEnv(lat: Double, lon: Double): String = when {
        lat in 29.0..34.0 && lon in 34.0..36.2 -> "il"
        lat in 14.0..72.0 && lon in -170.0..-50.0 -> "na"
        else -> "row"
    }

    fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dP = Math.toRadians(lat2 - lat1)
        val dL = Math.toRadians(lon2 - lon1)
        val a = sin(dP / 2).pow(2) + cos(p1) * cos(p2) * sin(dL / 2).pow(2)
        return 2 * r * asin(min(1.0, sqrt(a)))
    }

    fun formatDistance(meters: Double): String {
        if (meters < 160) return "within 500 ft"
        val miles = meters / 1609.34
        return if (miles < 0.95) {
            "${"%.1f".format(miles)} mi"
        } else {
            "${miles.roundToInt()} mi"
        }
    }

    fun toAlert(hit: Hit, originLat: Double, originLon: Double, nowMs: Long = System.currentTimeMillis()): RoadAlert {
        val meters = metersBetween(originLat, originLon, hit.lat, hit.lon)
        val label = when (hit.kind) {
            RoadAlert.Kind.POLICE -> "Police report nearby"
            RoadAlert.Kind.CAMERA -> "Mapped camera nearby"
            RoadAlert.Kind.HAZARD -> "Road hazard report nearby"
        }
        val where = hit.street?.takeIf { it.isNotBlank() }
        val summary = buildString {
            append(label)
            append(' ')
            append(formatDistance(meters))
            if (where != null) append(" — ").append(where)
            append(" (").append(hit.source)
            if (hit.kind != RoadAlert.Kind.CAMERA) {
                val age = hit.reportedAtMs?.let { ((nowMs - it).coerceAtLeast(0L) / 60_000L) }
                append(if (age == null) "; report age unknown" else if (hit.source == "AZ511 work zones") "; feed updated ${age} min ago" else "; ${age} min ago")
            }
            append(')')
        }
        return RoadAlert(
            kind = hit.kind,
            summary = summary,
            source = hit.source,
            atMs = nowMs,
            lat = hit.lat,
            lon = hit.lon,
            distanceMeters = meters.roundToInt(),
        )
    }

    fun pickNearest(
        hits: List<Hit>,
        originLat: Double,
        originLon: Double,
        radiusM: Double = ALERT_RADIUS_M,
        seen: Set<String> = emptySet(),
        headingDegrees: Double? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): Hit? {
        if (!validCoordinates(originLat, originLon)) return null
        val ranked = hits
            .filter { validCoordinates(it.lat, it.lon) && reportIsCurrent(it, nowMs) }
            .map { it to metersBetween(originLat, originLon, it.lat, it.lon) }
            .filter { it.second <= radiusM }
            .filter { it.first.key() !in seen }
            .filter { headingDegrees == null || !headingDegrees.isFinite() ||
                inTravelCorridor(originLat, originLon, it.first, it.second, headingDegrees) }
            .sortedWith(
                compareBy<Pair<Hit, Double>> { it.second }
                    .thenBy { kindRank(it.first.kind) },
            )
        return ranked.firstOrNull()?.first
    }

    fun validCoordinates(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

    fun reportIsCurrent(hit: Hit, nowMs: Long): Boolean {
        if (hit.validUntilMs?.let { nowMs >= it } == true) return false
        val at = hit.reportedAtMs ?: return true
        if (at <= 0L || at > nowMs + 60_000L) return false
        val ttl = when (hit.kind) {
            RoadAlert.Kind.POLICE -> POLICE_REPORT_TTL_MS
            RoadAlert.Kind.HAZARD -> HAZARD_REPORT_TTL_MS
            RoadAlert.Kind.CAMERA -> return true
        }
        return nowMs - at <= ttl
    }

    /** A heading can reject passed/off-axis reports, but cannot prove same-road
     * relevance without a routed road graph. Copy continues to say nearby. */
    private fun inTravelCorridor(lat: Double, lon: Double, hit: Hit, meters: Double, heading: Double): Boolean {
        if (meters <= 60.0) return true
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(hit.lat)
        val dLon = Math.toRadians(hit.lon - lon)
        val bearing = Math.toDegrees(atan2(sin(dLon) * cos(p2),
            cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)))
        val delta = Math.toRadians(((bearing - heading + 540.0) % 360.0 + 360.0) % 360.0 - 180.0)
        return cos(delta) > 0.0 && abs(Math.toDegrees(delta)) <= 60.0 &&
            abs(sin(delta) * meters) <= 250.0
    }

    fun parseWaze(body: String): List<Hit> {
        if (body.isBlank() || body.trimStart().startsWith('<')) return emptyList()
        return try {
            val trimmed = body.trimStart()
            val out = mutableListOf<Hit>()
            if (trimmed.startsWith('[')) {
                collectAlerts(JSONArray(trimmed), out)
            } else {
                val root = JSONObject(trimmed)
                collectAlerts(root.optJSONArray("alerts"), out)
                collectCameras(root.optJSONArray("cameras"), out)
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "waze parse failed: ${e.message}")
            emptyList()
        }
    }

    fun parseOsm(body: String): List<Hit> {
        if (body.isBlank() || body.trimStart().startsWith('<')) return emptyList()
        return try {
            val els = JSONObject(body).optJSONArray("elements") ?: return emptyList()
            val out = mutableListOf<Hit>()
            for (i in 0 until els.length()) {
                val n = els.optJSONObject(i) ?: continue
                val lat = n.optDouble("lat", Double.NaN)
                val lon = n.optDouble("lon", Double.NaN)
                if (!validCoordinates(lat, lon)) continue
                val tags = n.optJSONObject("tags")
                if (tags == null || !(tags.optString("highway") == "speed_camera" ||
                        tags.optString("enforcement") == "maxspeed" ||
                        tags.optString("camera:type") == "speed")) continue
                val speed = tags?.optString("maxspeed").orEmpty().ifBlank { null }
                val id = n.opt("id")?.toString() ?: "$lat,$lon"
                out += Hit(
                    kind = RoadAlert.Kind.CAMERA,
                    lat = lat,
                    lon = lon,
                    street = speed?.let { "$it zone" },
                    detail = speed,
                    source = "OSM",
                    id = "osm:$id",
                )
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "osm parse failed: ${e.message}")
            emptyList()
        }
    }

    fun wazeUrl(lat: Double, lon: Double, env: String = wazeEnv(lat, lon)): String {
        val box = bboxAround(lat, lon)
        return "https://www.waze.com/live-map/api/georss" +
            "?top=${box.top}&bottom=${box.bottom}" +
            "&left=${box.left}&right=${box.right}" +
            "&env=$env&types=alerts,traffic"
    }

    fun fetchNearby(lat: Double, lon: Double): List<Hit> {
        val waze = fetchWaze(lat, lon)
        val osm = fetchOsm(lat, lon)
        return waze + osm
    }

    internal fun fetchWaze(lat: Double, lon: Double): List<Hit> = fetchWazeResult(lat, lon).hits

    private val wazeProvider = WazeProvider(::httpGet)

    internal fun fetchWazeResult(lat: Double, lon: Double): FetchResult = wazeProvider.fetch(lat, lon)

    /** The retired TGeoRSS fallback returned 404 and hid the original 403 from
     * the live map. Do not probe old shards or retry an access denial rapidly.
     * Cache failures across poller restarts; successful reports remain GPS bound. */
    internal class WazeProvider(
        private val request: (String, Map<String, String>) -> Response,
        private val nowMs: () -> Long = System::currentTimeMillis,
    ) {
        private var failure: FetchResult? = null

        @Synchronized
        fun fetch(lat: Double, lon: Double): FetchResult {
            val now = nowMs()
            if (!validCoordinates(lat, lon)) return FetchResult("Waze", available = false,
                detail = "A valid location is required", fetchedAtMs = now)
            failure?.takeIf { now - it.fetchedAtMs in 0 until it.retryAfterMs }?.let { return it }
            val response = request(wazeUrl(lat, lon), mapOf(
                "User-Agent" to ROAD_UA,
                "Accept" to "application/json",
            ))
            val body = response.body
            if (response.statusCode?.let { it !in 200..299 } != true && body != null && hasWazePayload(body)) {
                failure = null
                return FetchResult("Waze", parseWaze(body), available = true, fetchedAtMs = nowMs())
            }
            return failedResponse("Waze", response, nowMs()).also { failure = it }
        }
    }

    internal fun failedResponse(source: String, response: Response, nowMs: Long): FetchResult {
        val code = response.statusCode
        val detail = when (code) {
            401, 403 -> if (source == "Waze")
                "Live-map access denied (HTTP $code). Use Waze navigation for live reports; supported feed access requires an approved provider connection"
                else "Provider denied access (HTTP $code)"
            404, 410 -> "Provider endpoint unavailable (HTTP $code)" + if (source == "Waze")
                ". Use Waze navigation for live reports" else ""
            429 -> "Provider rate limit (HTTP 429); retrying later"
            null -> response.error ?: "Invalid response"
            in 200..299 -> "Provider returned an invalid report payload"
            else -> "HTTP $code"
        }
        val waitMs = when (code) {
            401, 403, 404, 410 -> 30 * 60_000L
            429 -> 5 * 60_000L
            else -> 45_000L
        }
        return FetchResult(source, available = false, detail = detail, fetchedAtMs = nowMs,
            retryAfterMs = maxOf(waitMs, response.retryAfterMs).coerceAtMost(24 * 60 * 60_000L))
    }

    internal fun fetchOsm(lat: Double, lon: Double): List<Hit> = fetchOsmResult(lat, lon).hits

    internal fun fetchOsmResult(lat: Double, lon: Double): FetchResult {
        if (!validCoordinates(lat, lon)) return FetchResult("OSM", available = false,
            detail = "A valid location is required")
        val box = bboxAround(lat, lon)
        val query = """
            [out:json][timeout:12];
            (
              node["highway"="speed_camera"](${box.bottom},${box.left},${box.top},${box.right});
              node["enforcement"="maxspeed"](${box.bottom},${box.left},${box.top},${box.right});
              node["camera:type"="speed"](${box.bottom},${box.left},${box.top},${box.right});
            );
            out body;
        """.trimIndent()
        val form = "data=" + URLEncoder.encode(query, Charsets.UTF_8.name())
        var failure = FetchResult("OSM", available = false, detail = "Unavailable")
        for (host in OSM_HOSTS) {
            val response = httpPost(
                "$host/api/interpreter",
                form,
                mapOf(
                    "User-Agent" to OSM_UA,
                    "Accept" to "application/json",
                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                ),
            )
            val body = response.body
            if (body != null && hasOsmPayload(body)) {
                return FetchResult("OSM", parseOsm(body), available = true)
            }
            val current = failedResponse("OSM", response, System.currentTimeMillis())
            // Respect the longest server backoff if every independent host fails.
            failure = current.copy(retryAfterMs = maxOf(failure.retryAfterMs, current.retryAfterMs))
        }
        return failure
    }

    internal fun hasWazePayload(body: String): Boolean = runCatching {
        if (body.trimStart().startsWith('[')) { JSONArray(body); true }
        else JSONObject(body).let { it.optJSONArray("alerts") != null || it.optJSONArray("cameras") != null }
    }.getOrDefault(false)

    internal fun hasOsmPayload(body: String): Boolean = runCatching {
        JSONObject(body).let { it.optJSONArray("elements") != null && !it.has("remark") }
    }.getOrDefault(false)

    private fun collectAlerts(arr: JSONArray?, out: MutableList<Hit>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = o.optString("type").uppercase()
            val kind = when (type) {
                "POLICE" -> RoadAlert.Kind.POLICE
                "CAMERA" -> RoadAlert.Kind.CAMERA
                "ACCIDENT", "HAZARD", "ROAD_CLOSED" -> RoadAlert.Kind.HAZARD
                else -> continue
            }
            val loc = o.optJSONObject("location")
            val lon = firstDouble(o, loc, "x", "lon", "longitude") ?: continue
            val lat = firstDouble(o, loc, "y", "lat", "latitude") ?: continue
            if (!validCoordinates(lat, lon)) continue
            val street = o.optString("street").ifBlank { null }
            val subtype = o.optString("subtype").ifBlank { null }
            val id = o.optString("uuid").ifBlank {
                o.optString("id").ifBlank { "$type:$lat:$lon" }
            }
            out += Hit(
                kind = kind,
                lat = lat,
                lon = lon,
                street = street,
                detail = subtype,
                source = "Waze",
                id = "waze:$id",
                reportedAtMs = o.optLong("pubMillis", 0L).takeIf { it > 0L },
            )
        }
    }

    private fun collectCameras(arr: JSONArray?, out: MutableList<Hit>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val loc = o.optJSONObject("location")
            val lon = firstDouble(o, loc, "x", "lon", "longitude") ?: continue
            val lat = firstDouble(o, loc, "y", "lat", "latitude") ?: continue
            if (!validCoordinates(lat, lon)) continue
            val speed = o.opt("speed")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            val id = o.opt("id")?.toString() ?: "$lat,$lon"
            out += Hit(
                kind = RoadAlert.Kind.CAMERA,
                lat = lat,
                lon = lon,
                // The unofficial response supplies no dependable speed unit.
                street = null,
                detail = speed,
                source = "Waze",
                id = "waze-cam:$id",
            )
        }
    }

    private fun firstDouble(obj: JSONObject, loc: JSONObject?, vararg keys: String): Double? {
        for (k in keys) {
            val fromLoc = loc?.optDouble(k, Double.NaN)
            if (fromLoc != null && !fromLoc.isNaN()) return fromLoc
            val fromObj = obj.optDouble(k, Double.NaN)
            if (!fromObj.isNaN()) return fromObj
        }
        return null
    }

    private fun kindRank(kind: RoadAlert.Kind): Int = when (kind) {
        RoadAlert.Kind.POLICE -> 0
        RoadAlert.Kind.CAMERA -> 1
        RoadAlert.Kind.HAZARD -> 2
    }

    private fun httpGet(url: String, headers: Map<String, String>): Response {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 8_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Road provider GET HTTP $code")
                return Response(statusCode = code, retryAfterMs = retryAfterMs(conn.getHeaderField("Retry-After")))
            }
            Response(body = conn.inputStream.bufferedReader().use { it.readText() }, statusCode = code)
        } catch (e: Exception) {
            Log.w(TAG, "Road provider GET failed: ${e.javaClass.simpleName}")
            Response(error = e.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    private fun httpPost(url: String, body: String, headers: Map<String, String>): Response {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 10_000
                requestMethod = "POST"
                doOutput = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Road provider POST HTTP $code")
                return Response(statusCode = code, retryAfterMs = retryAfterMs(conn.getHeaderField("Retry-After")))
            }
            Response(body = conn.inputStream.bufferedReader().use { it.readText() }, statusCode = code)
        } catch (e: Exception) {
            Log.w(TAG, "Road provider POST failed: ${e.javaClass.simpleName}")
            Response(error = e.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    private const val TAG = "RoadFeed"
    private const val ROAD_UA = "Meowmba/1.0 (road-alerts)"
    private const val OSM_UA = ROAD_UA
    internal val OSM_HOSTS = listOf(
        // Kumi and Private.coffee are now the same operator/instance.
        "https://overpass.private.coffee",
        "https://overpass-api.de",
    )

    internal fun retryAfterMs(value: String?, nowMs: Long = System.currentTimeMillis()): Long {
        val limit = 24 * 60 * 60_000L
        value?.trim()?.toLongOrNull()?.let { return it.coerceIn(0L, limit / 1_000L) * 1_000L }
        return runCatching {
            val until = java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
            (until - nowMs).coerceIn(0L, limit)
        }.getOrDefault(0L)
    }
}
