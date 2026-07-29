package com.example.meowmba.core.nav

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Where the phone is, via the platform [LocationManager].
 *
 * Deliberately not FusedLocationProviderClient: this app has no
 * play-services-location dependency, and a last-known fix is all the navigator needs
 * — it is choosing between shops a few miles apart, not drawing a track.
 */
object CurrentLocation {

    data class Fix(val lat: Double, val lon: Double)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Most recent fix from any enabled provider, newest wins. Null when permission is
     * missing or nothing has a fix yet.
     */
    fun last(context: Context): Fix? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        return try {
            lm.getProviders(true)
                .mapNotNull { p ->
                    @Suppress("MissingPermission")
                    runCatching { lm.getLastKnownLocation(p) }.getOrNull()
                }
                .maxByOrNull { it.time }
                ?.let { Fix(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            Log.w("CurrentLocation", "Location denied at call time: ${e.message}")
            null
        }
    }
}
