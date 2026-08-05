package com.example.meowmba.core.robot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the active [MotorController] and honestly reports which body is live.
 *
 * Default is the [SimulatedMotorController] (no hardware). When the user enables
 * the robot body and a base is reachable, [connectLooi] swaps in a
 * [LooiMotorController]; if the base is unreachable we fall back to simulated
 * and say so in [status] rather than pretending.
 */
class RobotManager {
    private val _status = MutableStateFlow("simulated (no body)")
    val status: StateFlow<String> = _status.asStateFlow()

    @Volatile private var controller: MotorController = SimulatedMotorController()
    private var transport: RobotTransport? = null

    /** The controller the companion should drive right now. Never null. */
    val active: MotorController get() = controller

    /** Drop any connection and return to the honest simulated body. */
    fun useSimulated() {
        transport?.close()
        transport = null
        controller = SimulatedMotorController()
        _status.value = "simulated (no body)"
    }

    /**
     * Try to bring up a LOOI base at [host]:[port]. Returns true when the body
     * is live; on failure falls back to simulated and reports it in [status].
     */
    fun connectLooi(host: String, port: Int): Boolean =
        bringUp(TcpRobotTransport(host, port), "LOOI body @ $host:$port",
            "LOOI unreachable ($host:$port) - simulated")

    /**
     * Auto-discovery: the first bonded (paired) device whose name looks like a LOOI
     * base, so the body can connect without the user typing a MAC. Returns null when
     * nothing is paired or the BLUETOOTH_CONNECT permission is missing.
     */
    fun discoverLooiAddress(): String? = try {
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return null
        adapter.bondedDevices?.firstOrNull { d ->
            val n = d.name ?: ""
            n.contains("looi", ignoreCase = true) || n.contains("meow", ignoreCase = true)
        }?.address
    } catch (e: Exception) {
        null
    }

    /**
     * Full-spectrum discovery for the Settings picker. Returns "name -> address"
     * pairs: bonded devices first (a base already paired at the system level), then
     * anything a bounded BLE scan sees. Requires BLUETOOTH_SCAN (manifest,
     * neverForLocation) and BLUETOOTH_CONNECT for names; returns whatever it can
     * rather than nothing — a missing permission degrades, it does not blank the
     * list. The scan runs up to [scanMs] and is stopped before returning.
     */
    fun discoverAllBases(scanMs: Long = 7_000): List<Pair<String, String>> {
        val out = LinkedHashMap<String, String>()   // address -> name, dedup
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
            adapter.bondedDevices?.forEach { d ->
                runCatching { out[d.address] = d.name ?: d.address }
            }
            val scanner = if (adapter.isEnabled) {
                runCatching { adapter.bluetoothLeScanner }.getOrNull()
            } else null
            if (scanner != null) {
                val latch = java.util.concurrent.CountDownLatch(1)
                val callback = object : android.bluetooth.le.ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                        runCatching {
                            val d = result.device
                            if (d.address !in out) out[d.address] = result.scanRecord?.deviceName ?: d.address
                        }
                    }
                    override fun onScanFailed(errorCode: Int) { latch.countDown() }
                }
                try {
                    scanner.startScan(callback)
                    latch.await(scanMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    runCatching { scanner.stopScan(callback) }
                } catch (e: Exception) {
                    runCatching { scanner.stopScan(callback) }
                }
            }
        } catch (e: Exception) {
            // SecurityException on any missing permission: keep what we have.
        }
        // Likely bases first so the user sees them without scrolling.
        return out.entries
            .map { (addr, name) -> name to addr }
            .sortedWith(compareByDescending<Pair<String, String>> {
                val n = it.first.lowercase()
                "looi" in n || "meow" in n || "robot" in n || "luna" in n
            }.thenBy { it.first.lowercase() })
    }

    /** Bring up a LOOI base over classic Bluetooth (RFCOMM/SPP) to a paired device. */
    fun connectBluetooth(address: String): Boolean =
        bringUp(BluetoothRobotTransport(address), "LOOI body (BT) @ $address",
            "LOOI BT unreachable ($address) - simulated")

    private fun bringUp(t: RobotTransport, okLabel: String, failLabel: String): Boolean =
        if (t.connect()) {
            transport?.close()
            transport = t
            controller = LooiMotorController(t)
            _status.value = okLabel
            true
        } else {
            t.close()
            transport?.close()
            transport = null
            controller = SimulatedMotorController()
            _status.value = failLabel
            false
        }

    fun close() {
        transport?.close()
        transport = null
    }
}
