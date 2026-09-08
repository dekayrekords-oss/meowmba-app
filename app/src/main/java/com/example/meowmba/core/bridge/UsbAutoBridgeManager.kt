package com.example.meowmba.core.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.*
import java.net.*

/**
 * Monitors USB power and discovers the desktop bridge daemon
 * when the phone is tethered via USB.
 *
 * Detection strategy:
 *  1. [ACTION_POWER_CONNECTED] + [EXTRA_PLUGGED_USB] — the phone is
 *     physically plugged into a computer.
 *  2. Scan for the daemon on the USB tether subnet (192.168.42.x). The
 *     daemon broadcasts UDP presence every 2 s; we listen for it.
 *  3. Complete explicit code pairing in Settings; retain trust across disconnects.
 *
 * USB loss never removes trust. Only explicit Unpair clears a paired desktop.
 */
class UsbAutoBridgeManager(
    private val context: Context,
    private val bridgeManager: DesktopBridgeManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private var enabled = false
    private var usbConnected = false
    private var scanJob: Job? = null
    private var pairedHost: String? = null

    /** Called when USB connection status changes. */
    var onUsbStateChanged: ((connected: Boolean) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    val plugged = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                        ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    if (plugged == BatteryManager.BATTERY_PLUGGED_USB) {
                        Log.i(TAG, "USB power connected — scanning for bridge")
                        usbConnected = true
                        onUsbStateChanged?.invoke(true)
                        if (enabled) startScan()
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    val wasUsb = usbConnected
                    usbConnected = false
                    onUsbStateChanged?.invoke(false)
                    cancelScan()
                    if (wasUsb && enabled) {
                        Log.i(TAG, "USB disconnected — retaining trusted desktop pairing")
                        pairedHost = null
                    }
                }
            }
        }
    }

    /** Start monitoring. Call from the Activity's onCreate / onResume. */
    fun start() {
        if (enabled) return
        enabled = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)

        // Check current state (phone may already be plugged in)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (bm?.isCharging == true) {
            // Use the sticky intent instead for reliable USB detection
            val sticky = context.registerReceiver(null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val chargePlug = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            if (chargePlug == BatteryManager.BATTERY_PLUGGED_USB) {
                usbConnected = true
                onUsbStateChanged?.invoke(true)
                if (enabled) startScan()
            }
        }
    }

    /** Stop monitoring. Call from the Activity's onPause / onDestroy. */
    fun stop() {
        enabled = false
        cancelScan()
        runCatching { context.unregisterReceiver(receiver) }
    }

    /** True when USB power is reported; this alone does not prove a data connection. */
    fun isUsbConnected(): Boolean = usbConnected

    /** Host/port of the bridge auto-paired via USB, or null. */
    fun autoPairedHost(): String? = pairedHost

    // ------------------------------------------------------------------
    // Subnet scan
    // ------------------------------------------------------------------

    private fun startScan() {
        // A POWER_CONNECTED double-fire (or start() racing the sticky-battery
        // check) used to overwrite [scanJob] while the previous scan still held
        // UDP 8766: the replacement's bind then threw BindException straight
        // out of an unhandled coroutine and killed the process. Always cancel
        // the previous scan before starting a new one.
        cancelScan()
        if (bridgeManager.isPaired()) {
            val current = bridgeManager.pairedBridge() ?: return
            // Already paired — check if it's reachable
            scanJob = launchGuarded {
                val ok = bridgeManager.ping().isSuccess
                if (!ok) {
                    Log.w(TAG, "paired bridge ${current.host} unreachable — retaining pairing; check desktop/network")
                }
            }
            return
        }
        scanJob = launchGuarded { scanSubnet() }
    }

    /**
     * Launches on the manager scope with every failure logged. The default
     * scope has no exception handler, so anything thrown out of a plain
     * `launch` here crashed the whole process; nothing in this class throws
     * past this point on purpose.
     */
    private fun launchGuarded(block: suspend () -> Unit): Job =
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "USB bridge task failed", e)
            }
        }

    private suspend fun scanSubnet() {
        // Phase 1: listen for UDP discovery broadcasts on the USB subnet.
        // The daemon broadcasts every 2 s, so 6 s covers ~3 rounds.
        //
        // Construction + bind live INSIDE error handling: the port is shared
        // with BridgeDiscovery, and a concurrent holder turns bind() into a
        // BindException that must end the scan quietly, not crash the scope.
        val found = mutableMapOf<String, DesktopBridgeProtocol.DiscoveredBridge>()
        val buf = ByteArray(4096)
        val deadline = System.currentTimeMillis() + 6000
        val socket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(BridgeDiscovery.DISCOVERY_PORT))
                soTimeout = 2000
            }
        } catch (e: Exception) {
            Log.w(TAG, "discovery bind failed (${e.message}); skipping USB scan")
            return
        }
        try {
            val ctx = kotlinx.coroutines.currentCoroutineContext()
            while (ctx.isActive && System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val sender = packet.address?.hostAddress ?: continue
                    // Only accept broadcasts from the USB tether subnet
                    if (!sender.startsWith("192.168.42.")) continue
                    DesktopBridgeProtocol.parseDiscoveryPacket(text, sender)?.let {
                        if (it.pairingOpen) {
                            found[it.certSha256] = it
                        }
                    }
                } catch (_: SocketTimeoutException) { }
            }
        } finally {
            socket.close()
        }

        val ctx = kotlinx.coroutines.currentCoroutineContext()
        if (!ctx.isActive) return

        // Phase 2: discovery does not authorize trust or pairing.
        for ((_, d) in found) {
            if (!ctx.isActive) break
            Log.i(TAG, "Found ${d.name}; complete one-time code pairing in Desktop Bridge settings")
        }

        if (found.isEmpty()) {
            Log.w(TAG, "USB subnet scan found no bridge with an open pairing window")
        }
    }

    private fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    companion object {
        private const val TAG = "MeowmbaUSB"
    }
}
