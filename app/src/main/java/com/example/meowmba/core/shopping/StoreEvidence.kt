package com.example.meowmba.core.shopping

import com.example.meowmba.core.nav.CurrentLocation
import com.example.meowmba.core.nav.Place
import com.example.meowmba.core.nav.haversineMeters

/** Proximity is evidence of being NEAR a shop, never proof of being inside. No persisted tracking. */
sealed interface StoreEvidence {
    data class Likely(val place: Place) : StoreEvidence
    data class Ambiguous(val candidates: List<Place>) : StoreEvidence
    data class Unknown(val reason: String) : StoreEvidence

    companion object {
        const val RADIUS_METERS = 200
        const val MAX_AGE_MS = 30_000L
        const val MAX_ACCURACY_METERS = 60f

        fun unusable(fix: CurrentLocation.Fix?, nowMs: Long): String? = when {
            fix == null -> "I don't have a recent, precise location fix."
            !fix.lat.isFinite() || !fix.lon.isFinite() || fix.lat !in -90.0..90.0 || fix.lon !in -180.0..180.0 ->
                "The location fix isn't usable."
            nowMs - fix.atMs !in -2_000L..MAX_AGE_MS -> "The location fix is too old to identify your current store."
            fix.accuracyMeters?.let { it.isFinite() && it in 0f..MAX_ACCURACY_METERS } != true ->
                "The location is too approximate to distinguish nearby stores."
            fix.speedMps?.let { it.isFinite() && it > 2.8f } == true ->
                "You appear to be moving; passing a store doesn't mean you're shopping there."
            else -> null
        }

        fun resolve(fix: CurrentLocation.Fix?, places: List<Place>, nowMs: Long): StoreEvidence {
            unusable(fix, nowMs)?.let { return Unknown(it) }
            requireNotNull(fix)
            val accuracy = requireNotNull(fix.accuracyMeters).toDouble()
            val candidates = places.asSequence().filter {
                ShoppingRequest.validTerm(it.name) && it.lat.isFinite() && it.lon.isFinite() &&
                    it.lat in -90.0..90.0 && it.lon in -180.0..180.0 &&
                    (it.category == "commercial" || it.category?.startsWith("commercial.") == true)
            }.map { it.copy(distanceMeters = haversineMeters(fix.lat, fix.lon, it.lat, it.lon)) }
                .filter { it.distanceMeters!! <= RADIUS_METERS }
                // Packs can overlap; duplicate POIs must not look like two competing stores.
                .distinctBy { Triple(it.name.lowercase(), Math.round(it.lat * 10_000), Math.round(it.lon * 10_000)) }
                .sortedBy { it.distanceMeters }.toList()
            val first = candidates.firstOrNull() ?: return Unknown("I couldn't find a nearby store in the available place data.")
            val distance = first.distanceMeters!!
            val nextDistance = candidates.getOrNull(1)?.distanceMeters ?: Double.POSITIVE_INFINITY
            val close = distance <= maxOf(45.0, accuracy + 20.0)
            val separated = nextDistance - distance > maxOf(40.0, 2 * accuracy)
            return if (accuracy <= 40.0 && close && separated) Likely(first)
                else Ambiguous(candidates.take(4))
        }
    }
}
