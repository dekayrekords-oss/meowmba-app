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
 * Plain SharedPreferences rather than DataStore or Room: this app has neither a
 * DataStore dependency nor a reason to gain one for a handful of coordinates, and
 * putting them in Room would mean a schema migration. Nothing here touches the
 * network, so saved-place navigation works with no server and no signal.
 */
class SavedPlaceStore(private val context: Context) {

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

    fun save(name: String, lat: Double, lon: Double) {
        val clean = name.trim().lowercase()
        if (clean.isBlank()) return
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
        prefs.edit().putString(KEY, arr.toString()).apply()
        _places.value = list.sortedBy { it.name }
    }

    private fun read(): List<SavedPlace> {
        val raw = prefs.getString(KEY, null)
        if (raw.isNullOrBlank()) return emptyList()
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
    }
}
