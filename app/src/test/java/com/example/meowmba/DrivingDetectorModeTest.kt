package com.example.meowmba

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.meowmba.core.car.CarDynamics
import com.example.meowmba.core.car.DrivingDetector
import com.example.meowmba.core.car.DrivingManeuver
import com.example.meowmba.core.car.DrivingSensorInput
import com.example.meowmba.core.car.DrivingState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/** No Android sensor delivery, GPS, notifications, or evaluator clock is run here. */
class DrivingDetectorModeTest {
    private fun context(sensors: SensorManager? = null): Context = mock(Context::class.java).also {
        `when`(it.applicationContext).thenReturn(it)
        `when`(it.getSystemService(Context.SENSOR_SERVICE)).thenReturn(sensors)
    }

    private fun motion(lateral: Float = 0f, longitudinal: Float = 0f) = DrivingSensorInput.Motion(
        gForce = 0.8f,
        accel = CarDynamics.Accel(lateralG = lateral, longitudinalG = longitudinal),
        timestampNanos = 1_000_000_000L,
        continuous = true,
    )

    @Test fun `manual on from disabled immediately publishes driving and enables master`() = runTest {
        val detector = DrivingDetector(context(), this)
        try {
            detector.setManualDriving(true)
            assertEquals(
                DrivingState(isDriving = true, reason = "Manual", manualOverride = true, carModeEnabled = true),
                detector.driving.value,
            )
            assertNull(detector.lastCrash.value)
        } finally {
            // Cancel before the periodic evaluator can run: activation must not need a tick.
            detector.setCarModeEnabled(false)
        }
    }

    @Test fun `manual off clears driving and forces immediately but leaves master armed`() = runTest {
        val detector = DrivingDetector(context(), this)
        try {
            detector.setManualDriving(true)
            detector.processLinear(motion(lateral = 0.6f))
            assertTrue(detector.driving.value.gForce > 0f)
            detector.setManualDriving(false)
            assertEquals(DrivingState(carModeEnabled = true), detector.driving.value)
            assertNull(detector.lastFix)
            assertNull(detector.lastCrash.value)
        } finally {
            detector.setCarModeEnabled(false)
        }
    }

    @Test fun `master off clears manual force and reason without latching on restart`() = runTest {
        val detector = DrivingDetector(context(), this)
        try {
            detector.setManualDriving(true)
            detector.processLinear(motion(longitudinal = -0.7f))
            detector.setCarModeEnabled(false)
            assertEquals(DrivingState(), detector.driving.value)
            detector.processLinear(motion(lateral = -0.6f))
            assertEquals(DrivingState(), detector.driving.value)
            detector.setCarModeEnabled(false)
            assertEquals(DrivingState(), detector.driving.value)
            detector.setCarModeEnabled(true)
            assertEquals(DrivingState(carModeEnabled = true), detector.driving.value)
        } finally {
            detector.setCarModeEnabled(false)
        }
    }

    @Test fun `manual off while already disabled never enables mode`() = runTest {
        val detector = DrivingDetector(context(), this)
        detector.setManualDriving(false)
        assertEquals(DrivingState(), detector.driving.value)
        detector.setCarModeEnabled(false)
        assertEquals(DrivingState(), detector.driving.value)
    }

    @Test fun `left to right then turn to brake publishes every distinct signature immediately`() = runTest {
        val detector = DrivingDetector(context(), this)
        try {
            detector.setManualDriving(true)
            detector.processLinear(motion(lateral = -0.6f))
            assertEquals(-0.6f, detector.driving.value.lateralG, 0f)
            detector.processLinear(motion(lateral = 0.6f))
            assertEquals(0.6f, detector.driving.value.lateralG, 0f)
            detector.processLinear(motion(longitudinal = -0.7f))
            assertEquals(0f, detector.driving.value.lateralG, 0f)
            assertEquals(-0.7f, detector.driving.value.longitudinalG, 0f)
        } finally {
            detector.setCarModeEnabled(false)
        }
    }

    @Test fun `invalid motion immediately clears the currently published maneuver`() = runTest {
        val detector = DrivingDetector(context(), this)
        try {
            detector.setManualDriving(true)
            detector.processLinear(motion(lateral = 0.6f))
            detector.processLinear(null)
            assertEquals(0f, detector.driving.value.gForce, 0f)
            assertEquals(0f, detector.driving.value.lateralG, 0f)
            assertEquals(0f, detector.driving.value.longitudinalG, 0f)
            assertTrue(detector.driving.value.isDriving)
        } finally {
            detector.setCarModeEnabled(false)
        }
    }

    @Test fun `manual on starts low rate linear and gravity registration exactly once`() = runTest {
        val sensors = mock(SensorManager::class.java)
        val linear = mock(Sensor::class.java)
        val gravity = mock(Sensor::class.java)
        `when`(sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)).thenReturn(linear)
        `when`(sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)).thenReturn(gravity)
        `when`(sensors.registerListener(any(SensorEventListener::class.java), eq(linear), eq(SensorManager.SENSOR_DELAY_NORMAL)))
            .thenReturn(true)
        `when`(sensors.registerListener(any(SensorEventListener::class.java), eq(gravity), eq(SensorManager.SENSOR_DELAY_NORMAL)))
            .thenReturn(true)
        val detector = DrivingDetector(context(sensors), this)
        try {
            detector.setManualDriving(true)
            detector.setManualDriving(true)
            verify(sensors, times(1)).registerListener(any(SensorEventListener::class.java), eq(linear), eq(SensorManager.SENSOR_DELAY_NORMAL))
            verify(sensors, times(1)).registerListener(any(SensorEventListener::class.java), eq(gravity), eq(SensorManager.SENSOR_DELAY_NORMAL))
            verify(sensors, never()).getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } finally {
            detector.setCarModeEnabled(false)
        }
        verify(sensors, times(1)).unregisterListener(any(SensorEventListener::class.java))
    }

    @Test fun `linear sensor without gravity registers raw gravity fallback at low rate`() = runTest {
        val sensors = mock(SensorManager::class.java)
        val linear = mock(Sensor::class.java)
        val raw = mock(Sensor::class.java)
        `when`(sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)).thenReturn(linear)
        `when`(sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)).thenReturn(raw)
        `when`(sensors.registerListener(any(SensorEventListener::class.java), eq(linear), eq(SensorManager.SENSOR_DELAY_NORMAL)))
            .thenReturn(true)
        val detector = DrivingDetector(context(sensors), this)
        try {
            detector.setManualDriving(true)
            verify(sensors).registerListener(any(SensorEventListener::class.java), eq(raw), eq(SensorManager.SENSOR_DELAY_NORMAL))
        } finally {
            detector.setCarModeEnabled(false)
        }
    }

    @Test fun `failed linear registration falls back to raw sensor`() = runTest {
        val sensors = mock(SensorManager::class.java)
        val linear = mock(Sensor::class.java)
        val raw = mock(Sensor::class.java)
        `when`(sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)).thenReturn(linear)
        `when`(sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)).thenReturn(raw)
        val detector = DrivingDetector(context(sensors), this)
        try {
            detector.setManualDriving(true)
            verify(sensors).registerListener(any(SensorEventListener::class.java), eq(raw), eq(SensorManager.SENSOR_DELAY_NORMAL))
            verify(sensors, never()).getDefaultSensor(Sensor.TYPE_GRAVITY)
        } finally {
            detector.setCarModeEnabled(false)
        }
    }

    @Test fun `upright gravity resolves linear Z braking instead of dropping it`() {
        val input = DrivingSensorInput()
        val at = 1_000_000_000L
        assertTrue(input.updateGravity(floatArrayOf(0f, G, 0f), at, at))
        val sample = input.linear(floatArrayOf(0f, 0f, 0.7f * G), at, at)!!
        assertEquals(0.7f, sample.gForce, EPS)
        assertEquals(-0.7f, sample.accel.longitudinalG, EPS)
        assertEquals(0f, sample.accel.lateralG, EPS)
    }

    @Test fun `raw gravity reference also preserves Z braking from linear sensor`() {
        val input = DrivingSensorInput()
        val at = 1_000_000_000L
        assertTrue(input.updateRawGravity(floatArrayOf(0f, G, 0f), at, at))
        val sample = input.linear(floatArrayOf(0f, 0f, G * 0.6f), at, at)!!
        assertEquals(-0.6f, sample.accel.longitudinalG, EPS)
    }

    @Test fun `stationary raw fallback subtracts every gravity axis from first sample`() {
        val orientations = listOf(
            floatArrayOf(G, 0f, 0f), floatArrayOf(0f, G, 0f), floatArrayOf(0f, 0f, G),
            floatArrayOf(-G, 0f, 0f), floatArrayOf(0f, -G, 0f), floatArrayOf(0f, 0f, -G),
            floatArrayOf(G * 0.6f, G * 0.8f, 0f),
        )
        orientations.forEach { gravity ->
            val input = DrivingSensorInput()
            repeat(8) { index ->
                val at = 1_000_000_000L + index * 200_000_000L
                val sample = input.raw(gravity, at, at)!!
                assertEquals(0f, sample.gForce, EPS)
                assertEquals(0f, sample.accel.lateralG, EPS)
                assertEquals(0f, sample.accel.longitudinalG, EPS)
            }
        }
    }

    @Test fun `missing or stale gravity removes direction but preserves impact magnitude`() {
        val input = DrivingSensorInput()
        val at = 1_000_000_000L
        val linear = floatArrayOf(0f, 0f, G * 9f)
        val missing = input.linear(linear, at, at)!!
        assertEquals(9f, missing.gForce, EPS)
        assertEquals(CarDynamics.Accel(), missing.accel)
        input.updateGravity(floatArrayOf(0f, G, 0f), at, at)
        val later = at + DrivingSensorInput.MAX_SAMPLE_AGE_NS + 1L
        val stale = input.linear(linear, later, later)!!
        assertEquals(9f, stale.gForce, EPS)
        assertEquals(CarDynamics.Accel(), stale.accel)
    }

    @Test fun `short nonfinite overflowed stale and future samples are rejected`() {
        val at = 5_000_000_000L
        val badValues = listOf(
            floatArrayOf(), floatArrayOf(1f, 2f), floatArrayOf(Float.NaN, 0f, 0f),
            floatArrayOf(0f, Float.POSITIVE_INFINITY, 0f), floatArrayOf(0f, 0f, Float.NEGATIVE_INFINITY),
            floatArrayOf(Float.MAX_VALUE, 0f, 0f),
        )
        badValues.forEach {
            assertNull(DrivingSensorInput().linear(it, at, at))
            assertNull(DrivingSensorInput().raw(it, at, at))
        }
        val zeros = FloatArray(3)
        assertNull(DrivingSensorInput().linear(zeros, at - DrivingSensorInput.MAX_SAMPLE_AGE_NS - 1L, at))
        assertNull(DrivingSensorInput().linear(zeros, at + 1L, at))
        assertNull(DrivingSensorInput().linear(zeros, 0L, at))
    }

    @Test fun `duplicate and out of order samples cannot extend a motion window`() {
        val input = DrivingSensorInput()
        val at = 5_000_000_000L
        val zeros = FloatArray(3)
        assertFalse(input.linear(zeros, at, at)!!.continuous)
        assertNull(input.linear(zeros, at, at))
        assertNull(input.linear(zeros, at - 1L, at))
        assertTrue(input.linear(zeros, at + 200_000_000L, at + 200_000_000L)!!.continuous)
    }

    @Test fun `long input gap and explicit clear break continuity`() {
        val input = DrivingSensorInput()
        val at = 5_000_000_000L
        val zeros = FloatArray(3)
        input.linear(zeros, at, at)
        assertTrue(input.motionIsFresh(at))
        val later = at + DrivingSensorInput.MAX_SAMPLE_AGE_NS + 1L
        assertFalse(input.motionIsFresh(later))
        assertFalse(input.linear(zeros, later, later)!!.continuous)
        input.clearMotion()
        assertFalse(input.motionIsFresh(later))
        assertFalse(input.linear(zeros, later + 1L, later + 1L)!!.continuous)
    }

    @Test fun `reset removes prior orientation and accepts a fresh timeline`() {
        val input = DrivingSensorInput()
        val at = 5_000_000_000L
        input.updateGravity(floatArrayOf(0f, G, 0f), at, at)
        input.linear(floatArrayOf(0f, 0f, G), at, at)
        input.reset()
        assertFalse(input.motionIsFresh(at))
        val restarted = input.linear(floatArrayOf(0f, 0f, G), at - 1L, at - 1L)!!
        assertFalse(restarted.continuous)
        assertEquals(CarDynamics.Accel(), restarted.accel)
    }

    @Test fun `invalid gravity cannot retain a previous directional reference`() {
        val input = DrivingSensorInput()
        val at = 5_000_000_000L
        input.updateGravity(floatArrayOf(0f, G, 0f), at, at)
        assertFalse(input.updateGravity(FloatArray(3), at + 1L, at + 1L))
        assertEquals(
            CarDynamics.Accel(),
            input.linear(floatArrayOf(0f, 0f, G), at + 1L, at + 1L)!!.accel,
        )
    }

    @Test fun `maneuver signatures distinguish signs and combined braking without extra magnitude churn`() {
        fun signature(lateral: Float, longitudinal: Float) = DrivingManeuver.from(lateral, longitudinal, 0.35f, 0.55f)
        assertNotEquals(signature(-0.5f, 0f), signature(0.5f, 0f))
        assertNotEquals(signature(0.5f, 0f), signature(0f, -0.7f))
        assertNotEquals(signature(0.5f, 0f), signature(0.5f, -0.7f))
        assertEquals(signature(0.5f, -0.7f), signature(0.8f, -0.9f))
        assertEquals(DrivingManeuver(), signature(0.34f, -0.54f))
    }

    private companion object {
        const val G = DrivingSensorInput.G
        const val EPS = 0.0001f
    }
}
