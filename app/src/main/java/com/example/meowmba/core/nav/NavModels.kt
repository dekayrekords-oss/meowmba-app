package com.example.meowmba.core.nav

/**
 * A place we could navigate to, from any source (offline OSM pack, Geoapify, or a
 * saved known place).
 */
data class Place(
    val name: String,
    val lat: Double,
    val lon: Double,
    /** Street address if the source gave us one. */
    val address: String? = null,
    /** OSM-style category, e.g. "catering.fast_food.burger". Null for saved places. */
    val category: String? = null,
    /** Metres from the user. Filled in by the repository, not the source. */
    var distanceMeters: Double? = null,
    val source: PlaceSourceKind = PlaceSourceKind.OFFLINE,
) {
    /** "0.4 mi" / "320 ft" — US units, since that is where this phone lives. */
    fun distanceLabel(): String? {
        val m = distanceMeters ?: return null
        val miles = m / 1609.344
        return if (miles < 0.2) "${(m * 3.28084).toInt()} ft"
        else String.format("%.1f mi", miles)
    }
}

enum class PlaceSourceKind { OFFLINE, GEOAPIFY, SAVED }

/**
 * What the user asked for, as understood on-device.
 *
 * The distinction that drives the UX is [isCategory]:
 *  - "take me to Circle K"      -> isCategory = false -> navigate straight to nearest
 *  - "take me to get a burger"  -> isCategory = true  -> show a list, ask which
 */
sealed interface NavRequest {

    /** A specific named business or brand — "Circle K", "Starbucks", "QuikTrip". */
    data class Named(val query: String) : NavRequest

    /** A vague category — "a burger", "coffee", "gas". */
    data class Category(
        /** Human-facing word, for speech: "burger". */
        val label: String,
        /** Geoapify/OSM category ids to search. */
        val categories: List<String>,
        /** Free-text fallback if the category search comes back empty. */
        val freeText: String,
    ) : NavRequest

    /** A place the user previously saved — "home", "work", "mom's". */
    data class Saved(val placeName: String) : NavRequest
}
