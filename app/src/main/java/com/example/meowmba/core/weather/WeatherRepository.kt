package com.example.meowmba.core.weather

import android.content.Context
import android.util.Log
import com.example.meowmba.core.nav.CurrentLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Weather from Open-Meteo.
 *
 * Chosen because it needs **no API key and no account** — the companion can answer "what's
 * the weather" out of the box, with nothing for the user to sign up for. It also takes
 * plain coordinates, which [CurrentLocation] already provides from the navigation work.
 *
 * [latest] is the last successful reading. The UI observes it so a card appears whenever
 * anything fetches weather, whether that was the agent calling the tool or the user asking
 * directly — no separate event bus needed.
 */
class WeatherRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("weather_location", Context.MODE_PRIVATE)
    @Volatile private var locationRevision = 0L
    val selectedPlace: String get() = preferences?.getString("place", "").orEmpty()
    fun setSelectedPlace(place: String) {
        locationRevision++
        preferences?.edit()?.putString("place", place.trim().take(160))?.apply()
        clear()
    }

    private val _latest = MutableStateFlow<WeatherNow?>(null)
    val latest: StateFlow<WeatherNow?> = _latest.asStateFlow()

    /** Cleared when the card is dismissed. */
    fun clear() {
        _latest.value = null
    }

    sealed interface Result {
        data class Ok(val weather: WeatherNow) : Result
        data class Failed(val reason: String) : Result
    }

    /** A spoken city needs no device-location grant. Never treat it as another tool request. */
    @Suppress("DEPRECATION")
    suspend fun fetchPlace(place: String, surface: Boolean = true): Result = withContext(Dispatchers.IO) {
        val revision = locationRevision
        if (place.length !in 2..160) return@withContext Result.Failed("Please give a city and state.")
        try {
            val matches = android.location.Geocoder(context).getFromLocationName(place, 2).orEmpty()
            if (matches.size != 1) return@withContext Result.Failed(
                "I couldn't identify one location for $place."
            )
            val match = matches.single()
            if (!match.hasLatitude() || !match.hasLongitude())
                return@withContext Result.Failed("That place has no coordinates.")
            when (val result = fetch(match.latitude, match.longitude, surface = false)) {
                is Result.Ok -> {
                    if (revision != locationRevision) return@withContext Result.Failed("Weather location changed; refresh again.")
                    val named = result.weather.copy(place = place)
                    if (surface) _latest.value = named
                    Result.Ok(named)
                }
                is Result.Failed -> result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failed("I couldn't look up that city.")
        }
    }

    /**
     * Fetches current conditions for the user's location, or [lat]/[lon] when given.
     *
     * Never throws; a failure comes back as [Result.Failed] with something worth saying out
     * loud rather than a stack trace.
     *
     * [surface] controls whether the reading is published to [latest], which is what makes
     * the card appear. Ambient mode refreshes the sky every quarter hour and must NOT pop a
     * card open each time, so it passes `false` and reads the returned [Result.Ok] instead.
     */
    suspend fun fetch(
        lat: Double? = null,
        lon: Double? = null,
        surface: Boolean = true,
    ): Result =
        withContext(Dispatchers.IO) {
            val revision = locationRevision
            if (lat == null && lon == null && selectedPlace.isNotBlank())
                return@withContext fetchPlace(selectedPlace, surface)
            val fix = if (lat != null && lon != null) {
                CurrentLocation.Fix(lat, lon)
            } else {
                CurrentLocation.fresh(context, maxAgeMs = 15 * 60_000L, maxAccuracyMeters = 10_000f) ?: return@withContext Result.Failed(
                    if (CurrentLocation.hasPermission(context)) {
                        "I don't have a location fix yet, so I can't check the weather here."
                    } else {
                        "I need location access to check the weather where you are."
                    }
                )
            }

            var conn: HttpURLConnection? = null
            try {
                val url = buildUrl(fix.lat, fix.lon)
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 8_000
                    requestMethod = "GET"
                }
                if (conn.responseCode !in 200..299) {
                    return@withContext Result.Failed("The weather service returned ${conn.responseCode}.")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = parse(body)
                    ?: return@withContext Result.Failed("I couldn't read the weather response.")
                // Logged on SUCCESS too. Only logging failures meant "no card appeared" was
                // indistinguishable from "the fetch never happened".
                Log.i(
                    TAG,
                    "weather ok: ${parsed.condition} ${parsed.temperatureF}F " +
                        if (surface) "-> card" else "-> ambient only",
                )
                if (revision != locationRevision) return@withContext Result.Failed("Weather location changed; refresh again.")
                if (surface) _latest.value = parsed
                Result.Ok(parsed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "weather fetch failed: ${e.message}")
                Result.Failed("I couldn't reach the weather service.")
            } finally {
                conn?.disconnect()
            }
        }

    private fun buildUrl(lat: Double, lon: Double) = buildString {
        append("https://api.open-meteo.com/v1/forecast")
        append("?latitude=").append(lat)
        append("&longitude=").append(lon)
        append("&current=temperature_2m,relative_humidity_2m,apparent_temperature,")
        append("weather_code,wind_speed_10m,is_day")
        append("&daily=temperature_2m_max,temperature_2m_min")
        append("&temperature_unit=fahrenheit&wind_speed_unit=mph")
        // timezone=auto matters: the daily high/low must be for the USER's day, not UTC's.
        append("&timezone=auto&forecast_days=1")
    }

    private fun parse(body: String): WeatherNow? {
        return try {
        val root = JSONObject(body)
        val cur = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")

        fun firstOf(name: String): Double =
            daily.getJSONArray(name).optDouble(0, Double.NaN)

        val temp = cur.optDouble("temperature_2m", Double.NaN)
        if (temp.isNaN()) return null

        val high = firstOf("temperature_2m_max")
        val low = firstOf("temperature_2m_min")

        WeatherNow(
            condition = WeatherCondition.fromWmoCode(cur.optInt("weather_code", -1)),
            temperatureF = Math.round(temp).toInt(),
            feelsLikeF = Math.round(
                cur.optDouble("apparent_temperature", temp).takeIf { !it.isNaN() } ?: temp
            ).toInt(),
            // Fall back to the current reading rather than showing a nonsense 0° high.
            highF = Math.round(if (high.isNaN()) temp else high).toInt(),
            lowF = Math.round(if (low.isNaN()) temp else low).toInt(),
            humidityPct = cur.optInt("relative_humidity_2m", 0),
            windMph = Math.round(cur.optDouble("wind_speed_10m", 0.0)).toInt(),
            isDay = cur.optInt("is_day", 1) == 1,
            place = root.optString("timezone").takeIf { it.isNotBlank() }
                ?.substringAfterLast('/')?.replace('_', ' '),
        )
        } catch (e: Exception) {
            Log.w(TAG, "weather parse failed: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "Weather"
    }
}
