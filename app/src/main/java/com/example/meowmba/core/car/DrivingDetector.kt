package com.example.meowmba.core.car

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Decides whether the user is in a car, and how hard it is moving.
 *
 * Cues fused together, strongest first:
 *
 *  1. GPS speed — the reliable one. Sustained median speed above [Params.enterSpeedMph]
 *     turns driving on; speed below [Params.exitSpeedMph] for [Params.exitHysteresisMs]
 *     turns it off.
 *  2. Manual — the user (or the "drive mode" voice command) forces it on.
 *  3. Bluetooth — a user-configured car device is connected *and* the car is moving.
 *
 * The accelerometer only feeds [DrivingState.gForce] (for the card and the crash
 * check) — it never *enters* driving on its own, because a bus, a train or a
 * bumpy walk vibrates too.
 *
 * It also watches for a collision: either a hard linear-acceleration impact
 * ([Params.crashG]) or a sudden speed collapse while travelling fast. Crashes are
 * emitted on [crashEvents] and never auto-dismiss — the caller decides what to do.
 *
 * The sensor and location listeners must be registered/unregistered from the main
 * thread; the periodic evaluator runs on [scope].
 */
class DrivingDetector(
    context: Context,
    private val scope: CoroutineScope,
    private val params: Params = Params(),
) {
    /** Tuning knobs, defaulted for a pocketed phone on a highway. */
    data class Params(
        val enterSpeedMph: Float = 12f,
        val exitSpeedMph: Float = 5f,
        /** Median speed must be above the enter threshold for this long. */
        val enterSustainMs: Long = 6_000L,
        /** Speed below the exit threshold for this long before driving turns off. */
        val exitHysteresisMs: Long = 90_000L,
        /** How often the evaluator runs and samples the speed window. */
        val evaluateMs: Long = 2_000L,
        /** Rolling window over which median speed is computed. */
        val speedWindowMs: Long = 12_000L,
        /**
         * Linear acceleration magnitude above this is a collision. Deliberately
         * high: pocketed phones on rough roads routinely spike a few g, so the
         * threshold is set past hard braking (~1g), hard bumps (~2-3g), and
         * dropped-phone smacks, at the cost of missing glancing fender bumps.
         */
        val crashG: Float = 8.0f,
        /** The impact must stay above [crashG] for this long before it counts. */
        val crashSustainMs: Long = 300L,
        /** Lateral G magnitude above this counts as a hard turn (reaction). */
        val hardTurnG: Float = 0.35f,
        /** Longitudinal deceleration (braking) magnitude above this is a reaction. */
        val hardBrakeG: Float = 0.55f,
        /** Speed drop (mph) in a single window that counts as a brake-collision. */
        val crashSpeedDropMph: Float = 30f,
        /** Fast travel + sudden drop: only counts if we were going at least this fast. */
        val crashMinSpeedMph: Float = 50f,
        /** Ignore further crashes for this long after one fires. */
        val crashDebounceMs: Long = 60_000L,
    )

    private val appContext = (context.applicationContext ?: context)

    private val sensorManager = runCatching {
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }.getOrNull()
    private val locationManager = runCatching {
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }.getOrNull()

    private val _driving = MutableStateFlow(DrivingState())
    val driving: StateFlow<DrivingState> = _driving.asStateFlow()

    private val _crashEvents = MutableSharedFlow<CrashEvent>(extraBufferCapacity = 4)
    val crashEvents: SharedFlow<CrashEvent> = _crashEvents.asSharedFlow()

    private val _lastCrash = MutableStateFlow<CrashEvent?>(null)
    val lastCrash: StateFlow<CrashEvent?> = _lastCrash.asStateFlow()

    /** Latest position the detector has seen, for the trip tracker and crash reports. */
    val lastFix: Pair<Double, Double>?
        get() = lastLat?.let { lat -> lastLon?.let { lon -> lat to lon } }

    // ── Sensor state ─────────────────────────────────────────────────────────
    private var gForce = 0f
    private var lateralG = 0f
    private var longitudinalG = 0f
    private val sensorInput = DrivingSensorInput()
    /** True when a gravity-compensated linear-acceleration sensor exists. */
    private var linearSensorAvailable = false
    private var maneuver = DrivingManeuver()
    private var lastCrashAt = 0L
    /** Start of the current window where G stayed above [Params.crashG]. */
    private var crashAboveSince = 0L

    // ── GPS state ────────────────────────────────────────────────────────────
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastFixAt = 0L
    private var lastSpeedMph = 0f

    // ── Evaluation state ─────────────────────────────────────────────────────
    private val speedWindow = ArrayDeque<SpeedSample>()
    private var belowExitSince = 0L
    private var drivingSince = 0L
    private var job: Job? = null
    private var registered = false
    /** Interval the location stream is currently registered at, or null when off. */
    private var locationIntervalMs: Long? = null

    @Volatile
    private var carModeEnabled = false
    private var manualOverride = false
    private var carDeviceAddress: String? = null

    private fun publish() {
        val was = _driving.value
        _driving.value = DrivingState(
            isDriving = carModeEnabled && was.isDriving,
            speedMph = lastSpeedMph,
            gForce = gForce,
            lateralG = lateralG,
            longitudinalG = longitudinalG,
            reason = if (carModeEnabled && was.isDriving) was.reason else "",
            manualOverride = manualOverride,
            carModeEnabled = carModeEnabled,
        )
    }

    // ── Public controls ──────────────────────────────────────────────────────

    /** Master switch. Starting/stopping is idempotent. */
    fun setCarModeEnabled(enabled: Boolean) {
        carModeEnabled = enabled
        if (enabled) start() else stop()
        publish()
    }

    /** Voice/UI override: "drive mode on". Survives until turned off or app restart. */
    fun setManualDriving(on: Boolean) {
        manualOverride = on
        if (on) {
            carModeEnabled = true
            belowExitSince = 0L
            drivingSince = 0L
            _driving.value = _driving.value.copy(
                isDriving = true,
                reason = "Manual",
                manualOverride = true,
                carModeEnabled = true,
            )
            start()
        } else {
            // Manual off is immediate. Automatic detection may resume only
            // from fresh evidence through its existing sustain/hysteresis.
            resetTransientState()
            _driving.value = DrivingState(carModeEnabled = carModeEnabled)
        }
        publish()
    }

    /** Address of the user's car hands-free unit, if configured in Settings. */
    fun setCarDeviceAddress(address: String?) {
        carDeviceAddress = address?.takeIf { it.isNotBlank() }
        publish()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    private fun start() {
        if (registered) return
        registered = true
        registerSensors()
        registerLocation(_driving.value.isDriving)
        job = scope.launch { evaluateLoop() }
    }

    private fun stop() {
        if (registered) {
            sensorManager?.unregisterListener(sensorListener)
            locationManager?.removeUpdates(locationListener)
        }
        registered = false
        locationIntervalMs = null
        job?.cancel()
        job = null
        manualOverride = false
        resetTransientState()
        linearSensorAvailable = false
        _driving.value = DrivingState(carModeEnabled = carModeEnabled)
    }

    private fun resetTransientState() {
        speedWindow.clear()
        belowExitSince = 0L
        drivingSince = 0L
        sensorInput.reset()
        maneuver = DrivingManeuver()
        gForce = 0f
        lateralG = 0f
        longitudinalG = 0f
        crashAboveSince = 0L
        lastSpeedMph = 0f
        lastLat = null
        lastLon = null
        lastFixAt = 0L
        // Keep lastCrash and its debounce: mode toggling cannot dismiss or
        // manufacture another emergency event.
    }

    private fun registerSensors() {
        val sm = sensorManager ?: return
        // SENSOR_DELAY_NORMAL (~5Hz) is plenty for 12mph/5mph gates and the
        // crash check: GAME (~50Hz) kept two IMU streams + GPS hot and was a
        // measured hot-phone contributor. Prefer the gravity-compensated
        // linear sensor when present, plus a low-rate gravity reference. Linear
        // acceleration alone cannot identify the car plane (notably Z braking).
        val linear = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        linearSensorAvailable = linear != null &&
            sm.registerListener(sensorListener, linear, SensorManager.SENSOR_DELAY_NORMAL)
        if (linearSensorAvailable) {
            val gravitySensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            val gravityRegistered = gravitySensor != null &&
                sm.registerListener(sensorListener, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            if (gravityRegistered) return
        }
        // With linear acceleration this stream estimates gravity only; without
        // it, all three axes are gravity-compensated before magnitude/splitting.
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /**
     * Registers the location stream at the cadence the current state actually needs.
     *
     * A flat 5s/10m request kept the GPS radio fixing ~12x/minute even with the
     * phone parked and car mode merely switched on — a measured hot-phone
     * contributor, and the evaluator was already backing off to 10s while GPS was
     * not. Parked, this backs off to 60s/25m: 25m is far above the ~4m of
     * stationary GPS drift (see the drift filter in [locationListener]), so a
     * parked phone produces almost no fixes, while the *distance* criterion still
     * fires as soon as the car has travelled 25m (~5s at the 12mph entry speed).
     * That distance trigger is what lets the stream wake long before the 60s
     * interval would, so entering driving is not gated on the idle interval.
     */
    private fun registerLocation(driving: Boolean) {
        val lm = locationManager ?: return
        val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            LocationManager.GPS_PROVIDER
        } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            LocationManager.NETWORK_PROVIDER
        } else {
            return
        }
        val intervalMs = if (driving) LOCATION_INTERVAL_DRIVING_MS else LOCATION_INTERVAL_IDLE_MS
        val minDistanceM = if (driving) LOCATION_MIN_DISTANCE_DRIVING_M else LOCATION_MIN_DISTANCE_IDLE_M
        try {
            @Suppress("MissingPermission")
            lm.requestLocationUpdates(
                provider, intervalMs, minDistanceM, locationListener, Looper.getMainLooper(),
            )
            locationIntervalMs = intervalMs
        } catch (_: SecurityException) {
            // Permission missing — location is an empty cue, other signals carry on.
        } catch (_: IllegalArgumentException) {
            // Provider vanished between the check and the request.
        }
    }

    /**
     * Re-registers the location stream when the cadence changes, and only then:
     * every remove/request pair is a binder round trip to the location service.
     */
    private fun syncLocationCadence(driving: Boolean) {
        val wanted = if (driving) LOCATION_INTERVAL_DRIVING_MS else LOCATION_INTERVAL_IDLE_MS
        if (locationIntervalMs == wanted) return
        val lm = locationManager ?: return
        runCatching { lm.removeUpdates(locationListener) }
        locationIntervalMs = null
        if (registered && carModeEnabled) registerLocation(driving)
    }

    private suspend fun evaluateLoop() {
        while (true) {
            evaluate()
            // Parked phones don't need a 2s tick: back off to 10s until driving
            // starts, so car mode left on doesn't hold the CPU/GPS hot forever.
            delay(if (_driving.value.isDriving) params.evaluateMs else 10_000L)
        }
    }

    // ── Listeners ────────────────────────────────────────────────────────────

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!carModeEnabled || !registered) return
            val nowNanos = android.os.SystemClock.elapsedRealtimeNanos()
            when (event.sensor.type) {
                Sensor.TYPE_GRAVITY -> if (!sensorInput.updateGravity(event.values, event.timestamp, nowNanos)) {
                    clearMotion()
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    if (linearSensorAvailable) {
                        if (!sensorInput.updateRawGravity(event.values, event.timestamp, nowNanos)) clearMotion()
                    } else {
                        processLinear(sensorInput.raw(event.values, event.timestamp, nowNanos))
                    }
                }
                Sensor.TYPE_LINEAR_ACCELERATION ->
                    processLinear(sensorInput.linear(event.values, event.timestamp, nowNanos))
            }
            // No publish() for ordinary updates: even low-rate sensor traffic
            // would churn the state flow for no benefit. The 2s evaluator publishes.
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Folds one linear-acceleration sample into magnitude, car-plane split, crash. */
    internal fun processLinear(sample: DrivingSensorInput.Motion?) {
        if (!carModeEnabled || !registered) return
        if (sample == null) {
            clearMotion()
            return
        }
        gForce = sample.gForce
        lateralG = sample.accel.lateralG
        longitudinalG = sample.accel.longitudinalG

        if (!sample.continuous) crashAboveSince = 0L
        checkImpact(gForce, sample.timestampNanos)

        // A maneuver onset/exit must reach the UI immediately, not wait up to 2s
        // for the evaluator — that is how the cat reacts to a real turn or brake.
        val next = DrivingManeuver.from(lateralG, longitudinalG, params.hardTurnG, params.hardBrakeG)
        if (next != maneuver) {
            maneuver = next
            publish()
        }
    }

    private fun clearMotion() {
        val changed = gForce != 0f || lateralG != 0f || longitudinalG != 0f
        sensorInput.clearMotion()
        gForce = 0f
        lateralG = 0f
        longitudinalG = 0f
        maneuver = DrivingManeuver()
        crashAboveSince = 0L
        if (changed) publish()
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!carModeEnabled || !registered) return
            if (!location.speed.isFinite() || !location.latitude.isFinite() || !location.longitude.isFinite()) return
            val now = System.currentTimeMillis()
            val mph = location.speed.coerceAtLeast(0f) * MPS_TO_MPH

            // GPS occasionally reports a standing fix as a few mph of drift; a
            // short distance over a short time is the same car sitting still.
            val movedSince = lastFixAt > 0 && now - lastFixAt in 1..3_000
            if (movedSince && mph <= 3f && lastLat != null && lastLon != null) {
                val leg = CarGeo.haversineMiles(
                    lastLat!!, lastLon!!, location.latitude, location.longitude,
                )
                if (leg < 0.002) {
                    lastFixAt = now
                    return
                }
            }

            speedWindow.addLast(SpeedSample(now, mph))
            trimWindow(now)

            lastLat = location.latitude
            lastLon = location.longitude
            lastSpeedMph = mph
            lastFixAt = now
            // No publish() here either: the evaluator publishes every 2s.
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun trimWindow(now: Long) {
        while (speedWindow.isNotEmpty() && now - speedWindow.first().atMs > params.speedWindowMs) {
            speedWindow.removeFirst()
        }
    }

    /** Median of the current speed window, or null when empty. Resists GPS spikes. */
    private fun medianSpeed(): Float? {
        if (speedWindow.isEmpty()) return null
        val sorted = speedWindow.map { it.mph }.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
    }

    /** True when a connected Bluetooth device is the configured car hands-free unit. */
    private fun carBluetoothConnected(): Boolean {
        val address = carDeviceAddress ?: return false
        if (ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return false
        return runCatching {
            manager.getConnectedDevices(BluetoothProfile.HEADSET)
                .any { it.address.equals(address, ignoreCase = true) }
        }.getOrDefault(false)
    }

    private fun checkImpact(g: Float, timestampNanos: Long) {
        val nowMs = timestampNanos / 1_000_000
        if (g < params.crashG) {
            // Impact ended (or never started) — the sustain window resets.
            crashAboveSince = 0L
            return
        }
        if (crashAboveSince == 0L) crashAboveSince = nowMs
        // A single-sample spike is sensor noise, not a collision: the impact must
        // hold above threshold long enough that a bump can't trip it.
        if (nowMs - crashAboveSince < params.crashSustainMs) return
        if (nowMs - lastCrashAt < params.crashDebounceMs) return
        lastCrashAt = nowMs
        val event = CrashEvent(
            detectedAtMs = System.currentTimeMillis(),
            speedBeforeMph = lastSpeedMph,
            gForce = g,
            lat = lastLat,
            lon = lastLon,
        )
        _lastCrash.value = event
        _crashEvents.tryEmit(event)
    }

    /**
     * Sudden brake-check collision: speed collapsed by [Params.crashSpeedDropMph] while
     * travelling at least [Params.crashMinSpeedMph]. Fired from the evaluator so it is
     * fed the same debounced median the rest of the logic trusts.
     */
    private fun checkBrakeCollision(elapsedMs: Long) {
        if (speedWindow.size < 2) return
        val latest = speedWindow.last().mph
        val prev = speedWindow.first().mph
        if (latest < params.crashMinSpeedMph) return
        if (prev - latest < params.crashSpeedDropMph) return
        if (elapsedMs - lastCrashAt < params.crashDebounceMs) return
        lastCrashAt = elapsedMs
        val event = CrashEvent(
            detectedAtMs = System.currentTimeMillis(),
            speedBeforeMph = prev,
            gForce = gForce,
            lat = lastLat,
            lon = lastLon,
        )
        _lastCrash.value = event
        _crashEvents.tryEmit(event)
    }

    /**
     * Single evaluator tick. Runs on [scope] every [Params.evaluateMs].
     *
     * Speed is taken from the median of the rolling window so one noisy fix cannot
     * flip the state. Entry is gated on speed/manual/BT — never a lone accelerometer
     * spike, which a bump in the road or a bus seat would set off.
     */
    private fun evaluate() {
        if (!carModeEnabled || !registered) return
        val now = System.currentTimeMillis()
        if (!sensorInput.motionIsFresh(android.os.SystemClock.elapsedRealtimeNanos())) clearMotion()
        trimWindow(now)
        checkBrakeCollision(android.os.SystemClock.elapsedRealtime())

        val wasDriving = _driving.value.isDriving
        val median = medianSpeed() ?: 0f
        val btConnected = carBluetoothConnected()

        val shouldDrive = manualOverride ||
            median >= params.enterSpeedMph ||
            (btConnected && median >= 5f)

        if (!wasDriving) {
            if (shouldDrive) {
                if (drivingSince == 0L) drivingSince = now
                if (now - drivingSince >= params.enterSustainMs) {
                    _driving.value = _driving.value.copy(
                        isDriving = true,
                        reason = reasonFor(median, btConnected),
                    )
                    belowExitSince = 0L
                }
            } else {
                drivingSince = 0L
            }
        } else if (!manualOverride) {
            if (median < params.exitSpeedMph) {
                if (belowExitSince == 0L) belowExitSince = now
                if (now - belowExitSince >= params.exitHysteresisMs) {
                    _driving.value = _driving.value.copy(isDriving = false)
                    drivingSince = 0L
                    belowExitSince = 0L
                }
            } else {
                belowExitSince = 0L
            }
        } else {
            belowExitSince = 0L
        }

        // Keep the GPS cadence matched to the state the evaluator just settled on.
        syncLocationCadence(_driving.value.isDriving)
        publish()
    }

    private fun reasonFor(median: Float, btConnected: Boolean): String = when {
        manualOverride -> "Manual"
        median >= params.enterSpeedMph && btConnected -> "GPS speed + Bluetooth"
        median >= params.enterSpeedMph -> "GPS speed"
        btConnected -> "Bluetooth"
        else -> "GPS speed"
    }

    private data class SpeedSample(val atMs: Long, val mph: Float)

    companion object {
        private const val MPS_TO_MPH = 2.236936f

        /**
         * GPS cadence while driving. 5s/10m resolves the 12s median speed window
         * without waking the main looper on every fix (0L/0f did).
         */
        private const val LOCATION_INTERVAL_DRIVING_MS = 5_000L
        private const val LOCATION_MIN_DISTANCE_DRIVING_M = 10f

        /** GPS cadence while parked: car mode left on must not hold the radio hot. */
        private const val LOCATION_INTERVAL_IDLE_MS = 60_000L
        private const val LOCATION_MIN_DISTANCE_IDLE_M = 25f
    }
}
