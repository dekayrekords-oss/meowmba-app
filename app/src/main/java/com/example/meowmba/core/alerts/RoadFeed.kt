package com.example.meowmba.core.alerts

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.asin
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
 * This feed uses the phone's GPS and the same live-map data the Waze website
 * shows anyone — no route, no notification access.
 *
 * The Waze live-map endpoint is unofficial and can 403. OSM speed cameras
 * still work when it does. Failures never throw.
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
    ) {
        fun key(): String =
            "$kind:${(lat * 1000).roundToInt()}:${(lon * 1000).roundToInt()}"
    }

    data class BBox(
        val top: Double,
        val bottom: Double,
        val left: Double,
        val right: Double,
    )

    /** ~2.5 mi — highway at 70 mph covers 1.6 km in one 45s poll. */
    const val ALERT_RADIUS_M = 4_000.0
    const val BOX_RADIUS_M = 5_000.0

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
        if (meters < 160) return "just ahead"
        val miles = meters / 1609.34
        return if (miles < 0.95) {
            "${"%.1f".format(miles)} mi"
        } else {
            "${miles.roundToInt()} mi"
        }
    }

    fun toAlert(hit: Hit, originLat: Double, originLon: Double): RoadAlert {
        val meters = metersBetween(originLat, originLon, hit.lat, hit.lon)
        val label = when (hit.kind) {
            RoadAlert.Kind.POLICE -> "Police"
            RoadAlert.Kind.CAMERA -> "Speed camera"
            RoadAlert.Kind.HAZARD -> "Road hazard"
        }
        val where = hit.street?.takeIf { it.isNotBlank() }
        val summary = buildString {
            append(label)
            append(' ')
            append(formatDistance(meters))
            if (where != null) append(" — ").append(where)
        }
        return RoadAlert(
            kind = hit.kind,
            summary = summary,
            source = hit.source,
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
    ): Hit? {
        val ranked = hits
            .map { it to metersBetween(originLat, originLon, it.lat, it.lon) }
            .filter { it.second <= radiusM }
            .filter { it.first.key() !in seen }
            .sortedWith(
                compareBy<Pair<Hit, Double>> { kindRank(it.first.kind) }
                    .thenBy { it.second },
            )
        return ranked.firstOrNull()?.first
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
                if (lat.isNaN() || lon.isNaN()) continue
                val tags = n.optJSONObject("tags")
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

    fun wazeRtUrl(lat: Double, lon: Double, env: String = wazeEnv(lat, lon)): String {
        val box = bboxAround(lat, lon)
        val shard = when (env) {
            "il" -> "il-rtserver"
            "na" -> "rtserver"
            else -> "row-rtserver"
        }
        return "https://www.waze.com/$shard/web/TGeoRSS" +
            "?top=${box.top}&bottom=${box.bottom}" +
            "&left=${box.left}&right=${box.right}" +
            "&ma=200&mj=20&mu=10&types=alerts"
    }

    fun fetchNearby(lat: Double, lon: Double): List<Hit> {
        val waze = fetchWaze(lat, lon)
        val osm = fetchOsm(lat, lon)
        return waze + osm
    }

    internal fun fetchWaze(lat: Double, lon: Double): List<Hit> {
        val headers = mapOf(
            "User-Agent" to BROWSER_UA,
            "Accept" to "application/json,text/plain,*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Referer" to "https://www.waze.com/live-map/",
            "Origin" to "https://www.waze.com",
        )
        val primary = wazeEnv(lat, lon)
        val envs = listOf(primary) + listOf("na", "row", "il").filter { it != primary }
        for (env in envs) {
            val body = httpGet(wazeUrl(lat, lon, env), headers) ?: continue
            val hits = parseWaze(body)
            if (hits.isNotEmpty()) return hits
        }
        // live-map/georss 403s from some networks. The older rtserver
        // path is the same data and often still answers.
        for (env in envs) {
            val body = httpGet(wazeRtUrl(lat, lon, env), headers) ?: continue
            val hits = parseWaze(body)
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }

    internal fun fetchOsm(lat: Double, lon: Double): List<Hit> {
        val box = bboxAround(lat, lon)
        val query = """
            [out:json][timeout:12];
            (
              node["highway"="speed_camera"](${box.bottom},${box.left},${box.top},${box.right});
              node["enforcement"="maxspeed"](${box.bottom},${box.left},${box.top},${box.right});
              node["camera:type"="speed"](${box.bottom},${box.left},${box.top},${box.right});
            );
            out body 40;
        """.trimIndent()
        val form = "data=" + URLEncoder.encode(query, Charsets.UTF_8.name())
        for (host in OSM_HOSTS) {
            val body = httpPost(
                "$host/api/interpreter",
                form,
                mapOf(
                    "User-Agent" to OSM_UA,
                    "Accept" to "application/json",
                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                ),
            )
            if (body != null) return parseOsm(body)
        }
        return emptyList()
    }

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
            val speed = o.opt("speed")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            val id = o.opt("id")?.toString() ?: "$lat,$lon"
            out += Hit(
                kind = RoadAlert.Kind.CAMERA,
                lat = lat,
                lon = lon,
                street = speed?.let { "$it mph cam" },
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

    private fun httpGet(url: String, headers: Map<String, String>): String? {
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
                Log.w(TAG, "GET $code $url")
                return null
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "GET failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun httpPost(url: String, body: String, headers: Map<String, String>): String? {
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
                Log.w(TAG, "POST $code $url")
                return null
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "POST failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private const val TAG = "RoadFeed"
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Mobile Safari/537.36"
    private const val OSM_UA = "Meowmba/1.0 (road-alerts)"
    private val OSM_HOSTS = listOf(
        "https://overpass.kumi.systems",
        "https://overpass-api.de",
        "https://overpass.private.coffee",
    )
}
