package com.example.meowmba.core.bridge

import java.io.IOException
import javax.net.ssl.SSLException

/**
 * An adb reverse tunnel reaches the same paired desktop through phone loopback.
 * Only connection setup may fail over: retrying a POST after a lost response
 * could repeat a Logic edit or another desktop action. Each connection still
 * verifies the stored certificate, and each request retains its HMAC signature.
 */
internal class BridgeTransportFallback {
    @Volatile private var remembered: Pair<String, String>? = null

    suspend fun <C, T> execute(
        identity: String,
        networkHost: String,
        connect: suspend (String) -> C,
        send: suspend (C) -> T,
    ): T {
        val preferred = remembered?.takeIf { it.first == identity }?.second ?: networkHost
        val hosts = listOf(preferred, networkHost, "127.0.0.1").distinct()
        var lastFailure: IOException? = null
        for (host in hosts) {
            val connection = try {
                connect(host)
            } catch (e: SSLException) {
                // A failed trust check is not an invitation to try other peers.
                throw e
            } catch (e: IOException) {
                lastFailure = e
                continue
            }
            remembered = identity to host
            // Deliberately outside the connection catch: never replay a request.
            return send(connection)
        }
        throw IOException("Network and USB bridge connections failed: ${lastFailure?.message}", lastFailure)
    }
}
