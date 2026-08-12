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

    enum class LinkState { OFFLINE, SCANNING, CONNECTING, INITIALIZING, READY }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    @Volatile private var gatt: BluetoothGatt? = null
    private val seq = LooiBleProtocol.SequenceCounter()

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
        Log.i(TAG, "LOOI connectAuto: looking for base (known MAC $KNOWN_LOOI_MAC)")
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val alreadyConnected = btManager.getConnectedDevices(BluetoothProfile.GATT)
            .firstOrNull { it.name?.contains("LOOI", ignoreCase = true) == true }
        if (alreadyConnected != null) {
            Log.i(TAG, "LOOI already connected to phone (${alreadyConnected.address}) — attaching directly")
            connect(alreadyConnected.address)
        } else {
            startScan()
        }
        // Only READY releases the latch (setState no longer counts down on
        // OFFLINE). Otherwise stopScan()/scan-timeout would flip OFFLINE,
        // release the latch, make this return false while the direct-connect
        // fallback was still in flight, and connectLooiBle() would close the
        // GATT it had just opened.
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS) && state == LinkState.READY
    }

    /** Direct connect to a known address; blocks up to [timeoutMs] for READY. */
    fun connectBlocking(address: String, timeoutMs: Long = 20_000): Boolean {
        Log.i(TAG, "LOOI direct connect to $address")
        val latch = CountDownLatch(1)
        readyLatch = latch
        connect(address)
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS) && state == LinkState.READY
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        setState(LinkState.SCANNING, "scanning for LOOI…")
        val started = runCatching { adapter?.bluetoothLeScanner?.startScan(scanCallback) }.isSuccess
        if (!started) {
            Log.w(TAG, "BLE scan unavailable (permission/stack) — direct connect to $KNOWN_LOOI_MAC")
            stopScanner()
            connect(KNOWN_LOOI_MAC)
            return
        }
        scope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (state == LinkState.SCANNING) {
                Log.w(TAG, "LOOI scan timed out — trying direct connect to $KNOWN_LOOI_MAC")
                stopScanner()
                connect(KNOWN_LOOI_MAC)
            }
        }
    }

    /** Stop the BLE scanner without touching link state. */
    private fun stopScanner() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: ""
            if (name.contains("LOOI", ignoreCase = true)) {
                Log.i(TAG, "LOOI found: $name (${result.device.address})")
                stopScanner()
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
        stopScanner()
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
        // Release the connect latch on READY and on OFFLINE. The old bug was
        // that stopScan() emitted OFFLINE while a direct-connect fallback was
        // still in flight, which made connectAuto() return false and caused
        // the caller to close the GATT it had just opened. stopScan() no
        // longer touches link state, so OFFLINE now only means a definitive
        // GATT failure/disconnect — releasing then is correct and lets
        // connectBlocking() return promptly instead of burning its timeout.
        if (newState == LinkState.READY || newState == LinkState.OFFLINE) {
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
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "LOOI GATT connected, discovering services")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                keepAliveJob?.cancel()
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
            setState(LinkState.READY, "GATT init complete")
            startKeepAlive()
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

    // ── Motion channels ──────────────────────────────────────────────────

    val connected: Boolean get() = state == LinkState.READY

    /**
     * MAC of the device this link is actually attached to, or null when not
     * connected. The requested address and the connected one diverge whenever
     * the auto/known-MAC fallback wins, and reporting the request instead of
     * the reality made a healthy base look like the wrong device.
     */
    val connectedAddress: String?
        @SuppressLint("MissingPermission")
        get() = if (state == LinkState.READY) {
            runCatching { gatt?.device?.address }.getOrNull()
        } else null

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

    /** Raw write to fe00 (captured turn scripts are 20 bytes, not 17). */
    private fun writeRaw(bytes: ByteArray): Boolean = synchronized(writeLock) {
        val g = gatt ?: return false
        val ch = commandChar ?: return false
        ch.value = bytes
        val noResp = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val write = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        ch.writeType = when {
            noResp -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            write -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            else -> return false
        }
        runCatching { g.writeCharacteristic(ch) }.getOrDefault(false)
    }

    /**
     * Turn the base. 17-byte 0x07 + positive value is the verified LEFT.
     * RIGHT uses two's-complement value on the same template (subOp 0x01
     * still spun left on this robot).
     */
    fun turnPulse(speedPercent: Int, ticks: Int): Boolean {
        val mag = kotlin.math.abs(speedPercent)
        if (mag == 0) return writeFrame(LooiBleProtocol.rotateFrame(0, 1, right = false))
        val right = speedPercent > 0
        val frame = LooiBleProtocol.rotateFrame(mag, ticks, right = right)
        android.util.Log.i(
            "LooiBle",
            "turn ${if (right) "RIGHT" else "LEFT"} mag=$mag value=${frame.value} ticks=$ticks",
        )
        return writeFrame(frame)
    }

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
