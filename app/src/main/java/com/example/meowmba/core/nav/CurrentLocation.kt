package com.example.meowmba.core.nav

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where the phone is, via the platform [LocationManager].
 *
 * Deliberately not FusedLocationProviderClient: this app has no
 * play-services-location dependency, and a last-known fix is all the navigator needs
 * — it is choosing between shops a few miles apart, not drawing a track.
 */
object CurrentLocation {

    data class Fix(
        val lat: Double,
        val lon: Double,
        /** [Location.getTime] when known; [System.currentTimeMillis] otherwise. */
        val atMs: Long = System.currentTimeMillis(),
        /** Metres per second when the platform reported it. */
        val speedMps: Float? = null,
        val accuracyMeters: Float? = null,
        val bearingDegrees: Float? = null,
        val bearingAccuracyDegrees: Float? = null,
    ) {
        fun ageMs(nowMs: Long = System.currentTimeMillis()): Long = (nowMs - atMs).coerceAtLeast(0L)
        fun isFresh(maxAgeMs: Long = FRESH_MS, nowMs: Long = System.currentTimeMillis()): Boolean =
            ageMs(nowMs) <= maxAgeMs
        val speedMph: Float?
            get() = speedMps?.takeIf { it >= 0f }?.times(MPS_TO_MPH)
    }

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
            val providers = lm.getProviders(true)
            val result = providers
                .mapNotNull { p ->
                    @Suppress("MissingPermission")
                    runCatching { lm.getLastKnownLocation(p) }.getOrNull()
                }
                .maxByOrNull { it.time }
                ?.let {
                    Fix(
                        it.latitude,
                        it.longitude,
                        it.time,
                        speedMps = if (it.hasSpeed()) it.speed else null,
                        accuracyMeters = if (it.hasAccuracy()) it.accuracy else null,
                        bearingDegrees = if (it.hasBearing()) it.bearing else null,
                        bearingAccuracyDegrees = if (it.hasBearingAccuracy()) it.bearingAccuracyDegrees else null,
                    )
                }
            Log.i("CurrentLocation", "last(): providers=${providers.joinToString(",")} fix=${result != null} accuracy=${result?.accuracyMeters}")
            result
        } catch (e: SecurityException) {
            Log.w("CurrentLocation", "Location denied at call time: ${e.message}")
            null
        }
    }

    /**
     * Actively asks the platform for a fresh fix instead of relying on a cached one.
     * Some OEMs (notably Samsung) return null from [last] until the app has actually
     * received a delivery, so a voice "remember this place" can fail right after
     * launch even though GPS has a fix. Request a one-shot update and time-box it.
     *
     * [onResult] is always invoked on the main thread, exactly once, with null on
     * timeout / denial / no fix. Safe to call from any thread; returns immediately.
     */
    fun requestOnce(
        context: Context,
        timeoutMs: Long = 12_000L,
        requiredAccuracyMeters: Float? = null,
        onResult: (Fix?) -> Unit,
    ) {
        val main = Handler(Looper.getMainLooper())
        if (!hasPermission(context)) {
            main.post { onResult(null) }
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            main.post { onResult(null) }
            return
        }
        val delivered = AtomicBoolean(false)
        val finish = { fix: Fix? ->
            if (delivered.compareAndSet(false, true)) main.post { onResult(fix) }
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Road warnings cannot use a fast coarse-network delivery in
                // place of the GPS fix still on its way. Other callers retain
                // the original first-delivery behavior.
                if (requiredAccuracyMeters != null &&
                    (!location.hasAccuracy() || !location.accuracy.isFinite() ||
                        location.accuracy !in 0f..requiredAccuracyMeters)
                ) return
                lm.removeUpdates(this)
                finish(
                    Fix(
                        location.latitude,
                        location.longitude,
                        location.time,
                        speedMps = if (location.hasSpeed()) location.speed else null,
                        accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                        bearingDegrees = if (location.hasBearing()) location.bearing else null,
                        bearingAccuracyDegrees = if (location.hasBearingAccuracy()) location.bearingAccuracyDegrees else null,
                    )
                )
            }

            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
        var any = false
        for (p in providers) {
            if (lm.getProviders(true).contains(p)) {
                @Suppress("MissingPermission")
                runCatching { lm.requestSingleUpdate(p, listener, Looper.getMainLooper()) }
                    .onSuccess { any = true }
            }
        }
        if (!any) {
            finish(null)
            return
        }
        // Daemon thread, and shut down as soon as the timeout task is queued.
        // The default factory makes a NON-daemon thread and nothing ever shut
        // this executor down, so every location request leaked a live thread
        // for the life of the process — and non-daemon threads keep the runtime
        // from exiting. shutdown() still runs the already-scheduled delayed
        // task (ScheduledThreadPoolExecutor keeps them by default), it just
        // stops the pool accepting new work and lets the thread die afterwards.
        val timer = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "meow-location-timeout").apply { isDaemon = true }
        }
        timer.schedule({
            lm.removeUpdates(listener)
            finish(null)
        }, timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        timer.shutdown()
    }

    /** Last-known is usable for road alerts / nearby search below this age. */
    const val FRESH_MS = 90_000L

    /** Shared by voice and tools: never save an old cached position as 'here'. */
    suspend fun fresh(context: Context, maxAgeMs: Long = 30_000L, maxAccuracyMeters: Float = 150f): Fix? {
        fun usable(fix: Fix): Boolean = fix.lat.isFinite() && fix.lon.isFinite() &&
            fix.lat in -90.0..90.0 && fix.lon in -180.0..180.0 &&
            System.currentTimeMillis() - fix.atMs in -2_000L..maxAgeMs &&
            fix.accuracyMeters?.let { it.isFinite() && it in 0f..maxAccuracyMeters } == true
        last(context)?.takeIf(::usable)?.let { return it }
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            requestOnce(context, requiredAccuracyMeters = maxAccuracyMeters) { fix ->
                if (continuation.isActive) continuation.resumeWith(Result.success(fix?.takeIf(::usable)))
            }
        }
    }

    /** New platform callback avoids an unbounded geocoder call on a worker thread.
     * Coordinates remain usable offline or on older devices. */
    suspend fun address(context: Context, fix: Fix): String? {
        if (android.os.Build.VERSION.SDK_INT < 33 || !android.location.Geocoder.isPresent()) return null
        return kotlinx.coroutines.withTimeoutOrNull(3_000L) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                try {
                    android.location.Geocoder(context, java.util.Locale.getDefault()).getFromLocation(
                        fix.lat, fix.lon, 1, object : android.location.Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                val a = addresses.firstOrNull()
                                // A street address is not evidence of a cross street. Never invent an intersection.
                                val road = a?.thoroughfare?.takeIf { it.isNotBlank() }
                                val label = if (road != null) listOfNotNull(road, a.locality).distinct().joinToString(", ")
                                    else a?.getAddressLine(0)
                                if (continuation.isActive) continuation.resumeWith(Result.success(label))
                            }
                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resumeWith(Result.success(null))
                            }
                        })
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resumeWith(Result.success(null))
                }
            }
        }
    }

    private const val MPS_TO_MPH = 2.2369363f
}
