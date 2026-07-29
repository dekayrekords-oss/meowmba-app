package com.example.meowmba.core.nav

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Online place lookup via Geoapify's Places API — the top-up for whatever the
 * offline packs miss.
 *
 * Geoapify is OpenStreetMap-derived, which is why the same category ids work against
 * [OfflinePoiSource]. This is the online mirror of the offline pack, not a second
 * taxonomy.
 *
 * Uses HttpURLConnection to match the other tools in this app (see WebSearchTool)
 * rather than pulling Ktor into the nav layer.
 *
 * The key is supplied by the caller — typically from SecureStorageManager, so it can
 * be entered in the NAV card at runtime instead of baked into the build. With no key
 * this source reports itself unavailable and the repository falls back to offline.
 */
class GeoapifyPlaceSource(
    private val apiKeyProvider: () -> String,
) : PlaceSource {

    override val kind = PlaceSourceKind.GEOAPIFY

    override fun isAvailable(): Boolean = apiKeyProvider().isNotBlank()

    override suspend fun searchByName(
        name: String, lat: Double, lon: Double, radiusMeters: Int, limit: Int,
    ): List<Place> = query(buildUrl(lat, lon, radiusMeters, limit, name = name), lat, lon)

    override suspend fun searchByCategory(
        categories: List<String>, lat: Double, lon: Double, radiusMeters: Int, limit: Int,
    ): List<Place> = query(
        buildUrl(lat, lon, radiusMeters, limit, categories = categories), lat, lon,
    )

    private fun buildUrl(
        lat: Double, lon: Double, radiusMeters: Int, limit: Int,
        name: String? = null,
        categories: List<String>? = null,
    ): String {
        val sb = StringBuilder("https://api.geoapify.com/v2/places?")
        // `categories` is mandatory. For a pure name search we still have to supply
        // something, so cast a wide net and let `name` do the filtering.
        val cats = categories ?: NAME_SEARCH_CATEGORIES
        sb.append("categories=").append(enc(cats.joinToString(",")))
        name?.let { sb.append("&name=").append(enc(it)) }
        sb.append("&filter=circle:").append(lon).append(',').append(lat)
            .append(',').append(radiusMeters)
        sb.append("&bias=proximity:").append(lon).append(',').append(lat)
        sb.append("&limit=").append(limit)
        sb.append("&apiKey=").append(enc(apiKeyProvider()))
        return sb.toString()
    }

    private suspend fun query(url: String, lat: Double, lon: Double): List<Place> =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 8_000
                    requestMethod = "GET"
                }
                if (conn.responseCode !in 200..299) {
                    Log.w(TAG, "Geoapify HTTP ${conn.responseCode}")
                    return@withContext emptyList()
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (body.isBlank()) emptyList() else parse(body, lat, lon)
            } catch (e: Exception) {
                Log.w(TAG, "Geoapify lookup failed: ${e.message}")
                emptyList()
            } finally {
                conn?.disconnect()
            }
        }

    private fun parse(body: String, lat: Double, lon: Double): List<Place> {
        val out = mutableListOf<Place>()
        val features = JSONObject(body).optJSONArray("features") ?: return out
        for (i in 0 until features.length()) {
            val p = features.optJSONObject(i)?.optJSONObject("properties") ?: continue
            val name = p.optString("name").takeIf { it.isNotBlank() } ?: continue
            val pLat = p.optDouble("lat", Double.NaN)
            val pLon = p.optDouble("lon", Double.NaN)
            if (pLat.isNaN() || pLon.isNaN()) continue
            // `distance` only appears when a proximity bias was applied; compute our own
            // otherwise so ranking never silently degrades to API order.
            val dist = p.optDouble("distance", Double.NaN)
                .takeIf { !it.isNaN() } ?: haversineMeters(lat, lon, pLat, pLon)
            out += Place(
                name = name,
                lat = pLat,
                lon = pLon,
                address = p.optString("address_line2").takeIf { it.isNotBlank() }
                    ?: p.optString("formatted").takeIf { it.isNotBlank() },
                category = p.optJSONArray("categories")?.optString(0),
                distanceMeters = dist,
                source = PlaceSourceKind.GEOAPIFY,
            )
        }
        return out.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val TAG = "GeoapifyPlaces"

        /** Wide net for name-only searches, since `categories` is required. */
        val NAME_SEARCH_CATEGORIES = listOf(
            "commercial", "catering", "service", "accommodation", "healthcare",
        )
    }
}
