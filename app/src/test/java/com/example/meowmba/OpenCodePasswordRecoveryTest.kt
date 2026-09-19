package com.example.meowmba

import com.example.meowmba.core.computer.OpenCodeLocalConnection
import com.example.meowmba.core.security.SecureStorageManager
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class OpenCodePasswordRecoveryTest {
    @Test fun wrongPasswordIsNotTreatedAsAHealthyConnection() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val expected = "Basic " + Base64.getEncoder().encodeToString("opencode:correct-private-password".toByteArray())
        val paths = mutableListOf<String>()
        server.createContext("/") { exchange ->
            synchronized(paths) { paths.add(exchange.requestMethod + " " + exchange.requestURI.path) }
            val authorized = exchange.requestHeaders.getFirst("Authorization") == expected
            val bytes = """{"healthy":true,"version":"test"}""".toByteArray()
            exchange.sendResponseHeaders(if (authorized) 200 else 401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val address = "http://127.0.0.1:${server.address.port}"
            val failure = runCatching { OpenCodeLocalConnection.verify("new-but-wrong-password", address) }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertTrue(failure!!.message!!.contains("rejected"))
            assertEquals("test", OpenCodeLocalConnection.verify("correct-private-password", address))
            assertTrue(paths.all { it == "GET /global/health" })
        } finally { server.stop(0) }
    }

    @Test fun savedEndpointIsRestrictedToKnownLocalServers() {
        val storage = mock(SecureStorageManager::class.java)
        try {
            `when`(storage.getEndpoint(OpenCodeLocalConnection.CREDENTIAL)).thenReturn(OpenCodeLocalConnection.RECOVERY_ADDRESS)
            OpenCodeLocalConnection.restore(storage)
            assertEquals(OpenCodeLocalConnection.RECOVERY_ADDRESS, OpenCodeLocalConnection.ADDRESS)
            `when`(storage.getEndpoint(OpenCodeLocalConnection.CREDENTIAL)).thenReturn("https://external.example")
            OpenCodeLocalConnection.restore(storage)
            assertEquals(OpenCodeLocalConnection.ORIGINAL_ADDRESS, OpenCodeLocalConnection.ADDRESS)
        } finally { OpenCodeLocalConnection.restore(null) }
    }
}
