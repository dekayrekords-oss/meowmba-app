package com.example.meowmba.core.nav

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Places the user has taught the companion ("home", "work", "mom's").
 *
 * One encrypted source shared by voice, tools and the NAV card. Older voice
 * and NAV preference formats migrate once; deletion cannot resurrect a stale
 * duplicate from another store. Saved navigation works without a network.
 */
class SavedPlaceStore(private val context: Context, suppliedStorage: com.example.meowmba.core.security.SecureStorageManager? = null) {
    private val storage by lazy { suppliedStorage ?: com.example.meowmba.core.security.SecureStorageManager(context) }

    // Lazy on purpose: constructing this must not touch Context or disk. It is built
    // eagerly inside CompanionViewModel's init, and doing I/O there NPE'd every test
    // that constructs the ViewModel with a mock Context.
    private val prefs by lazy {
        context.applicationContext.getSharedPreferences("nav_saved_places", Context.MODE_PRIVATE)
    }

    data class SavedPlace(
        val name: String,
        val lat: Double,
        val lon: Double,
        val savedAt: Long,
    ) {
        fun toPlace() = Place(
            name = name,
            lat = lat,
            lon = lon,
            source = PlaceSourceKind.SAVED,
        )
    }

    private val _places by lazy { MutableStateFlow(read()) }
    val places: StateFlow<List<SavedPlace>> get() = _places.asStateFlow()

    fun all(): List<SavedPlace> = read()

    fun save(name: String, lat: Double, lon: Double) {
        require(com.example.meowmba.core.alerts.RoadFeed.validCoordinates(lat, lon))
        val clean = name.trim().lowercase()
        if (clean.isBlank()) return
        require(clean.length <= 80)
        // Re-saving a name overwrites it: "this is my home" should move home, not add
        // a second one.
        val next = read().filterNot { it.name == clean } +
            SavedPlace(clean, lat, lon, System.currentTimeMillis())
        write(next)
    }

    fun delete(name: String) {
        val clean = name.trim().lowercase()
        write(read().filterNot { it.name == clean })
    }

    fun get(name: String): SavedPlace? {
        val clean = name.trim().lowercase()
        return read().firstOrNull { it.name == clean }
    }

    /** Names for [NavRequestParser], so it recognises "take me to mom's". */
    fun names(): Set<String> = read().map { it.name }.toSet()

    private fun write(list: List<SavedPlace>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("name", p.name)
                    put("lat", p.lat)
                    put("lon", p.lon)
                    put("savedAt", p.savedAt)
                }
            )
        }
        storage.savePreference(SECURE_KEY, arr.toString())
        _places.value = list.sortedBy { it.name }
    }

    private fun read(): List<SavedPlace> {
        var raw = storage.getPreference(SECURE_KEY)
        if (raw == null) {
            // Both historical stores now migrate into one encrypted source.
            // Keep legacy data intact if parsing fails; never clear on failure.
            val nav = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
            val voice = JSONObject(storage.getPreference("named_places") ?: "{}")
            val names = (0 until nav.length()).map { nav.getJSONObject(it).getString("name").lowercase() }.toSet()
            voice.keys().asSequence().filter { it.lowercase() !in names }.forEach { name ->
                val parts = voice.getString(name).split(",")
                val lat = parts[0].toDouble(); val lon = parts[1].toDouble()
                if (com.example.meowmba.core.alerts.RoadFeed.validCoordinates(lat, lon)) nav.put(JSONObject()
                    .put("name", name.lowercase()).put("lat", lat).put("lon", lon).put("savedAt", 0L))
            }
            raw = nav.toString()
            storage.savePreference(SECURE_KEY, raw)
            // Leaving old stores inert avoids any cross-file migration data-loss
            // window. All reads, saves and deletes below use SECURE_KEY only.
        }
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val lat = o.optDouble("lat", Double.NaN).takeIf { !it.isNaN() }
                    ?: return@mapNotNull null
                val lon = o.optDouble("lon", Double.NaN).takeIf { !it.isNaN() }
                    ?: return@mapNotNull null
                SavedPlace(name, lat, lon, o.optLong("savedAt", 0L))
            }.sortedBy { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val KEY = "places"
        const val SECURE_KEY = "saved_places_v2"
    }
}
