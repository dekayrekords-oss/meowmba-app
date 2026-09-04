package com.example.meowmba.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * IMU awareness: how the phone (and, later, the robot body) is moving.
 *
 * Emits [MotionState] with device tilt (from the gravity vector, low-pass
 * filtered) and discrete shake events (spikes above [SHAKE_G] with a 1s
 * debounce). The avatar leans with the phone; a shake startles the cat.
 */
data class MotionState(
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    /**
     * Rotation about the screen normal: 0° = portrait upright, ±90° = landscape.
     * Well-defined in every orientation except face-flat, so the avatar keeps
     * tracking in a horizontal car dock where [rollDeg] saturates.
     */
    val screenLeanDeg: Float = 0f,
    /** Continuous lateral G (car frame, + ≈ push toward the right side) — turns. */
    val leanG: Float = 0f,
    /** Continuous longitudinal G (car frame, + = accelerating, - = braking). */
    val surgeG: Float = 0f,
    /** True for one emission per detected shake. */
    val shaking: Boolean = false,
)

/** Future robot-body hook: anything that can report the platform pose. */
interface RobotPoseSource {
    val motion: StateFlow<MotionState>
}

class MotionSense(context: Context) : RobotPoseSource {
    // Defensive: unit-test mock contexts return null for applicationContext.
    private val sensorManager = runCatching {
        (context.applicationContext ?: context)
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }.getOrNull()

    private val _motion = MutableStateFlow(MotionState())
    override val motion: StateFlow<MotionState> = _motion.asStateFlow()

    /** Monotonic counter: UI layers watch it to fire one reaction per shake. */
    private val _shakeCount = MutableStateFlow(0)
    val shakeCount: StateFlow<Int> = _shakeCount.asStateFlow()

    val available: Boolean =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    private var lastShakeAt = 0L
    private var smoothPitch = 0f
    private var smoothRoll = 0f
    /** Low-passed gravity vector — the orientation reference for every channel. */
    private val gravity = FloatArray(3)
    /** Last valid screen-plane tilt; held while the phone is too flat to read one. */
    private var screenLean = 0f
    private var smoothLeanG = 0f
    private var smoothSurgeG = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            val (x, y, z) = floatArrayOf(event.values[0], event.values[1], event.values[2])
            val magnitude = sqrt(x * x + y * y + z * z)

            // Shake: free-fall-ish spike beyond gravity, debounced 1s.
            if (magnitude > SHAKE_G * SensorManager.GRAVITY_EARTH) {
                val now = System.currentTimeMillis()
                if (now - lastShakeAt > SHAKE_DEBOUNCE_MS) {
                    lastShakeAt = now
                    _shakeCount.value++
                    _motion.value = _motion.value.copy(shaking = true)
                }
            } else if (_motion.value.shaking) {
                _motion.value = _motion.value.copy(shaking = false)
            }

            // Tilt from the gravity vector, low-pass so the cat leans smoothly.
            val pitch = Math.toDegrees(atan2(y.toDouble(), sqrt((x * x + z * z).toDouble()))).toFloat()
            val roll = Math.toDegrees(atan2(x.toDouble(), z.toDouble())).toFloat()
            smoothPitch += (pitch - smoothPitch) * LOW_PASS
            smoothRoll += (roll - smoothRoll) * LOW_PASS

            // Gravity reference: slower low-pass than the display tilt, so
            // sustained orientation wins over bumps and vibration.
            for (i in 0..2) gravity[i] = gravity[i] * GRAVITY_KEEP + event.values[i] * (1f - GRAVITY_KEEP)
            // Screen-plane tilt holds its last value when the phone goes flat.
            MotionMath.screenLeanDeg(gravity[0], gravity[1])?.let { screenLean = it }

            // Dynamic forces in the car frame: raw minus gravity, decomposed by
            // the same car-plane math the driving detector uses. Turns push
            // laterally, braking pushes longitudinally — both continuous, so the
            // avatar can track the force instead of waiting for a threshold.
            val linear = floatArrayOf(
                x - gravity[0],
                y - gravity[1],
                z - gravity[2],
            )
            val car = com.example.meowmba.core.car.CarDynamics.decompose(linear, gravity)
            smoothLeanG += (car.lateralG - smoothLeanG) * FORCE_LOW_PASS
            smoothSurgeG += (car.longitudinalG - smoothSurgeG) * FORCE_LOW_PASS

            _motion.value = _motion.value.copy(
                pitchDeg = smoothPitch.coerceIn(-MAX_TILT_DEG, MAX_TILT_DEG),
                rollDeg = smoothRoll.coerceIn(-MAX_TILT_DEG, MAX_TILT_DEG),
                screenLeanDeg = screenLean,
                leanG = smoothLeanG,
                surgeG = smoothSurgeG,
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        // NORMAL (~5Hz) is plenty for an 8-degree avatar lean: UI rate fired
        // 15-60x/s forever — even in the background — and fanned out to avatar
        // recomposition + servo writes per sample.
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensorManager?.unregisterListener(listener)
    }

    companion object {
        const val SHAKE_G = 2.5f
        const val SHAKE_DEBOUNCE_MS = 1000L
        const val MAX_TILT_DEG = 8f
        private const val LOW_PASS = 0.12f
        /** Gravity estimator: keep 90% of the old vector per sample (~1s settle). */
        private const val GRAVITY_KEEP = 0.90f
        /** Force channels: fast enough to feel a turn, slow enough to not buzz. */
        private const val FORCE_LOW_PASS = 0.25f
    }
}
