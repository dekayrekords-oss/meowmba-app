package com.example.meowmba.core.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.*
import java.net.*
import java.security.SecureRandom
import javax.net.ssl.*

/**
 * Monitors USB connection and auto-pairs with the desktop bridge daemon
 * when the phone is tethered via USB.
 *
 * Detection strategy:
 *  1. [ACTION_POWER_CONNECTED] + [EXTRA_PLUGGED_USB] — the phone is
 *     physically plugged into a computer.
 *  2. Scan for the daemon on the USB tether subnet (192.168.42.x). The
 *     daemon broadcasts UDP presence every 2 s; we listen for it.
 *  3. When a daemon with an open pairing window is heard, auto-pair.
 *
 * On USB disconnect the bridge is unpaired automatically.
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
                    val plugged = intent.getIntExtra(
                        BatteryManager.EXTRA_PLUGGED, -1
                    )
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
                        Log.i(TAG, "USB disconnected — unpairing bridge")
                        pairedHost = null
                        bridgeManager.unpair()
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
            val plugged = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) != 0
            // Use the sticky intent instead for reliable USB detection
            val sticky = context.registerReceiver(null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val chargePlug = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            if (chargePlug == BatteryManager.BATTERY_PLUGGED_USB) {
                usbConnected = true
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

    /** True when the phone is currently plugged into USB (data connection). */
    fun isUsbConnected(): Boolean = usbConnected

    /** Host/port of the bridge auto-paired via USB, or null. */
    fun autoPairedHost(): String? = pairedHost

    // ------------------------------------------------------------------
    // Subnet scan
    // ------------------------------------------------------------------

    private fun startScan() {
        if (bridgeManager.isPaired()) {
            val current = bridgeManager.pairedBridge() ?: return
            // Already paired — check if it's reachable
            scanJob = scope.launch {
                val ok = bridgeManager.ping().isSuccess
                if (!ok) {
                    Log.w(TAG, "paired bridge ${current.host} unreachable via USB — re-pairing")
                    bridgeManager.unpair()
                    scanSubnet()
                }
            }
            return
        }
        scanJob = scope.launch { scanSubnet() }
    }

    private suspend fun scanSubnet() {
        // Phase 1: listen for UDP discovery broadcasts on the USB subnet.
        // The daemon broadcasts every 2 s, so 6 s covers ~3 rounds.
        val found = mutableMapOf<String, DesktopBridgeProtocol.DiscoveredBridge>()
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(BridgeDiscovery.DISCOVERY_PORT))
            soTimeout = 2000
        }
        val buf = ByteArray(4096)
        val deadline = System.currentTimeMillis() + 6000
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

        // Phase 2: for any found daemon, attempt USB auto-pair
        for ((_, d) in found) {
            if (!ctx.isActive) break
            Log.i(TAG, "USB auto-pairing with ${d.name} (${d.host}:${d.port})")
            val result = autoPair(d)
            if (result.isSuccess) {
                pairedHost = "${d.host}:${d.port}"
                Log.i(TAG, "USB auto-pair succeeded with ${d.name}")
                return
            }
            Log.w(TAG, "USB auto-pair with ${d.host} failed: ${result.exceptionOrNull()?.message}")
        }

        if (found.isEmpty()) {
            Log.w(TAG, "USB subnet scan found no bridge with an open pairing window")
        }
    }

    private suspend fun autoPair(
        discovered: DesktopBridgeProtocol.DiscoveredBridge
    ): Result<String> {
        val secret = java.security.SecureRandom().let { r ->
            ByteArray(32).also { r.nextBytes(it) }
        }
        val phoneName = "${android.os.Build.MODEL ?: "phone"} (USB)"
        val body = DesktopBridgeProtocol.buildAutoPairBody(
            secretB64 = DesktopBridgeProtocol.b64EncodeStd(secret),
            phoneName = phoneName
        )

        val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                post(discovered.host, discovered.port, "/pair", body,
                    discovered.certSha256)
            }
        }.getOrElse {
            return Result.failure(Exception("desktop unreachable: ${it.message}"))
        }

        return when (result.first) {
            200 -> {
                val desktopName = Json.parseToJsonElement(
                    String(result.second, Charsets.UTF_8)
                ).jsonObject["desktop_name"]?.jsonPrimitive?.content
                    ?: discovered.name
                // Save pairing
                bridgeManager.savePairing(
                    discovered.host, discovered.port, desktopName,
                    discovered.certSha256, DesktopBridgeProtocol.b64EncodeStd(secret)
                )
                Result.success(desktopName)
            }
            403 -> Result.failure(Exception("USB auto-pair rejected by daemon"))
            else -> Result.failure(Exception("auto-pair failed (http ${result.first})"))
        }
    }

    private fun post(
        host: String, port: Int, path: String, body: ByteArray,
        expectedCertSha256: String
    ): Pair<Int, ByteArray> {
        val trustManager = object : javax.net.ssl.X509TrustManager {
            override fun checkServerTrusted(
                chain: Array<java.security.cert.X509Certificate>, authType: String
            ) {
                val fp = DesktopBridgeProtocol.sha256Hex(chain[0].encoded)
                if (!fp.equals(expectedCertSha256, ignoreCase = true)) {
                    throw java.security.cert.CertificateException(
                        "cert fingerprint mismatch"
                    )
                }
            }
            override fun checkClientTrusted(
                chain: Array<java.security.cert.X509Certificate>, authType: String
            ) = throw java.security.cert.CertificateException("not used")
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }
        val sslCtx = javax.net.ssl.SSLContext.getInstance("TLS")
        sslCtx.init(null, arrayOf(trustManager), null)
        val conn = (java.net.URL("https://$host:$port$path").openConnection()
            as javax.net.ssl.HttpsURLConnection).apply {
            sslSocketFactory = sslCtx.socketFactory
            hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            connectTimeout = 5000
            readTimeout = 8000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body) }
        val code = conn.responseCode
        val bytes = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.use { it.readBytes() } ?: ByteArray(0)
        conn.disconnect()
        return code to bytes
    }

    private fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    companion object {
        private const val TAG = "MeowmbaUSB"
    }
}
