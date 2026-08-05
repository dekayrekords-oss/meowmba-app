package com.example.meowmba.core.robot

import java.net.InetSocketAddress
import java.net.Socket

/**
 * A byte-pipe to the robot base. The [MotorController] implementations only
 * know how to format frames; the transport knows how to deliver them.
 *
 * Two implementations ship: [TcpRobotTransport] for a real base reachable on
 * the network (a LOOI dock exposes a serial-over-TCP bridge), and
 * [LoopbackTransport] which records what it was sent — used by unit tests and
 * by the "demo body" so the pipeline is observable without hardware.
 */
interface RobotTransport {
    val connected: Boolean
    fun connect(): Boolean
    fun send(line: String)
    fun close()
}

/** Serial-over-TCP bridge to a physical robot base. Thread-safe writer. */
class TcpRobotTransport(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = 1500,
) : RobotTransport {
    private var socket: Socket? = null
    private val lock = Any()

    override val connected: Boolean
        get() = socket?.let { it.isConnected && !it.isClosed } ?: false

    override fun connect(): Boolean = synchronized(lock) {
        try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), connectTimeoutMs)
            s.tcpNoDelay = true
            socket = s
            true
        } catch (e: Exception) {
            socket = null
            false
        }
    }

    override fun send(line: String) = synchronized(lock) {
        val s = socket ?: return
        try {
            s.getOutputStream().write((line + "\n").toByteArray(Charsets.US_ASCII))
            s.getOutputStream().flush()
        } catch (e: Exception) {
            // A dropped base must not take the companion down with it.
            close()
        }
    }

    override fun close() = synchronized(lock) {
        runCatching { socket?.close() }
        socket = null
    }
}

/**
 * Classic-Bluetooth (RFCOMM / SPP) pipe to a paired LOOI base — the real hardware
 * path. The base exposes the standard serial-port profile; we open the RFCOMM
 * socket and speak [LooiProtocol] lines over it.
 *
 * Requires BLUETOOTH_CONNECT (runtime on Android 12+) and a paired device.
 */
class BluetoothRobotTransport(private val address: String) : RobotTransport {
    private var socket: android.bluetooth.BluetoothSocket? = null
    private val lock = Any()

    override val connected: Boolean
        get() = socket?.let { it.isConnected } ?: false

    override fun connect(): Boolean = synchronized(lock) {
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                ?: return false
            if (!adapter.isEnabled) return false
            val device = adapter.getRemoteDevice(address)
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            s.connect()
            socket = s
            true
        } catch (e: Exception) {
            socket = null
            false
        }
    }

    override fun send(line: String) = synchronized(lock) {
        val s = socket ?: return
        try {
            s.outputStream.write((line + "\n").toByteArray(Charsets.US_ASCII))
            s.outputStream.flush()
        } catch (e: Exception) {
            close()
        }
    }

    override fun close() = synchronized(lock) {
        runCatching { socket?.close() }
        socket = null
    }

    companion object {
        /** Standard Serial Port Profile UUID used by LOOI-style serial bridges. */
        val SPP_UUID: java.util.UUID =
            java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

/** Records sent frames; for tests and the no-hardware demo body. */
class LoopbackTransport : RobotTransport {
    val sent = mutableListOf<String>()
    override val connected: Boolean = true
    override fun connect(): Boolean = true
    override fun send(line: String) { sent.add(line) }
    override fun close() {}
}
