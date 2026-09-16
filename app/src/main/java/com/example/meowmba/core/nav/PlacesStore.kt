package com.example.meowmba.core.nav

import com.example.meowmba.core.security.SecureStorageManager

/** Compatibility facade: voice and NAV must use the same encrypted records. */
class PlacesStore(storage: SecureStorageManager) {
    data class Place(val lat: Double, val lon: Double)
    private val places = SavedPlaceStore(storage.context, storage)
    fun save(name: String, lat: Double, lon: Double) = places.save(name, lat, lon)
    fun find(name: String): Place? = places.get(name)?.let { Place(it.lat, it.lon) }
    fun all(): Map<String, Place> = places.all().associate { it.name to Place(it.lat, it.lon) }
}
