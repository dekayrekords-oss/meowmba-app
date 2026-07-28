package com.example.meowmba.core.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * LAN discovery for meowmba-bridge daemons (Phase 6 pairing UX).
 *
 * The desktop broadcasts a small JSON presence packet (name, port, cert
 * fingerprint, pairing-window state) over UDP every 2s. This listens for a
 * few seconds and returns every distinct daemon heard, keyed by cert
 * fingerprint so repeats collapse.
 *
 * Honest limits: LAN broadcast only — VPNs, guest networks, and emulators
 * may not deliver it; an empty result means "nothing heard", not "no desktop".
 */
class BridgeDiscovery {

    suspend fun discover(timeoutMs: Long = 6000): List<DesktopBridgeProtocol.DiscoveredBridge> =
        withContext(Dispatchers.IO) {
            val found = linkedMapOf<String, DesktopBridgeProtocol.DiscoveredBridge>()
            val socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(DISCOVERY_PORT))
                soTimeout = 400
            }
            val buf = ByteArray(4096)
            val deadline = System.currentTimeMillis() + timeoutMs
            try {
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val sender = packet.address?.hostAddress ?: continue
                        DesktopBridgeProtocol.parseDiscoveryPacket(text, sender)?.let {
                            found[it.certSha256] = it
                        }
                    } catch (_: SocketTimeoutException) {
                        // keep listening until the overall deadline
                    }
                }
            } finally {
                socket.close()
            }
            found.values.toList()
        }

    companion object {
        const val DISCOVERY_PORT = 8766
    }
}
