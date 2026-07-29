package com.example.meowmba.core.nav

/**
 * Somewhere we can look up places. Implementations must be safe to call from a
 * coroutine on Dispatchers.IO and must never throw — return an empty list instead,
 * so a dead network or a missing region pack degrades rather than crashes.
 */
interface PlaceSource {

    val kind: PlaceSourceKind

    /** True if this source can answer right now (pack installed / key configured). */
    fun isAvailable(): Boolean

    /**
     * Places matching a business name, nearest first.
     * @param radiusMeters search radius around ([lat], [lon]).
     */
    suspend fun searchByName(
        name: String,
        lat: Double,
        lon: Double,
        radiusMeters: Int,
        limit: Int,
    ): List<Place>

    /**
     * Places in any of [categories], nearest first.
     * Category ids follow Geoapify's OSM-derived taxonomy.
     */
    suspend fun searchByCategory(
        categories: List<String>,
        lat: Double,
        lon: Double,
        radiusMeters: Int,
        limit: Int,
    ): List<Place>
}

/** Great-circle distance in metres. */
internal fun haversineMeters(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double,
): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}
