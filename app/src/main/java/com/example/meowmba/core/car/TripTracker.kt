package com.example.meowmba.core.car

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Turns the detector's on/off + speed + position stream into trip statistics.
 *
 * Feed it once per merge (ideally every [DrivingDetector] state change or location
 * update) via [update]. While [DrivingDetector.driving].isDriving is true it
 * accumulates distance (clamped legs reject GPS jumps), duration, average speed
 * and top speed. The moment driving goes false it finalises and remembers the
 * trip in [lastTrip] for "how was my drive home?".
 *
 * Pure Kotlin — no Android dependencies — so it is unit-testable.
 */
class TripTracker {

    private val _trip = MutableStateFlow<TripStats?>(null)
    /** The trip in progress, or null when parked. */
    val trip: StateFlow<TripStats?> = _trip.asStateFlow()

    private val _lastTrip = MutableStateFlow<TripStats?>(null)
    /** The most recently completed trip, kept until the next one ends. */
    val lastTrip: StateFlow<TripStats?> = _lastTrip.asStateFlow()

    private var startedAt = 0L
    private var lastSampleMs = 0L
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var accumulatedMiles = 0.0
    private var maxSpeed = 0f
    private var speedSamples = 0
    private var speedSum = 0f

    /**
     * @param isDriving whether a trip should be running now.
     * @param speedMph current speed (used for avg/max even when standing still).
     * @param lat/[lon] current position, or null when GPS is unavailable.
     */
    fun update(isDriving: Boolean, speedMph: Float, lat: Double?, lon: Double?, timeMs: Long = System.currentTimeMillis()) {
        if (isDriving) {
            if (_trip.value == null) {
                startedAt = timeMs
                lastSampleMs = timeMs
                lastLat = lat
                lastLon = lon
                accumulatedMiles = 0.0
                maxSpeed = 0f
                speedSamples = 0
                speedSum = 0f
                // No early return: this first sample's speed counts toward avg/max too.
            }
            if (lat != null && lon != null && lastLat != null && lastLon != null) {
                val leg = CarGeo.haversineMiles(lastLat!!, lastLon!!, lat, lon)
                // A single fix can jump the phone across town; clamp so one bad
                // sample cannot inflate the trip.
                if (leg in 0.0..2.0) accumulatedMiles += leg
            }
            lastLat = lat ?: lastLat
            lastLon = lon ?: lastLon
            maxSpeed = maxOf(maxSpeed, speedMph)
            speedSamples++
            speedSum += speedMph
            lastSampleMs = timeMs
            _trip.value = TripStats(
                startedAtMs = startedAt,
                distanceMiles = accumulatedMiles,
                durationMs = (timeMs - startedAt).coerceAtLeast(0L),
                avgSpeedMph = if (speedSamples > 0) speedSum / speedSamples else 0f,
                maxSpeedMph = maxSpeed,
            )
        } else if (_trip.value != null) {
            val finished = _trip.value!!
            _lastTrip.value = finished
            _trip.value = null
            lastLat = null
            lastLon = null
        }
    }

    fun reset() {
        _trip.value = null
        _lastTrip.value = null
        lastLat = null
        lastLon = null
        accumulatedMiles = 0.0
        maxSpeed = 0f
        speedSamples = 0
        speedSum = 0f
    }
}
