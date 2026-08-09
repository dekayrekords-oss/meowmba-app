package com.example.meowmba.core.car

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Live snapshot of whether the user is driving, and why.
 *
 * [reason] is the strongest cue that flipped the detector: "GPS speed", "Manual",
 * "Bluetooth" or "Vibration". [speedMph] and [gForce] are carried here too so the
 * whole car state lives behind one [DrivingDetector.driving] flow.
 */
data class DrivingState(
    val isDriving: Boolean = false,
    val speedMph: Float = 0f,
    val gForce: Float = 0f,
    /** Signed lateral G in the horizontal plane (+ = right turn). Approximate. */
    val lateralG: Float = 0f,
    /** Signed longitudinal G (+ = accelerating forward, - = braking). Approximate. */
    val longitudinalG: Float = 0f,
    val reason: String = "",
    /** True when the user (or voice) forced drive mode on. */
    val manualOverride: Boolean = false,
    /** The car-assistant master switch (Settings). Detector only runs while true. */
    val carModeEnabled: Boolean = false,
)

/** Stats for the trip currently in progress, or null when parked. */
data class TripStats(
    val startedAtMs: Long,
    val distanceMiles: Double = 0.0,
    val durationMs: Long = 0L,
    val avgSpeedMph: Float = 0f,
    val maxSpeedMph: Float = 0f,
) {
    val durationMinutes: Int get() = (durationMs / 60_000L).toInt()
}

/** A possible collision: hard impact or sudden brake while travelling fast. */
data class CrashEvent(
    val detectedAtMs: Long,
    val speedBeforeMph: Float,
    val gForce: Float,
    val lat: Double? = null,
    val lon: Double? = null,
)

/** Straight-line helpers for trip distance and ETA. */
object CarGeo {

    /** Great-circle distance in miles. */
    fun haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 3958.7613 // Earth radius in miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /** Minutes to cover [miles] at [speedMph]; falls back to 30 mph when speed is 0. */
    fun etaMinutes(miles: Double, speedMph: Float): Int {
        val s = speedMph.takeIf { it > 5f } ?: 30f
        return (miles / s * 60).roundToInt().coerceAtLeast(1)
    }
}
