package com.example.meowmba

import android.content.Context
import android.hardware.*
import android.os.SystemClock
import com.example.meowmba.core.sensor.MotionSense
import com.example.meowmba.core.sensor.MotionState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MotionSenseLifecycleTest {
    private class Fixture {
        val sensors = mock(SensorManager::class.java)
        val accel = mock(Sensor::class.java).also { `when`(it.type).thenReturn(Sensor.TYPE_ACCELEROMETER) }
        val gravity = mock(Sensor::class.java).also { `when`(it.type).thenReturn(Sensor.TYPE_GRAVITY) }
        val ctx = mock(Context::class.java).also {
            `when`(it.applicationContext).thenReturn(it)
            `when`(it.getSystemService(Context.SENSOR_SERVICE)).thenReturn(sensors)
        }
        val sense: MotionSense
        private val listener: SensorEventListener
        init {
            `when`(sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)).thenReturn(accel)
            `when`(sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)).thenReturn(gravity)
            `when`(sensors.registerListener(any(SensorEventListener::class.java), any(Sensor::class.java), eq(50_000))).thenReturn(true)
            sense = MotionSense(ctx)
            listener = MotionSense::class.java.getDeclaredField("listener").apply { isAccessible = true }.get(sense) as SensorEventListener
            advance(); sense.start()
        }
        fun advance() = ShadowSystemClock.advanceBy(Duration.ofMillis(50))
        fun feed(sensor: Sensor = accel, x: Float = 0f, y: Float = 9.80665f, z: Float = 0f,
                 stamp: Long? = null) {
            advance()
            val event = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
                .apply { isAccessible = true }.newInstance(3)
            event.sensor = sensor; event.timestamp = stamp ?: SystemClock.elapsedRealtimeNanos()
            event.values[0] = x; event.values[1] = y; event.values[2] = z
            listener.onSensorChanged(event)
        }
    }

    @Test fun stoppedAndPreviousSessionCallbacksCannotResurrectOldForce() {
        val f = Fixture()
        try {
            f.feed(f.gravity); repeat(12) { f.feed(z = 6f) }
            assertTrue(f.sense.motion.value.surgeG < -0.4f)
            val oldStamp = f.sense.motion.value.observedAtMs * 1_000_000
            f.sense.stop(); assertEquals(MotionState(), f.sense.motion.value)
            f.feed(z = 6f); assertEquals(MotionState(), f.sense.motion.value)
            f.sense.start()
            f.feed(z = 6f, stamp = oldStamp)
            assertEquals(MotionState(), f.sense.motion.value)
            f.feed()
            assertEquals(0f, f.sense.motion.value.surgeG, 0.001f)
            assertTrue(f.sense.motion.value.observedAtMs > 0)
        } finally { f.sense.stop() }
    }

    @Test fun successfulButSilentGravitySensorUsesRawGravityFallback() {
        val f = Fixture()
        try {
            repeat(40) { f.feed() }
            assertEquals(0f, f.sense.motion.value.leanG, 0.001f)
            assertEquals(0f, f.sense.motion.value.surgeG, 0.001f)
            assertEquals(0f, f.sense.motion.value.screenLeanDeg, 0.001f)
        } finally { f.sense.stop() }
    }

    @Test fun stalledGravityReferenceEventuallyTracksTheNewMount() {
        val f = Fixture()
        try {
            f.feed(f.gravity, x = 9.80665f, y = 0f)
            repeat(420) { f.feed() }
            assertEquals(0f, f.sense.motion.value.screenLeanDeg, 0.1f)
            assertEquals(0f, f.sense.motion.value.leanG, 0.01f)
            assertEquals(0f, f.sense.motion.value.surgeG, 0.01f)
        } finally { f.sense.stop() }
    }

    @Test fun invalidOutOfOrderFutureAndOldSamplesDoNotRefreshMotion() {
        val f = Fixture()
        try {
            f.feed(); val before = f.sense.motion.value
            val stamp = before.observedAtMs * 1_000_000
            f.feed(z = Float.NaN)
            f.feed(z = Float.MAX_VALUE)
            f.feed(z = 6f, stamp = stamp)
            f.feed(z = 6f, stamp = stamp + 20_000_000_000)
            f.feed(z = 6f, stamp = stamp - 1_000_000_000)
            assertEquals(before, f.sense.motion.value)
        } finally { f.sense.stop() }
    }
}
