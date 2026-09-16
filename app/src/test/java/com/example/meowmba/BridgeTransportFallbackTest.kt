package com.example.meowmba

import com.example.meowmba.core.bridge.BridgeTransportFallback
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BridgeTransportFallbackTest {
    @Test fun networkFailureUsesUsbAndRemembersTheWorkingRoute() = runBlocking {
        val transport = BridgeTransportFallback()
        val connected = mutableListOf<String>()
        val connect: suspend (String) -> String = {
            connected += it
            if (it == "mac") throw IOException("offline")
            it
        }
        repeat(2) { assertEquals("127.0.0.1", transport.execute("pair", "mac", connect) { it }) }
        assertEquals(listOf("mac", "127.0.0.1", "127.0.0.1"), connected)
    }

    @Test fun workingNetworkDoesNotProbeUsb() = runBlocking {
        val connected = mutableListOf<String>()
        assertEquals("ok", BridgeTransportFallback().execute("pair", "mac", {
            connected += it; it
        }) { "ok" })
        assertEquals(listOf("mac"), connected)
    }

    @Test fun lostResponseNeverReplaysTheMutation() = runBlocking {
        val connected = mutableListOf<String>()
        var sends = 0
        val result = runCatching { BridgeTransportFallback().execute("pair", "mac", {
            connected += it; it
        }) { sends++; throw IOException("response lost") } }
        assertTrue(result.isFailure)
        assertEquals(1, sends)
        assertEquals(listOf("mac"), connected)
    }

    @Test fun certificateRejectionNeverFailsOver() = runBlocking {
        val connected = mutableListOf<String>()
        val result = runCatching { BridgeTransportFallback().execute("pair", "mac", {
            connected += it; throw SSLHandshakeException("pin mismatch")
        }) { fail("must not send") } }
        assertTrue(result.exceptionOrNull() is SSLHandshakeException)
        assertEquals(listOf("mac"), connected)
    }

    @Test fun cancellationDoesNotTryAnotherHost() = runBlocking {
        val connected = mutableListOf<String>()
        val result = runCatching { BridgeTransportFallback().execute("pair", "mac", {
            connected += it; throw CancellationException("cancelled")
        }) { fail("must not send") } }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(listOf("mac"), connected)
    }

    @Test fun unpluggedUsbReturnsToNetworkWithoutLosingPairing() = runBlocking {
        val transport = BridgeTransportFallback()
        transport.execute("pair", "mac", { if (it == "mac") throw IOException(); it }) { it }
        val connected = mutableListOf<String>()
        val result = transport.execute("pair", "mac", {
            connected += it
            if (it == "127.0.0.1") throw IOException("unplugged")
            it
        }) { it }
        assertEquals("mac", result)
        assertEquals(listOf("127.0.0.1", "mac"), connected)
    }

    @Test fun changedPairingDoesNotInheritRememberedUsbRoute() = runBlocking {
        val transport = BridgeTransportFallback()
        transport.execute("pair", "mac", { if (it == "mac") throw IOException(); it }) { it }
        val connected = mutableListOf<String>()
        transport.execute("new-pair", "other-mac", { connected += it; it }) { it }
        assertEquals(listOf("other-mac"), connected)
    }
}
