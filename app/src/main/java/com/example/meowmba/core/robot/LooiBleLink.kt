package com.example.meowmba.core.robot

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * BLE link to the TangibleFuture LOOI base — the connection setup ported 1:1
 * from the Memba 2 droid section (LooiBleManager):
 *
 *  1. A BLE peripheral that is ALREADY connected (e.g. the official LOOI app
 *     holding the link) STOPS advertising — scanning will never find it. So
 *     first look at what is already connected to the phone and attach a second
 *     GATT client directly.
 *  2. Otherwise scan for a device named "*LOOI*"; on timeout fall back to a
 *     direct connectGatt to [KNOWN_LOOI_MAC] (works without advertising).
 *  3. connectGatt(autoConnect=false) -> discoverServices -> map the six known
 *     characteristics -> run a serial init queue that subscribes to every
 *     notify/indicate characteristic (CCCD write) and reads every readable one.
 *  4. Keep-alive: poll a READ on the sensor characteristic every 3s (reads can
 *     never move a motor).
 *
 * Motion channels (hardware-verified in the Memba 2 work):
 *  - fe00: 17-byte Script-Mode frames with rolling SEQ -> TURNS the base
 *  - fed0: single speed byte 0x00..0xFF -> proportional FORWARD drive; 0x00 stops
 *  - fed1: single POSITION byte for the neck (0x40 center; clamp 0x18..0xF0)
 *  - fed2: headlight 0x00 off / 0x03 on
 *  - ff02: hardware brake/stop: write 0x00
 */
class LooiBleLink(private val context: Context) {

    enum class LinkState { OFFLINE, SCANNING, CONNECTING, INITIALIZING, VERIFYING, HANDSHAKE_REQUIRED, READY }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    @Volatile private var gatt: BluetoothGatt? = null
    private val seq = LooiBleProtocol.SequenceCounter()
    @Volatile private var connectRetries = 0
    private var verifyJob: kotlinx.coroutines.Job? = null
    private val MAX_CONNECT_RETRIES = 2

    @Volatile var state: LinkState = LinkState.OFFLINE
        private set
    /** Fired on notable transitions so RobotManager can mirror it to the UI. */
    var onStateChange: ((LinkState, String) -> Unit)? = null

    private var commandChar: BluetoothGattCharacteristic? = null
    private var headlightChar: BluetoothGattCharacteristic? = null
    private var sensorChar: BluetoothGattCharacteristic? = null
    private var motorBoostChar: BluetoothGattCharacteristic? = null
    private var motorSlowChar: BluetoothGattCharacteristic? = null
    private var neckChar: BluetoothGattCharacteristic? = null

    // Android runs at most ONE async GATT op at a time — firing them
    // concurrently silently drops all but the first (lesson from Memba 2).
    private val gattOps = ArrayDeque<() -> Boolean>()
    private var gattOpInFlight = false
    private var drainingInit = false
    private var keepAliveJob: kotlinx.coroutines.Job? = null
    private val writeLock = Any()

    @Volatile private var readyLatch: CountDownLatch? = null

    // ── Connection ───────────────────────────────────────────────────────

    /** Full Memba-2 scan policy. Blocks up to [timeoutMs] waiting for READY. */
    @SuppressLint("MissingPermission")
    fun connectAuto(timeoutMs: Long = 20_000): Boolean {
        val latch = CountDownLatch(1)
        readyLatch = latch
        if (adapter == null || !adapter.isEnabled) {
            onStateChange?.invoke(LinkState.OFFLINE, "Bluetooth off — cannot connect")
            return false
        }
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val alreadyConnected = btManager.getConnectedDevices(BluetoothProfile.GATT)
            .firstOrNull { it.name?.contains("LOOI", ignoreCase = true) == true }
        if (alreadyConnected != null) {
            Log.i(TAG, "LOOI already connected to phone (${alreadyConnected.address}) — attaching directly")
            connect(alreadyConnected.address)
        } else {
            startScan()
        }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS) && state == LinkState.READY
    }

    /** Direct connect to a known address; blocks up to [timeoutMs] for READY. */
    fun connectBlocking(address: String, timeoutMs: Long = 20_000): Boolean {
        val latch = CountDownLatch(1)
        readyLatch = latch
        connect(address)
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS) && state == LinkState.READY
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        setState(LinkState.SCANNING, "scanning for LOOI…")
        adapter?.bluetoothLeScanner?.startScan(scanCallback)
        scope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (state == LinkState.SCANNING) {
                Log.w(TAG, "LOOI scan timed out — trying direct connect to $KNOWN_LOOI_MAC")
                stopScan()
                connect(KNOWN_LOOI_MAC)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        if (state == LinkState.SCANNING) setState(LinkState.OFFLINE, "scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: ""
            if (name.contains("LOOI", ignoreCase = true)) {
                Log.i(TAG, "LOOI found: $name (${result.device.address})")
                stopScan()
                connect(result.device.address)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: error code $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun connect(address: String) {
        if (gatt != null) return
        setState(LinkState.CONNECTING, "connecting to $address")
        gatt = adapter?.getRemoteDevice(address)?.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun close() {
        stopScan()
        keepAliveJob?.cancel()
        gattOps.clear(); gattOpInFlight = false; drainingInit = false
        val g = gatt
        gatt = null
        commandChar = null; headlightChar = null; sensorChar = null
        motorBoostChar = null; motorSlowChar = null; neckChar = null
        setState(LinkState.OFFLINE, "closed")
        if (g != null) {
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
        readyLatch?.countDown()
    }

    private fun setState(newState: LinkState, detail: String) {
        state = newState
        onStateChange?.invoke(newState, detail)
        if (newState == LinkState.READY || newState == LinkState.OFFLINE || newState == LinkState.HANDSHAKE_REQUIRED) {
            readyLatch?.countDown()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (this@LooiBleLink.gatt !== g) {
                runCatching { g.disconnect() }; runCatching { g.close() }
                return
            }
            // GATT error codes (status != 0): 8=timeout, 11=internal error, 133=device not found
            if (status != BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "LOOI GATT error: status=0 (retry /)")
                runCatching { g.close() }
                gatt = null
                commandChar = null; headlightChar = null; sensorChar = null
                motorBoostChar = null; motorSlowChar = null; neckChar = null
                if (connectRetries < MAX_CONNECT_RETRIES) {
                    connectRetries++
                    val addr = g.device?.address ?: KNOWN_LOOI_MAC
                    setState(LinkState.CONNECTING, "GATT error 0 — retrying ()…")
                    scope.launch {
                        delay(1500L * connectRetries) // backoff
                        connect(addr)
                    }
                } else {
                    connectRetries = 0
                    setState(LinkState.OFFLINE, "GATT error 0 after  retries")
                }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectRetries = 0
                Log.i(TAG, "LOOI GATT connected, discovering services")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                keepAliveJob?.cancel()
                verifyJob?.cancel()
                gatt = null
                commandChar = null; headlightChar = null; sensorChar = null
                motorBoostChar = null; motorSlowChar = null; neckChar = null
                setState(LinkState.OFFLINE, "disconnected")
            }
        }

        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (this@LooiBleLink.gatt !== g) { runCatching { g.close() }; return }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setState(LinkState.OFFLINE, "service discovery failed ($status)")
                teardown(g)
                return
            }
            for (service in g.services) {
                for (ch in service.characteristics) {
                    when (ch.uuid) {
                        LooiBleProtocol.CHAR_COMMAND -> commandChar = ch
                        LooiBleProtocol.CHAR_HEADLIGHT -> headlightChar = ch
                        LooiBleProtocol.CHAR_SENSORS -> sensorChar = ch
                        LooiBleProtocol.CHAR_MOTOR_BOOST -> motorBoostChar = ch
                        LooiBleProtocol.CHAR_MOTOR_SLOW -> motorSlowChar = ch
                        LooiBleProtocol.CHAR_NECK -> neckChar = ch
                    }
                }
            }
            Log.i(
                TAG,
                "LOOI chars: cmd/fe00=${commandChar != null} neck/fed1=${neckChar != null} " +
                    "slow/fed0=${motorSlowChar != null} boost/ff02=${motorBoostChar != null} " +
                    "light/fed2=${headlightChar != null} sensors/fed9=${sensorChar != null}"
            )
            if (commandChar == null) {
                setState(LinkState.OFFLINE, "connected but command channel missing")
                return
            }
            // Standard GATT initialization: subscribe to every notify/indicate
            // characteristic and read every readable one, SERIALLY.
            setState(LinkState.INITIALIZING, "subscribing characteristics…")
            gattOps.clear()
            for (service in g.services) {
                for (ch in service.characteristics) {
                    val props = ch.properties
                    val canNotify = props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    val canIndicate = props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    if (canNotify || canIndicate) {
                        gattOps.add {
                            g.setCharacteristicNotification(ch, true)
                            val d = ch.getDescriptor(LooiBleProtocol.CCCD)
                            if (d != null) {
                                d.value = if (canIndicate)
                                    android.bluetooth.BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                else
                                    android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                g.writeDescriptor(d)
                            } else false
                        }
                    }
                    if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                        gattOps.add { g.readCharacteristic(ch) }
                    }
                }
            }
            Log.i(TAG, "LOOI direct init: ${gattOps.size} GATT ops queued")
            drainingInit = true
            drainGattOps(g)
        }

        @Suppress("DEPRECATION")
        override fun onDescriptorWrite(g: BluetoothGatt, d: android.bluetooth.BluetoothGattDescriptor, status: Int) {
            if (this@LooiBleLink.gatt !== g) return
            if (drainingInit) advanceGattOps(g)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            if (this@LooiBleLink.gatt !== g) return
            if (drainingInit) advanceGattOps(g)
        }
    }

    @SuppressLint("MissingPermission")
    private fun drainGattOps(g: BluetoothGatt) {
        if (gattOpInFlight) return
        val op = gattOps.removeFirstOrNull()
        if (op == null) {
            drainingInit = false
            startVerification()
            return
        }
        gattOpInFlight = true
        val async = runCatching { op() }.getOrDefault(false)
        if (!async) advanceGattOps(g)
    }

    private fun advanceGattOps(g: BluetoothGatt) {
        gattOpInFlight = false
        drainGattOps(g)
    }

    /** Poll a READ every 3s so the link stays warm; reads never move motors. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        val readable = sensorChar?.takeIf {
            it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
        } ?: return
        keepAliveJob = scope.launch {
            while (true) {
                delay(3_000)
                val g = gatt ?: break
                if (!gattOpInFlight) runCatching { g.readCharacteristic(readable) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun teardown(g: BluetoothGatt) {
        if (gatt === g) gatt = null
        runCatching { g.disconnect() }
        runCatching { g.close() }
        readyLatch?.countDown()
    }

    // ── Safe link verification (ported from Memba 2 LooiBleManager) ─────

    /**
     * After GATT init completes, verify the link accepts neutral writes.
     * The LOOI base may ignore commands until a proprietary handshake has run
     * (observed: GATT error 11, writes silently dropped). This sends safe neutral
     * commands (brake, zero drive, center neck) to confirm the command path works.
     */
    private fun startVerification() {
        verifyJob?.cancel()
        setState(LinkState.VERIFYING, "verifying command path…")
        verifyJob = scope.launch {
            delay(600) // let GATT settle
            val accepted = verifySafeCommandPath()
            if (accepted) {
                setState(LinkState.READY, "verified — link ready")
                startKeepAlive()
            } else {
                setState(LinkState.HANDSHAKE_REQUIRED, "command path rejected — retry or use official app first")
            }
        }
    }

    /**
     * Write neutral commands that CANNOT move the robot:
     * 1. ff02 <- 0x00 (brake)
     * 2. fed0 <- 0x00 (zero drive speed)
     * 3. fed1 <- 0x40 (neck center)
     *
     * Returns true if all writes are locally accepted by Android's GATT stack.
     * This does NOT guarantee the robot firmware acknowledged them (no-response writes
     * never confirm), but a rejected write here means the BLE link is unusable.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun verifySafeCommandPath(): Boolean = synchronized(writeLock) {
        val g = gatt ?: return false
        var allOk = true
        // 1. Brake on ff02
        motorBoostChar?.let { ch ->
            val noResp = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            val write = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            if (noResp || write) {
                ch.writeType = if (noResp) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ch.value = byteArrayOf(0x00)
                if (!runCatching { g.writeCharacteristic(ch) }.getOrDefault(false)) allOk = false
            }
        }
        Thread.sleep(100)
        // 2. Zero drive on fed0
        motorSlowChar?.let { ch ->
            val noResp = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            val write = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            if (noResp || write) {
                ch.writeType = if (noResp) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ch.value = byteArrayOf(0x00)
                if (!runCatching { g.writeCharacteristic(ch) }.getOrDefault(false)) allOk = false
            }
        }
        Thread.sleep(100)
        // 3. Neck center on fed1
        neckChar?.let { ch ->
            val noResp = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            val write = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            if (noResp || write) {
                ch.writeType = if (noResp) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ch.value = byteArrayOf(0x40)
                if (!runCatching { g.writeCharacteristic(ch) }.getOrDefault(false)) allOk = false
            }
        }
        Log.i(TAG, "LOOI verification: neutral writes accepted=")
        allOk
    }

    /**
     * Manual retry — call from UI "Initialize" / "Retry" button.
     * Re-runs the safe verification step without re-doing the full BLE scan.
     */
    fun retryHandshake() {
        if (state == LinkState.HANDSHAKE_REQUIRED || state == LinkState.VERIFYING) {
            startVerification()
        } else if (state == LinkState.OFFLINE) {
            // Full reconnect
            connectAuto()
        }
    }

    // ── Motion channels ──────────────────────────────────────────────────

    val connected: Boolean get() = state == LinkState.READY
    val verifying: Boolean get() = state == LinkState.VERIFYING
    val handshakeRequired: Boolean get() = state == LinkState.HANDSHAKE_REQUIRED

    /** Script-Mode frame on fe00 with the rolling SEQ (turns/animations). */
    fun writeFrame(frame: LooiBleProtocol.Frame): Boolean = synchronized(writeLock) {
        val g = gatt ?: return false
        val ch = commandChar ?: return false
        val bytes = LooiBleProtocol.encode(frame, seq.next())
        ch.value = bytes
        val noResp = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val write = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        ch.writeType = when {
            frame.value == 0 && write -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            noResp -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            write -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            else -> return false
        }
        runCatching { g.writeCharacteristic(ch) }.getOrDefault(false)
    }

    /** Turn the base via the verified rotation frame. ticks ~ 20ms each. */
    fun turnPulse(speedPercent: Int, ticks: Int): Boolean =
        writeFrame(LooiBleProtocol.rotateFrame(speedPercent, ticks))

    /** Proportional FORWARD drive on fed0 (the verified drive channel). */
    fun driveSpeed(speed0to1: Float): Boolean =
        writeMotor(motorSlowChar, intArrayOf((speed0to1.coerceIn(0f, 1f) * 0xFF).toInt()))

    /** Neck position byte on fed1; pitch -1=up, 0=center, +1=down. */
    fun setNeckPitch(pitch: Float): Boolean {
        val p = pitch.coerceIn(-1f, 1f)
        val byte = if (p <= 0f)
            (NECK_CENTER + p * (NECK_CENTER - NECK_UP_SAFE)).toInt()
        else
            (NECK_CENTER + p * (NECK_DOWN - NECK_CENTER)).toInt()
        return writeMotor(neckChar, intArrayOf(byte.coerceIn(NECK_UP_SAFE, NECK_DOWN)))
    }

    fun setHeadlight(on: Boolean): Boolean =
        writeMotor(headlightChar, intArrayOf(if (on) 0x03 else 0x00))

    /** Brake/stop: ff02 <- 0x00 plus fed0 <- 0x00. */
    fun stopAll(): Boolean {
        val brake = writeMotor(motorBoostChar, intArrayOf(0x00))
        val halt = writeMotor(motorSlowChar, intArrayOf(0x00))
        return brake || halt
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun writeMotor(ch: BluetoothGattCharacteristic?, values: IntArray): Boolean =
        synchronized(writeLock) {
            val g = gatt ?: return false
            ch ?: return false
            val noResp = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            val write = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            ch.writeType = when {
                noResp -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                write -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                else -> return false
            }
            ch.value = ByteArray(values.size) { (values[it] and 0xFF).toByte() }
            runCatching { g.writeCharacteristic(ch) }.getOrDefault(false)
        }

    companion object {
        private const val TAG = "LooiBle"
        private const val SCAN_TIMEOUT_MS = 12_000L

        // The user's robot ("looi_a444da" in nRF Connect). Direct-connect
        // fallback when scanning finds nothing (robot already connected to
        // another central and not advertising). Same constant as Memba 2.
        const val KNOWN_LOOI_MAC = "EC:64:C9:AA:44:DA"

        // fed1 neck POSITION byte (decoded 2026-07-06): 0x40 center; clamp the
        // top end so look-up never slams the mechanical stop.
        private const val NECK_UP_SAFE = 0x18
        private const val NECK_CENTER = 0x40
        private const val NECK_DOWN = 0xF0
    }
}
