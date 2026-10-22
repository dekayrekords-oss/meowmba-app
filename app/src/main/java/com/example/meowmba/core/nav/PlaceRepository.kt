package com.example.meowmba.core.nav

/**
 * Offline-first place lookup.
 *
 * Order is deliberate: the offline OSM pack is tried first, because the goal is a
 * Meowmba that works standalone. Geoapify is a top-up for when the pack is missing,
 * out of date, or simply has no hit — not the primary path.
 */
class PlaceRepository(
    private val offline: PlaceSource,
    private val online: PlaceSource?,
) {

    data class Result(
        val places: List<Place>,
        val usedSource: PlaceSourceKind?,
        /** True when nothing could answer at all (no pack, no key, no network). */
        val noSourceAvailable: Boolean = false,
    )

    /** A store-identification lookup must not expand into a 25-mile navigation search. */
    suspend fun nearbyShops(lat: Double, lon: Double, radiusMeters: Int = 200): Result {
        val sources = listOfNotNull(offline, online).filter { it.isAvailable() }
        if (sources.isEmpty()) return Result(emptyList(), null, noSourceAvailable = true)
        for (source in sources) {
            val hits = source.searchByCategory(listOf("commercial"), lat, lon, radiusMeters, 30)
                .filter { place ->
                    place.lat.isFinite() && place.lon.isFinite() &&
                        place.lat in -90.0..90.0 && place.lon in -180.0..180.0 &&
                        (place.category == "commercial" || place.category?.startsWith("commercial.") == true) &&
                        haversineMeters(lat, lon, place.lat, place.lon) <= radiusMeters
                }
            if (hits.isNotEmpty()) return Result(hits, source.kind)
        }
        return Result(emptyList(), null)
    }

    suspend fun find(
        request: NavRequest,
        lat: Double,
        lon: Double,
        radiusMeters: Int = DEFAULT_RADIUS_M,
        limit: Int = DEFAULT_LIMIT,
    ): Result {
        val sources = listOfNotNull(offline, online).filter { it.isAvailable() }
        if (sources.isEmpty()) {
            return Result(emptyList(), null, noSourceAvailable = true)
        }

        for (source in sources) {
            val hits = when (request) {
                is NavRequest.Named ->
                    source.searchByName(request.query, lat, lon, radiusMeters, limit)

                is NavRequest.Category -> {
                    val byCat = source.searchByCategory(
                        request.categories, lat, lon, radiusMeters, limit,
                    )
                    // Category tagging in OSM is uneven — a burger place may only be
                    // tagged catering.fast_food with the word in its name. Fall back to
                    // the raw phrase rather than reporting nothing.
                    byCat.ifEmpty {
                        source.searchByName(request.freeText, lat, lon, radiusMeters, limit)
                    }
                }

                is NavRequest.Saved -> emptyList()   // handled by the caller's saved places
            }
            if (hits.isNotEmpty()) return Result(hits, source.kind)
        }

        // Widen once before giving up — rural areas legitimately have nothing within
        // the default radius.
        if (radiusMeters < WIDE_RADIUS_M) {
            return find(request, lat, lon, WIDE_RADIUS_M, limit)
        }
        return Result(emptyList(), null)
    }

    companion object {
        const val DEFAULT_RADIUS_M = 8_000     // ~5 mi
        const val WIDE_RADIUS_M = 40_000       // ~25 mi
        const val DEFAULT_LIMIT = 8
    }
}
