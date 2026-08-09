package com.example.meowmba.core.nav

import com.example.meowmba.core.security.SecureStorageManager
import org.json.JSONObject

/**
 * Named GPS locations ("remember this as my mailbox", "where is my mailbox").
 *
 * Backed by the encrypted preferences store as a small JSON map of
 * name -> "lat,lon" so no Room migration is needed. Names are canonicalised
 * (lowercased, trimmed) for lookup.
 */
class PlacesStore(private val storage: SecureStorageManager) {

    data class Place(val lat: Double, val lon: Double)

    private val key = "named_places"

    fun save(name: String, lat: Double, lon: Double) {
        val map = all().toMutableMap()
        map[canonical(name)] = Place(lat, lon)
        val json = JSONObject()
        map.forEach { (n, p) -> json.put(n, "${p.lat},${p.lon}") }
        storage.savePreference(key, json.toString())
    }

    fun find(name: String): Place? = all()[canonical(name)]

    fun all(): Map<String, Place> {
        val raw = storage.getPreference(key) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { k ->
                val parts = obj.getString(k).split(",")
                Place(parts[0].trim().toDouble(), parts[1].trim().toDouble())
            }
        }.getOrDefault(emptyMap())
    }

    private fun canonical(name: String): String = name.trim().lowercase()
}
