package com.example.meowmba.core.context.live

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.meowmba.core.database.MeowmbaDatabase
import com.example.meowmba.core.flow.Activity
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Always-on, low-power sensing (docs/MEOW_CONTEXT_PLAN.md §1):
 *  - Activity Recognition *transitions* (vehicle / bicycle / walking / running / still), batched
 *    by Play services' sensor hub, delivered even when Meow is closed.
 *  - Fused location with BALANCED power via PendingIntent: every [STILL_INTERVAL_MS] normally,
 *    every [VEHICLE_INTERVAL_MS] while in a vehicle. No foreground service, no GPS polling loop.
 * Both land in [ContextReceiver], which updates [LiveContext] and the journal.
 */
object ContextSensing {
    private const val TAG = "ContextSensing"
    const val ACTION_TRANSITION = "com.example.meowmba.context.TRANSITION"
    const val ACTION_LOCATION = "com.example.meowmba.context.LOCATION"
    const val STILL_INTERVAL_MS = 10 * 60_000L
    const val VEHICLE_INTERVAL_MS = 60_000L

    fun canSenseActivity(ctx: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ctx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun canLocate(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun canLocateInBackground(ctx: Context): Boolean = canLocate(ctx) &&
        ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Idempotent: re-registering replaces the same PendingIntents. Call on app start, boot, grant. */
    @SuppressLint("MissingPermission")
    fun start(ctx: Context) = runCatching { startNow(ctx.applicationContext ?: ctx) }
        .onFailure { Log.w(TAG, "context sensing not started", it) }.let { }

    @SuppressLint("MissingPermission")
    private fun startNow(app: Context) {
        if (canSenseActivity(app)) runCatching {
            val kinds = listOf(DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE, DetectedActivity.WALKING,
                DetectedActivity.RUNNING, DetectedActivity.STILL)
            val transitions = kinds.flatMap { k ->
                listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map { t ->
                    ActivityTransition.Builder().setActivityType(k).setActivityTransition(t).build()
                }
            }
            ActivityRecognition.getClient(app).requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pending(app, ACTION_TRANSITION))
                .addOnFailureListener { Log.w(TAG, "activity transitions unavailable", it) }
        }
        if (canLocate(app)) requestLocation(app, LiveContext.now.value.activity == Activity.DRIVING)
    }

    @SuppressLint("MissingPermission")
    internal fun requestLocation(ctx: Context, inVehicle: Boolean) {
        if (!canLocate(ctx)) return
        val interval = if (inVehicle) VEHICLE_INTERVAL_MS else STILL_INTERVAL_MS
        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .setMaxUpdateDelayMillis(interval * 2) // let the platform batch deliveries
            .build()
        runCatching {
            LocationServices.getFusedLocationProviderClient(ctx).requestLocationUpdates(req, pending(ctx, ACTION_LOCATION))
                .addOnFailureListener { Log.w(TAG, "location updates unavailable", it) }
        }
    }

    @SuppressLint("MissingPermission") // checked just below; without it nothing was registered
    fun stop(ctx: Context) {
        val app = ctx.applicationContext ?: ctx
        if (canSenseActivity(app)) runCatching { ActivityRecognition.getClient(app).removeActivityTransitionUpdates(pending(app, ACTION_TRANSITION)) }
        runCatching { LocationServices.getFusedLocationProviderClient(app).removeLocationUpdates(pending(app, ACTION_LOCATION)) }
    }

    private fun pending(ctx: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, action.hashCode(), Intent(ctx, ContextReceiver::class.java).setAction(action),
        // Mutable: Play services writes the result extras into the intent.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    fun activityOf(type: Int): Activity = when (type) {
        DetectedActivity.IN_VEHICLE -> Activity.DRIVING
        DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.ON_FOOT, DetectedActivity.ON_BICYCLE -> Activity.WALKING
        DetectedActivity.STILL -> Activity.STILL
        else -> Activity.UNKNOWN
    }

    fun describe(type: Int, entering: Boolean): String {
        val what = when (type) {
            DetectedActivity.IN_VEHICLE -> "driving"; DetectedActivity.ON_BICYCLE -> "cycling"
            DetectedActivity.RUNNING -> "running"; DetectedActivity.WALKING -> "walking"; else -> "still"
        }
        return if (type == DetectedActivity.STILL) (if (entering) "Stopped moving" else "Started moving")
        else (if (entering) "Started $what" else "Stopped $what")
    }
}

/** Receives activity transitions and location batches, in or out of the app. */
class ContextReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                val dao = MeowmbaDatabase.getDatabase(app).lifeJournalDao()
                when (intent.action) {
                    ContextSensing.ACTION_TRANSITION -> ActivityTransitionResult.extractResult(intent)?.transitionEvents?.forEach { e ->
                        val entering = e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
                        val at = System.currentTimeMillis() - (android.os.SystemClock.elapsedRealtimeNanos() - e.elapsedRealTimeNanos) / 1_000_000
                        val before = LiveContext.now.value.activity
                        if (entering) LiveContext.setActivity(ContextSensing.activityOf(e.activityType), at)
                        // Leaving a vehicle is where the car was parked (docs/MEOW_SCENARIOS.md §2 Parking).
                        val parked = !entering && e.activityType == com.google.android.gms.location.DetectedActivity.IN_VEHICLE
                        if (entering || parked) dao.insert(ContextStamp.event(LifeEventKind.ACTIVITY,
                            if (parked) "Left the car (parked here)" else ContextSensing.describe(e.activityType, true), at))
                        val nowActivity = LiveContext.now.value.activity
                        if ((before == com.example.meowmba.core.flow.Activity.DRIVING) != (nowActivity == com.example.meowmba.core.flow.Activity.DRIVING))
                            ContextSensing.requestLocation(app, nowActivity == com.example.meowmba.core.flow.Activity.DRIVING)
                    }
                    ContextSensing.ACTION_LOCATION -> LocationResult.extractResult(intent)?.locations?.forEach { l ->
                        LiveContext.setFix(l.latitude, l.longitude, l.accuracy, if (l.hasSpeed()) l.speed else null, l.time)
                        dao.insertSample(LocationSampleEntity(at = l.time, lat = l.latitude, lon = l.longitude,
                            accuracyM = l.accuracy, activity = LiveContext.now.value.activity.name))
                    }
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ContextSensing.start(app)
                }
                dao.pruneSamples(System.currentTimeMillis() - 30L * 24 * 3_600_000L)
            } catch (t: Throwable) {
                Log.w("ContextReceiver", "context update failed", t)
            } finally { pending.finish() }
        }
    }

    private companion object { val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
