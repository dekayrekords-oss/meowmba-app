package com.example.meowmba.ui.companion

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Inverse camera rotation: keep the cloud in its initial spatial frame. */
internal fun orbViewRotation(current: FloatArray, reference: FloatArray): FloatArray =
    FloatArray(9) { index ->
        val row = index / 3
        val col = index % 3
        (0..2).sumOf { k -> (current[k * 3 + row] * reference[k * 3 + col]).toDouble() }.toFloat()
    }

/** Fused gyro orientation, registered only while this visible avatar is resumed. */
@Composable
internal fun rememberOrbOrientation(enabled: Boolean): State<FloatArray?> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val orientation = remember { mutableStateOf<FloatArray?>(null) }
    DisposableEffect(context, lifecycle, enabled) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var reference: FloatArray? = null
        var registered = false
        val raw = FloatArray(9)
        val screen = FloatArray(9)
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(raw, event.values)
                val axes = when (context.display?.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(raw, axes.first, axes.second, screen)
                if (reference == null) reference = screen.copyOf()
                orientation.value = orbViewRotation(screen, reference!!)
            }
        }
        fun sync() {
            val active = enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (active && !registered && sensor != null) {
                reference = null
                registered = manager?.registerListener(listener, sensor, 20_000) == true
            } else if (!active && registered) {
                manager?.unregisterListener(listener)
                registered = false
            }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycle.addObserver(observer)
        sync()
        onDispose {
            lifecycle.removeObserver(observer)
            manager?.unregisterListener(listener)
        }
    }
    return orientation
}
