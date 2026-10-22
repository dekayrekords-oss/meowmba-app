package com.example.meowmba.core.robot

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One explicitly requested LOOI GATT session. Scan, initialization, keep-alive,
 * reads and disconnect share one lifecycle fence. Physical control is currently
 * quarantined: no scan/connect or motor/light/stop packet may be submitted.
 * Intentional close revokes admission without attempting a guessed brake packet.
 * A subsequent explicit user connect must create a new link instance.
 *
 * The old zero-speed rotation template could itself be a movement command.
 * A verified stop/attachment protocol is required before restoring control.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class LooiBleLink(private val context: Context) {
    enum class LinkState { OFFLINE, SCANNING, CONNECTING, INITIALIZING, READY }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val adapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val session = BleSessionLifecycle<BluetoothGatt> { retired ->
        runCatching { retired.disconnect() }
        runCatching { retired.close() }
    }
    private var scanCallback: ScanCallback? = null
    private var scanTimeoutJob: Job? = null
    private var keepAliveJob: Job? = null
    private var gattReachable = false
    private var stateRevision = 0L
    private var readyLatch: CountDownLatch? = null

    @Volatile var state: LinkState = LinkState.OFFLINE
        private set
    /** Callback delivery is outside the link monitor (avoids safety-lock inversion). */
    @Volatile var onStateChange: ((LinkState, String) -> Unit)? = null

    private val _sensorFrames = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 16,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val sensorFrames: SharedFlow<ByteArray> = _sensorFrames.asSharedFlow()
    /** Synchronous safety hook; SharedFlow delivery alone may drop an unobserved frame. */
    @Volatile var onSensorEvent: ((SensorEvent) -> Unit)? = null
    @Volatile var lastSensorEvent: SensorEvent? = null
        private set

    private var commandChar: BluetoothGattCharacteristic? = null
    private var headlightChar: BluetoothGattCharacteristic? = null
    private var sensorChar: BluetoothGattCharacteristic? = null
    private var motorBoostChar: BluetoothGattCharacteristic? = null
    private var motorSlowChar: BluetoothGattCharacteristic? = null
    private var neckChar: BluetoothGattCharacteristic? = null

    /** Never interpret PLATFORM_COMPLETED as measured physical stopping. */
    val lastStopReceipt: BleStopReceipt get() = synchronized(session) { session.stopReceipt }

    /** Explicit user connection only; no retry or reconnect is scheduled here. */
    fun connectAuto(timeoutMs: Long = 20_000): Boolean {
        if (!RobotBaseControlPolicy.CONTROL_AVAILABLE) return false
        val (token, latch) = beginAttempt() ?: return false
        synchronized(session) {
            if (!session.acceptsAttempt(token)) return false
            if (adapter == null || !runCatching { adapter.isEnabled }.getOrDefault(false)) {
                failLocked(token, "Bluetooth off — cannot connect")
                return false
            }
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val alreadyConnected = runCatching {
                btManager.getConnectedDevices(BluetoothProfile.GATT)
                    .firstOrNull { it.name?.contains("LOOI", ignoreCase = true) == true }
            }.getOrNull()
            if (alreadyConnected != null) connectLocked(token, alreadyConnected.address)
            else startScanLocked(token)
        }
        return awaitAttempt(token, latch, timeoutMs)
    }

    /** Explicit direct connection; a timeout retires this attempt, not its successor. */
    fun connectBlocking(address: String, timeoutMs: Long = 20_000): Boolean {
        if (!RobotBaseControlPolicy.CONTROL_AVAILABLE) return false
        val (token, latch) = beginAttempt() ?: return false
        synchronized(session) { connectLocked(token, address) }
        return awaitAttempt(token, latch, timeoutMs)
    }

    private fun beginAttempt(): Pair<Long, CountDownLatch>? = synchronized(session) {
        val token = session.beginAttempt() ?: return@synchronized null
        stopScannerLocked()
        keepAliveJob?.cancel()
        readyLatch?.countDown()
        clearCharacteristicsLocked()
        val latch = CountDownLatch(1)
        readyLatch = latch
        setStateLocked(LinkState.CONNECTING, "starting explicit connection…")
        token to latch
    }

    private fun awaitAttempt(token: Long, latch: CountDownLatch, timeoutMs: Long): Boolean {
        val completed = try {
            latch.await(timeoutMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        return synchronized(session) {
            val ready = completed && session.acceptsAttempt(token) && connected
            if (!ready && session.acceptsAttempt(token)) failLocked(token, "connection did not complete")
            ready
        }
    }

    private fun startScanLocked(token: Long) {
        if (!session.acceptsAttempt(token)) return
        setStateLocked(LinkState.SCANNING, "scanning for LOOI…")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                synchronized(session) {
                    if (!session.acceptsAttempt(token) || scanCallback !== this ||
                        state != LinkState.SCANNING
                    ) return
                    val matches = runCatching {
                        matchesLooiName(result.device.name, result.scanRecord?.deviceName)
                    }.getOrDefault(false)
                    if (!matches) return
                    stopScannerLocked()
                    connectLocked(token, result.device.address)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                synchronized(session) {
                    if (!session.acceptsAttempt(token) || scanCallback !== this) return
                    failLocked(token, "scan failed $errorCode")
                }
            }
        }
        scanCallback = callback
        val started = runCatching {
            val scanner = adapter?.bluetoothLeScanner ?: return@runCatching false
            val settings = android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            // Only for this bounded explicit scan. An exact "LOOI" name filter
            // excludes bases advertising a suffix (for example looi_a444da).
            scanner.startScan(emptyList(), settings, callback)
            true
        }.getOrDefault(false)
        if (!started) {
            failLocked(token, "can't scan for a base")
            return
        }
        scanTimeoutJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            synchronized(session) {
                if (session.acceptsAttempt(token) && scanCallback === callback) {
                    failLocked(token, "no LOOI in range")
                }
            }
        }
    }

    private fun stopScannerLocked() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        val retired = scanCallback
        scanCallback = null
        if (retired != null) runCatching { adapter?.bluetoothLeScanner?.stopScan(retired) }
    }

    private fun connectLocked(token: Long, address: String) {
        if (!session.acceptsAttempt(token) || session.connection != null) return
        setStateLocked(LinkState.CONNECTING, "connecting to LOOI…")
        val connectedGatt = runCatching {
            adapter?.getRemoteDevice(address)?.connectGatt(context, false, callbackFor(token))
        }.getOrNull()
        if (connectedGatt == null) failLocked(token, "could not open a GATT connection")
        else session.bind(token, connectedGatt)
    }

    /**
     * Retire immediately without ANY characteristic write. An unverified stop
     * frame is itself unsafe. Radio disconnect does not prove the motor stopped;
     * the user must switch off a base that is still moving.
     */
    fun close() = synchronized(session) {
        stopScannerLocked()
        keepAliveJob?.cancel()
        session.revokeWithoutWrites()
        clearCharacteristicsLocked()
        setStateLocked(LinkState.OFFLINE, RobotBaseControlPolicy.STOP_MESSAGE)
    }

    private fun clearCharacteristicsLocked() {
        gattReachable = false
        commandChar = null; headlightChar = null; sensorChar = null
        motorBoostChar = null; motorSlowChar = null; neckChar = null
        lastSensorEvent = null
    }

    private fun setStateLocked(newState: LinkState, detail: String) {
        state = newState
        val revision = ++stateRevision
        if (newState == LinkState.READY || newState == LinkState.OFFLINE) readyLatch?.countDown()
        scope.launch {
            val callback = synchronized(session) {
                if (revision == stateRevision) onStateChange else null
            }
            runCatching { callback?.invoke(newState, detail) }
        }
    }

    private fun failLocked(token: Long, detail: String) {
        if (!session.abort(token)) return
        stopScannerLocked()
        keepAliveJob?.cancel()
        clearCharacteristicsLocked()
        setStateLocked(LinkState.OFFLINE, detail)
    }

    private fun syncPhaseLocked() {
        when (session.phase) {
            BleSessionLifecycle.Phase.READY -> if (state == LinkState.INITIALIZING) {
                setStateLocked(LinkState.READY, "GATT init complete")
                startKeepAliveLocked()
            }
            BleSessionLifecycle.Phase.CLOSED -> {
                keepAliveJob?.cancel()
                clearCharacteristicsLocked()
                if (state != LinkState.OFFLINE) setStateLocked(
                    LinkState.OFFLINE,
                    if (session.initializationFailed) "GATT setup incomplete; disconnected" else "link closed",
                )
            }
            else -> Unit
        }
    }

    private fun callbackFor(token: Long) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            synchronized(session) {
                if (session.generation != token || session.connection !== g) {
                    runCatching { g.close() }
                    return
                }
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    session.disconnected(g)
                    syncPhaseLocked()
                    return
                }
                // CLOSING accepts stop write completions, never reconnect/init.
                if (!session.accepts(g)) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    failLocked(token, "GATT connection failed ($status)")
                } else if (newState == BluetoothProfile.STATE_CONNECTED && !gattReachable) {
                    gattReachable = true
                    if (!runCatching { g.discoverServices() }.getOrDefault(false)) {
                        failLocked(token, "service discovery rejected")
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            synchronized(session) {
                if (session.generation != token || !session.accepts(g) ||
                    session.phase != BleSessionLifecycle.Phase.CONNECTING
                ) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    failLocked(token, "service discovery failed ($status)")
                    return
                }
                val operations = mutableListOf<BleSessionLifecycle.Operation<BluetoothGatt>>()
                for (service in g.services) for (ch in service.characteristics) {
                    when (ch.uuid) {
                        LooiBleProtocol.CHAR_COMMAND -> commandChar = ch
                        LooiBleProtocol.CHAR_HEADLIGHT -> headlightChar = ch
                        LooiBleProtocol.CHAR_SENSORS -> sensorChar = ch
                        LooiBleProtocol.CHAR_MOTOR_BOOST -> motorBoostChar = ch
                        LooiBleProtocol.CHAR_MOTOR_SLOW -> motorSlowChar = ch
                        LooiBleProtocol.CHAR_NECK -> neckChar = ch
                    }
                    val indicate = ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    val notify = ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    val descriptor = ch.getDescriptor(LooiBleProtocol.CCCD)
                    if ((notify || indicate) && descriptor != null) {
                        operations += BleSessionLifecycle.Operation(descriptorKey(descriptor)) { current ->
                            if (!current.setCharacteristicNotification(ch, true)) false else {
                                descriptor.value = if (indicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                current.writeDescriptor(descriptor)
                            }
                        }
                    }
                    if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                        operations += readOperation(ch)
                    }
                }
                if (commandChar == null) {
                    failLocked(token, "connected but command channel missing")
                    return
                }
                setStateLocked(LinkState.INITIALIZING, "subscribing characteristics…")
                session.initialize(g, operations)
                syncPhaseLocked()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            complete(token, g, descriptorKey(d), status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            complete(token, g, characteristicKey("write", ch), status)
        }

        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            onCharacteristicRead(g, ch, ch.value ?: byteArrayOf(), status)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) deliverSensor(token, g, ch, value)
            complete(token, g, characteristicKey("read", ch), status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            onCharacteristicChanged(g, ch, ch.value ?: byteArrayOf())
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            deliverSensor(token, g, ch, value)
        }
    }

    private fun deliverSensor(token: Long, g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
        val event = synchronized(session) {
            if (session.generation != token || !session.accepts(g) ||
                ch.uuid != LooiBleProtocol.CHAR_SENSORS
            ) return
            LooiSensorParser.parse(value).also { lastSensorEvent = it }
        }
        // Never acquire BodySafety's lock while holding this link's monitor.
        onSensorEvent?.invoke(event)
        _sensorFrames.tryEmit(value.copyOf())
    }

    private fun complete(token: Long, g: BluetoothGatt, key: String, status: Int) = synchronized(session) {
        if (session.generation != token) return@synchronized
        session.completed(g, key, status == BluetoothGatt.GATT_SUCCESS)
        syncPhaseLocked()
    }

    private fun readOperation(ch: BluetoothGattCharacteristic) =
        BleSessionLifecycle.Operation<BluetoothGatt>(characteristicKey("read", ch)) { it.readCharacteristic(ch) }

    private fun startKeepAliveLocked() {
        keepAliveJob?.cancel()
        val token = session.generation
        val readable = sensorChar?.takeIf {
            it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
        } ?: return
        keepAliveJob = scope.launch {
            while (true) {
                delay(30_000)
                synchronized(session) {
                    if (!session.acceptsAttempt(token)) return@launch
                    session.submit(readOperation(readable)) // Busy? Skip; never overlap a write.
                }
            }
        }
    }

    val connected: Boolean get() = synchronized(session) {
        session.connection != null && (session.phase == BleSessionLifecycle.Phase.READY ||
            session.phase == BleSessionLifecycle.Phase.STOPPING)
    }

    val connectedAddress: String? get() = synchronized(session) {
        if (connected) runCatching { session.connection?.device?.address }.getOrNull() else null
    }

    /** Historical script templates have no runtime write path, including zero-speed frames. */
    fun writeFrame(frame: LooiBleProtocol.Frame): Boolean = false

    @Volatile var turnEncoding: LooiBleProtocol.TurnEncoding = LooiBleProtocol.TurnEncoding.LEGACY

    fun turnPulse(speedPercent: Int, ticks: Int): Boolean = false

    fun driveSpeed(speed0to1: Float): Boolean = false

    /** No actuator is safe to admit while its stop behavior is unverified. */
    fun setNeckPitch(pitch: Float): Boolean = false

    fun setHeadlight(on: Boolean): Boolean = false

    /** Always unconfirmed: revoke control without emitting any actuator packet. */
    fun stopAll(): Boolean {
        close()
        return false
    }

    private fun characteristicKey(kind: String, ch: BluetoothGattCharacteristic): String =
        "$kind:${ch.uuid}:${ch.instanceId}"

    private fun descriptorKey(d: BluetoothGattDescriptor): String =
        "${characteristicKey("descriptor", d.characteristic)}:${d.uuid}"

    companion object {
        private const val TAG = "LooiBle"
        private const val SCAN_TIMEOUT_MS = 12_000L
        internal fun matchesLooiName(deviceName: String?, advertisedName: String?): Boolean =
            listOfNotNull(deviceName, advertisedName).any { it.contains("LOOI", ignoreCase = true) }
        /** Direction/timing and safety sensor protocol have not been validated. */
        const val WHEEL_CONTROL_VERIFIED = false
        const val KNOWN_LOOI_MAC = "EC:64:C9:AA:44:DA"
    }
}
