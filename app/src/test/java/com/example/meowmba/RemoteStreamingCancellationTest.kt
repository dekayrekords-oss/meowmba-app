package com.example.meowmba

import com.example.meowmba.core.agent.RemoteModelProvider
import com.example.meowmba.core.agent.ModelResponse
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

/** Real chunked HTTP, not MockEngine: cancellation must close the transport and
 * leave the provider usable. Repeats the path that crashed preview122. */
class RemoteStreamingCancellationTest {
    @Test fun terminalEventsReleaseOpenBodiesAndKeepOllamaFinalText() = runBlocking {
        val workers = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val release = java.util.concurrent.CountDownLatch(1)
        server.executor = workers
        server.createContext("/") { exchange ->
            try {
                val request = exchange.requestBody.use { String(it.readBytes()) }
                val ollama = exchange.requestURI.path == "/api/generate"
                exchange.responseHeaders.add("Content-Type", if (ollama) "application/x-ndjson" else "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { out ->
                    val response = when {
                        ollama -> "{\"response\":\"hello \",\"done\":false}\n{\"response\":\"world\",\"done\":true}\n"
                        request.contains("error fixture") -> "data:{\"error\":{\"message\":\"quota fixture\"}}\n\n"
                        else -> "data:{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\ndata:[DONE]\n\n"
                    }
                    out.write(response.toByteArray()); out.flush()
                    release.await(10, java.util.concurrent.TimeUnit.SECONDS)
                }
            } finally { exchange.close() }
        }
        server.start()
        val base = "http://127.0.0.1:${server.address.port}"
        val api = RemoteModelProvider("fixture", base, "fixture", "fixture")
        val ollama = RemoteModelProvider("Ollama fixture", base, null, "fixture")
        try {
            assertEquals(ModelResponse.Complete("hello"), withTimeout(2_000) { api.generate("finish fixture") })
            assertEquals(ModelResponse.Error("quota fixture"), withTimeout(2_000) { api.generate("error fixture") })
            assertEquals(ModelResponse.Complete("hello world"), withTimeout(2_000) { ollama.generate("finish fixture") })
        } finally { release.countDown(); api.close(); ollama.close(); server.stop(0); workers.shutdownNow() }
    }

    @Test fun cancelAndTimeoutStreamingRequestsLeaveNextRequestUsable() = runBlocking {
        val workers = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val active = AtomicInteger()
        server.executor = workers
        server.createContext("/v1/chat/completions") { exchange ->
            active.incrementAndGet()
            try {
                exchange.requestBody.use { it.readBytes() }
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { output ->
                    repeat(100) {
                        output.write("data: {\"choices\":[{\"delta\":{\"content\":\"hello \"}}]}\n\n".toByteArray())
                        output.flush()
                        Thread.sleep(10)
                    }
                    output.write("data: [DONE]\n\n".toByteArray())
                }
            } catch (_: java.io.IOException) {
                // Closing a cancelled response is expected.
            } finally { active.decrementAndGet(); exchange.close() }
        }
        server.start()
        val provider = RemoteModelProvider("HTTP fixture", "http://127.0.0.1:${server.address.port}", "fixture", "fixture")
        try {
            repeat(5) {
                val received = CompletableDeferred<Unit>()
                val job = launch {
                    provider.generateStreaming("cancel").collect {
                        if (it is ModelResponse.Partial) received.complete(Unit)
                    }
                }
                withTimeout(5_000) { received.await() }
                withTimeout(2_000) { job.cancelAndJoin() }
            }
            assertNull(withTimeoutOrNull(100) { provider.generateStreaming("timeout").collect(); true })
            var completed = false
            withTimeout(5_000) {
                provider.generateStreaming("recover").collect { if (it is ModelResponse.Complete) completed = true }
            }
            assertTrue("Next request must finish after cancellations", completed)
            withTimeout(3_000) { while (active.get() != 0) delay(10) }
        } finally { provider.close(); server.stop(0); workers.shutdownNow() }
    }
}
