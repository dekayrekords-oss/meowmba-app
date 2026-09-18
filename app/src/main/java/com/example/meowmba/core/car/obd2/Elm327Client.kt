package com.example.meowmba.core.car.obd2

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.hardware.usb.UsbManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Minimal ELM327 classic Bluetooth (SPP) client. USB host path is detected and
 * reported but not fully driven without a CDC/ACM driver binding — callers get
 * an honest failure when only USB is present without a BT adapter address.
 *
 * Permissions: BLUETOOTH_CONNECT (API 31+) must already be granted.
 */
class Elm327Client(
    private val context: Context,
    private val adapterProvider: () -> BluetoothAdapter? = {
        @Suppress("DEPRECATION")
        BluetoothAdapter.getDefaultAdapter()
    },
) {
    private var socket: BluetoothSocket? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private val connected = AtomicBoolean(false)

    val isConnected: Boolean get() = connected.get()

    fun usbHostAvailable(): Boolean {
        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        return usb.deviceList.isNotEmpty()
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String): Result<Unit> = withContext(Dispatchers.IO) {
        disconnect()
        val clean = address.trim().uppercase()
        if (clean.isBlank()) return@withContext Result.failure(IllegalArgumentException("No OBD adapter address"))
        val adapter = adapterProvider()
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth unavailable"))
        if (!adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth is off"))
        }
        try {
            val device: BluetoothDevice = adapter.getRemoteDevice(clean)
            val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            @Suppress("DEPRECATION")
            adapter.cancelDiscovery()
            sock.connect()
            socket = sock
            writer = OutputStreamWriter(sock.outputStream)
            reader = BufferedReader(InputStreamReader(sock.inputStream))
            connected.set(true)
            // Reset + echo off + spaces on for readable hex
            at("ATZ")
            at("ATE0")
            at("ATL0")
            at("ATS0")
            at("ATH0")
            at("ATSP0")
            Result.success(Unit)
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    suspend fun poll(): Obd2Reading = withContext(Dispatchers.IO) {
        if (!connected.get()) {
            return@withContext Obd2Reading(lastError = "Not connected", updatedAtMs = System.currentTimeMillis())
        }
        var rpm = Float.NaN
        var speed = Float.NaN
        var coolant = Float.NaN
        var battery = Float.NaN
        var fuel = Float.NaN
        var mil = false
        var err = ""
        try {
            Obd2PidParser.parseRpm(at("010C"))?.let { rpm = it }
            Obd2PidParser.parseSpeedMph(at("010D"))?.let { speed = it }
            Obd2PidParser.parseCoolantC(at("0105"))?.let { coolant = it }
            Obd2PidParser.parseBatteryV(at("0142"))?.let { battery = it }
            Obd2PidParser.parseMil(at("0101"))?.let { mil = it }
            Obd2PidParser.parseFuelPercent(at("012F"))?.let { fuel = it }
        } catch (e: Exception) {
            err = e.message ?: "Poll failed"
        }
        Obd2Reading(
            rpm = rpm,
            speedMph = speed,
            coolantC = coolant,
            batteryV = battery,
            milOn = mil,
            fuelPercent = fuel,
            connected = connected.get(),
            adapterAddress = socket?.remoteDevice?.address.orEmpty(),
            lastError = err,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    fun disconnect() {
        connected.set(false)
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { socket?.close() }
        writer = null
        reader = null
        socket = null
    }

    private fun at(cmd: String): String {
        val w = writer ?: return ""
        val r = reader ?: return ""
        w.write(cmd.trim() + "\r")
        w.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + 1_500L
        while (System.currentTimeMillis() < deadline) {
            if (!r.ready()) {
                Thread.sleep(20)
                continue
            }
            val line = r.readLine() ?: break
            if (line.contains('>')) {
                sb.append(line.substringBefore('>'))
                break
            }
            sb.append(line).append(' ')
            if (sb.length > 512) break
        }
        return sb.toString()
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val PREF_ADAPTER = "obd2_adapter_address"
        const val PREF_ENABLED = "obd2_enabled"
    }
}
