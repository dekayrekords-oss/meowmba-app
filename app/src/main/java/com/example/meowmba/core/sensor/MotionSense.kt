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
            _motion.value = _motion.value.copy(
                pitchDeg = smoothPitch.coerceIn(-MAX_TILT_DEG, MAX_TILT_DEG),
                rollDeg = smoothRoll.coerceIn(-MAX_TILT_DEG, MAX_TILT_DEG),
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager?.unregisterListener(listener)
    }

    companion object {
        const val SHAKE_G = 2.5f
        const val SHAKE_DEBOUNCE_MS = 1000L
        const val MAX_TILT_DEG = 8f
        private const val LOW_PASS = 0.12f
    }
}
