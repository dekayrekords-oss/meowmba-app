package com.example.meowmba.core.audio

import io.ktor.client.engine.okhttp.OkHttpEngine
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread

/** Synthetic loopback traffic only: exercises the phone's real HTTP cancellation stack. */
class SpeechCancellation149DeviceTest {
    @Test fun cancelPartialGzipResponseAndReuseSpeechClient() = runBlocking {
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, error -> failures.add(error) })
        val client = speechHttpClient(2_000, 5_000)
        try {
            assertTrue(client.engine is OkHttpEngine)
            repeat(12) {
                ServerSocket(0).use { server ->
                    val sent = CountDownLatch(1)
                    val release = CountDownLatch(1)
                    val worker = thread(isDaemon = true) {
                        try {
                            server.accept().use { socket ->
                                readHeaders(socket)
                                val compressed = ByteArrayOutputStream()
                                val gzip = GZIPOutputStream(compressed, true)
                                gzip.write("partial transcription".toByteArray())
                                gzip.flush() // No gzip footer / last HTTP chunk: reader remains active.
                                val bytes = compressed.toByteArray()
                                val output = socket.getOutputStream()
                                output.write(("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\n" +
                                    "Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n" +
                                    "${bytes.size.toString(16)}\r\n").toByteArray())
                                output.write(bytes)
                                output.write("\r\n".toByteArray())
                                output.flush()
                                sent.countDown()
                                check(release.await(5, TimeUnit.SECONDS))
                            }
                            server.accept().use { socket ->
                                readHeaders(socket)
                                socket.getOutputStream().write(
                                    "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                            }
                        } catch (error: Throwable) { failures.add(error) }
                    }
                    try {
                        val request = scope.launch {
                            client.get("http://127.0.0.1:${server.localPort}/stalled").bodyAsText()
                            error("Incomplete response must not complete")
                        }
                        assertTrue(sent.await(5, TimeUnit.SECONDS))
                        delay(50)
                        assertTrue(request.isActive)
                        withContext(Dispatchers.Main) { request.cancel() }
                        withTimeout(2_000) { request.join() }
                        assertTrue(request.isCancelled)
                        release.countDown()
                        assertEquals("ok", client.get("http://127.0.0.1:${server.localPort}/next").bodyAsText())
                    } finally {
                        release.countDown()
                        server.close()
                        worker.join(2_000)
                    }
                }
            }
            assertEquals(emptyList<Throwable>(), failures.toList())
        } finally { scope.cancel(); client.close() }
    }

    private fun readHeaders(socket: Socket) {
        socket.soTimeout = 5_000
        val input = socket.getInputStream().bufferedReader()
        while (!input.readLine().isNullOrEmpty()) { }
    }
}
