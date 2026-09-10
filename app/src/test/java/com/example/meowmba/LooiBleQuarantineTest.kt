package com.example.meowmba

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.example.meowmba.core.robot.BleSessionLifecycle
import com.example.meowmba.core.robot.BleStopReceipt
import com.example.meowmba.core.robot.BodySafety
import com.example.meowmba.core.robot.BodyScheduler
import com.example.meowmba.core.robot.LooiBleBodyOutput
import com.example.meowmba.core.robot.LooiBleLink
import com.example.meowmba.core.robot.LooiBleProtocol
import com.example.meowmba.core.robot.RobotBaseControlPolicy
import com.example.meowmba.core.robot.SensorEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Exercise the REAL adapter, never mocked command methods or a physical radio.
 *
 * Test-only reflection represents a connection/follow/clip that was already
 * active before revocation. Every actuator case has a READY fake GATT and mapped
 * writable characteristics: an offline-only test would miss the old zero-speed
 * Brake bypass. No production policy or protocol-verification flag is changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@Suppress("DEPRECATION")
class LooiBleQuarantineTest {
    private class ReadyLink : AutoCloseable {
        val context: Context = mock(Context::class.java)
        val gatt: BluetoothGatt = mock(BluetoothGatt::class.java)
        val link = LooiBleLink(context)
        @Suppress("UNCHECKED_CAST")
        val session = field(link, "session") as BleSessionLifecycle<BluetoothGatt>
        val token = checkNotNull(session.beginAttempt())
        val command = characteristic(LooiBleProtocol.CHAR_COMMAND)
        val sensor = characteristic(LooiBleProtocol.CHAR_SENSORS)
        val descriptor: BluetoothGattDescriptor = mock(BluetoothGattDescriptor::class.java).also {
            `when`(it.uuid).thenReturn(LooiBleProtocol.CCCD)
            `when`(it.characteristic).thenReturn(sensor)
        }
        val callback: BluetoothGattCallback by lazy {
            val method = LooiBleLink::class.java.getDeclaredMethod("callbackFor", Long::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(link, token) as BluetoothGattCallback
        }

        init {
            assertTrue(session.bind(token, gatt))
            assertTrue(session.initialize(gatt, emptyList()))
            setField(link, "state", LooiBleLink.LinkState.READY)
            setField(link, "gattReachable", true)
            setField(link, "commandChar", command)
            setField(link, "sensorChar", sensor)
            setField(link, "neckChar", characteristic(LooiBleProtocol.CHAR_NECK))
            setField(link, "headlightChar", characteristic(LooiBleProtocol.CHAR_HEADLIGHT))
            setField(link, "motorBoostChar", characteristic(LooiBleProtocol.CHAR_MOTOR_BOOST))
            setField(link, "motorSlowChar", characteristic(LooiBleProtocol.CHAR_MOTOR_SLOW))
            assertTrue("Fixture must be connected, not vacuously offline", link.connected)
            assertTrue(command.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
        }

        fun safety(): BodySafety = BodySafety { 1_000L }.also {
            it.attach(LooiBleBodyOutput(link)) { link.connected }
        }

        fun assertNoGattCommands() {
            // Inspect method names so both legacy and API-33 overloads are covered.
            val forbidden = mockingDetails(gatt).invocations.map { it.method.name }.filter {
                it in setOf("writeCharacteristic", "writeDescriptor", "readCharacteristic",
                    "discoverServices", "setCharacteristicNotification")
            }
            assertTrue("Unexpected GATT I/O: $forbidden", forbidden.isEmpty())
            // Lazy adapter lookup must not occur through blocked connect/stop paths.
            verifyNoInteractions(context)
        }

        fun assertRetiredWithoutWrites() {
            assertFalse(link.connected)
            assertEquals(BleSessionLifecycle.Phase.CLOSED, session.phase)
            assertNull(session.connection)
            assertEquals(BleStopReceipt.UNCONFIRMED, link.lastStopReceipt)
            assertNull("Intentional retirement must remain terminal", session.beginAttempt())
            verify(gatt, atLeastOnce()).disconnect()
            verify(gatt, atLeastOnce()).close()
            assertNoGattCommands()
        }

        override fun close() {
            link.close()
            (field(link, "scope") as CoroutineScope).cancel()
        }
    }

    @Test fun `quarantine blocks connections before requesting any Android Bluetooth service`() {
        val context = mock(Context::class.java)
        val link = LooiBleLink(context)
        try {
            assertFalse(RobotBaseControlPolicy.CONTROL_AVAILABLE)
            assertFalse(link.connectAuto(timeoutMs = 0))
            assertFalse(link.connectBlocking("00:11:22:33:44:55", timeoutMs = 0))
            assertFalse(link.connected)
            link.close()
            assertFalse(link.connectAuto(timeoutMs = 0))
            assertFalse(link.connectBlocking("00:11:22:33:44:55", timeoutMs = 0))
            verifyNoInteractions(context)
            @Suppress("UNCHECKED_CAST")
            val session = field(link, "session") as BleSessionLifecycle<BluetoothGatt>
            assertNull(session.beginAttempt())
        } finally {
            link.close()
            (field(link, "scope") as CoroutineScope).cancel()
        }
    }

    @Test fun `all actuator entry points reject even with a ready writable GATT`() {
        val actions: List<Pair<String, (LooiBleLink) -> Boolean>> = listOf(
            "zero rotation frame" to { it.writeFrame(LooiBleProtocol.rotateFrame(0, 1)) },
            "nonzero rotation frame" to { it.writeFrame(LooiBleProtocol.rotateFrame(30, 5)) },
            "other zero-valued frame" to { it.writeFrame(LooiBleProtocol.Frame(opcode = 0)) },
            "zero turn" to { it.turnPulse(0, 1) },
            "positive turn" to { it.turnPulse(25, 5) },
            "negative turn" to { it.turnPulse(-25, 5) },
            "zero drive" to { it.driveSpeed(0f) },
            "positive drive" to { it.driveSpeed(0.4f) },
            "negative drive" to { it.driveSpeed(-0.4f) },
            "invalid drive" to { it.driveSpeed(Float.NaN) },
            "center neck" to { it.setNeckPitch(0f) },
            "up neck" to { it.setNeckPitch(-1f) },
            "down neck" to { it.setNeckPitch(1f) },
            "invalid neck" to { it.setNeckPitch(Float.NaN) },
            "infinite neck" to { it.setNeckPitch(Float.POSITIVE_INFINITY) },
            "headlight on" to { it.setHeadlight(true) },
            "headlight off" to { it.setHeadlight(false) },
        )
        for ((name, action) in actions) ReadyLink().use { fixture ->
            assertFalse("$name must be rejected, not reported as sent", action(fixture.link))
            fixture.assertNoGattCommands()
        }
    }

    @Test fun `historical encoding preferences cannot restore any write path`() {
        for (encoding in LooiBleProtocol.TurnEncoding.entries) ReadyLink().use { fixture ->
            fixture.link.turnEncoding = encoding
            assertFalse(fixture.link.turnPulse(20, 5))
            assertFalse(fixture.link.turnPulse(-20, 5))
            assertFalse(fixture.link.turnPulse(0, 1))
            fixture.assertNoGattCommands()
        }
    }

    @Test fun `stopAll retires a ready real adapter without sending a guessed zero packet`() {
        ReadyLink().use { fixture ->
            assertFalse(fixture.link.stopAll())
            fixture.assertRetiredWithoutWrites()
            verify(fixture.gatt, times(1)).disconnect()
            verify(fixture.gatt, times(1)).close()
        }
    }

    @Test fun `close is immediate terminal and idempotent with a reachable writable GATT`() {
        ReadyLink().use { fixture ->
            fixture.link.close()
            fixture.link.close()
            fixture.assertRetiredWithoutWrites()
            verify(fixture.gatt, times(1)).disconnect()
            verify(fixture.gatt, times(1)).close()
            assertFalse(fixture.link.connectAuto(timeoutMs = 0))
            assertFalse(fixture.link.connectBlocking("00:11:22:33:44:55", timeoutMs = 0))
            fixture.assertNoGattCommands()
        }
    }

    @Test fun `losing a previously followed face cannot issue a brake rotation packet`() {
        ReadyLink().use { fixture ->
            val safety = fixture.safety()
            // A new follow command is now rejected. Represent only its historical
            // accepted state, so this specifically exercises the old loss→brake path.
            setField(safety, "followingActive", true)
            setField(safety, "followingUntilMs", 1_400L)
            safety.stopFollowing()
            assertEquals(false, field(safety, "followingActive"))
            fixture.assertRetiredWithoutWrites()
        }
    }

    @Test fun `unknown read and notification events use real adapter safety without any stop write`() {
        val deliveries: List<Pair<String, (ReadyLink) -> Unit>> = listOf(
            "legacy notification" to { it.callback.onCharacteristicChanged(it.gatt, it.sensor) },
            "notification with value" to {
                it.callback.onCharacteristicChanged(it.gatt, it.sensor, byteArrayOf(0x55))
            },
            "legacy read" to {
                it.callback.onCharacteristicRead(it.gatt, it.sensor, BluetoothGatt.GATT_SUCCESS)
            },
            "read with value" to {
                it.callback.onCharacteristicRead(it.gatt, it.sensor, byteArrayOf(0x55), BluetoothGatt.GATT_SUCCESS)
            },
        )
        for ((name, deliver) in deliveries) ReadyLink().use { fixture ->
            val safety = fixture.safety()
            var observed: SensorEvent? = null
            fixture.link.onSensorEvent = { event -> observed = event; safety.onSensor(event) }
            deliver(fixture)
            assertTrue("$name must actually reach the safety hook", observed is SensorEvent.Unknown)
            fixture.assertRetiredWithoutWrites()
        }
    }

    @Test fun `touch pause hazard resume and detach stop paths never write through real adapter`() {
        val interruptions: List<Pair<String, (BodySafety) -> Unit>> = listOf(
            "touch" to { it.touch() },
            "pause" to { it.setPaused(true) },
            "unknown" to { it.onSensor(SensorEvent.Unknown(byteArrayOf(0x55))) },
            "cliff" to { it.onSensor(SensorEvent.Cliff(byteArrayOf(0x55))) },
            "sensor touch" to { it.onSensor(SensorEvent.Touch) },
            "explicit stop" to { it.stop() },
            "resume interruption" to { it.resume() },
            "detach" to { it.attach(null) },
        )
        for ((name, interrupt) in interruptions) ReadyLink().use { fixture ->
            interrupt(fixture.safety())
            assertFalse("$name must retire the existing link", fixture.link.connected)
            fixture.assertRetiredWithoutWrites()
        }
    }

    @Test fun `finishing a previously active lease cannot send an unverified brake`() {
        ReadyLink().use { fixture ->
            val safety = fixture.safety()
            val lease = checkNotNull(safety.beginExplicitMotion())
            assertTrue(lease.isValid)
            // Simulate accepted pre-quarantine history, not a new accepted command.
            setField(lease, "needsBrake", true)
            lease.finish()
            assertFalse(lease.isValid)
            fixture.assertRetiredWithoutWrites()
        }
    }

    @Test fun `scheduler stop with no current clip cannot send an unverified brake`() = runTest {
        ReadyLink().use { fixture ->
            BodyScheduler(this, fixture.safety()).stop()
            fixture.assertRetiredWithoutWrites()
        }
    }

    @Test fun `close discards already pending stop work before a real GATT callback can drain it`() {
        for (alreadyClosing in listOf(false, true)) ReadyLink().use { fixture ->
            val key = "write:${fixture.command.uuid}:${fixture.command.instanceId}"
            // Only model an already in-flight platform operation; no GATT I/O.
            assertTrue(fixture.session.submit(BleSessionLifecycle.Operation(key) { true }))
            var queuedStarts = 0
            val queued = BleSessionLifecycle.Operation<BluetoothGatt>("historical-pending-write") {
                queuedStarts++
                it.writeCharacteristic(fixture.command) // Mock only; must remain unreachable.
            }
            assertTrue(fixture.session.stop(listOf(queued), closeAfter = alreadyClosing))
            assertEquals(if (alreadyClosing) BleSessionLifecycle.Phase.CLOSING else BleSessionLifecycle.Phase.STOPPING,
                fixture.session.phase)
            assertEquals(0, queuedStarts)
            fixture.link.close()
            fixture.callback.onCharacteristicWrite(fixture.gatt, fixture.command, BluetoothGatt.GATT_SUCCESS)
            assertEquals("Retirement must discard, not drain, old stop packets", 0, queuedStarts)
            fixture.assertRetiredWithoutWrites()
        }
    }

    @Test fun `late real adapter callbacks cannot rediscover subscribe read or write after close`() {
        ReadyLink().use { fixture ->
            val callback = fixture.callback
            fixture.link.close()
            callback.onConnectionStateChange(fixture.gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            callback.onServicesDiscovered(fixture.gatt, BluetoothGatt.GATT_SUCCESS)
            callback.onDescriptorWrite(fixture.gatt, fixture.descriptor, BluetoothGatt.GATT_SUCCESS)
            callback.onCharacteristicWrite(fixture.gatt, fixture.command, BluetoothGatt.GATT_SUCCESS)
            callback.onCharacteristicRead(fixture.gatt, fixture.sensor, BluetoothGatt.GATT_SUCCESS)
            callback.onCharacteristicRead(fixture.gatt, fixture.sensor, byteArrayOf(0x55), BluetoothGatt.GATT_SUCCESS)
            callback.onCharacteristicChanged(fixture.gatt, fixture.sensor)
            callback.onCharacteristicChanged(fixture.gatt, fixture.sensor, byteArrayOf(0x55))
            fixture.assertRetiredWithoutWrites()
        }
    }

    private companion object {
        fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).let {
            it.isAccessible = true
            it.get(target)
        }

        fun setField(target: Any, name: String, value: Any?) {
            target.javaClass.getDeclaredField(name).also { it.isAccessible = true }.set(target, value)
        }

        fun characteristic(uuid: UUID): BluetoothGattCharacteristic =
            mock(BluetoothGattCharacteristic::class.java).also {
                `when`(it.uuid).thenReturn(uuid)
                `when`(it.instanceId).thenReturn(0)
                `when`(it.properties).thenReturn(BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY)
                `when`(it.value).thenReturn(byteArrayOf(0x55))
            }
    }
}
