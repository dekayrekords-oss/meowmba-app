package com.example.meowmba

import com.example.meowmba.core.media.RemoteMusicGenerator
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteMusicGeneratorTest {

    @Test
    fun `isAvailable is true whenever a base URL is configured`() {
        val generator = RemoteMusicGenerator("http://192.168.1.10:9200", MockEngine { respond("") })
        assertTrue(generator.isAvailable())
    }

    @Test
    fun `generate polls status until complete then downloads the result`() = runTest {
        var statusCallCount = 0
        val fakeAudioBytes = byteArrayOf(0x52, 0x49, 0x46, 0x46) // "RIFF" — real WAV header magic bytes
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/generate" ->
                    respond(
                        """{"task_id": "abc-123"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                request.url.encodedPath == "/status/abc-123" -> {
                    statusCallCount++
                    val status = if (statusCallCount < 2) 0 else 1
                    respond(
                        """{"status": $status}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                request.url.encodedPath == "/result/abc-123" ->
                    respond(
                        content = fakeAudioBytes,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "audio/mpeg")
                    )
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val generator = RemoteMusicGenerator("http://192.168.1.10:9200", engine, pollIntervalMs = 1L)

        val result = generator.generate("upbeat lofi", durationSec = 15)

        assertTrue(result.isSuccess)
        assertEquals(2, statusCallCount)
    }

    @Test
    fun `generate reports an honest failure on a non-2xx response from the generate endpoint`() = runTest {
        val engine = MockEngine { respond("server error", HttpStatusCode.InternalServerError) }
        val generator = RemoteMusicGenerator("http://192.168.1.10:9200", engine)

        val result = generator.generate("upbeat lofi")

        assertTrue(result.isFailure)
    }

    @Test
    fun `a server-reported failure status is an honest Result failure`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/generate" -> respond(
                    """{"task_id": "failed-task"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                "/status/failed-task" -> respond(
                    """{"status": 2}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val generator = RemoteMusicGenerator("http://192.168.1.10:9200", engine, pollIntervalMs = 1L)

        val result = generator.generate("upbeat lofi")

        assertTrue(result.isFailure)
    }

    @Test
    fun `polling exhausts maxPolls and times out honestly instead of hanging`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/generate" -> respond(
                    """{"task_id": "never-done"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                "/status/never-done" -> respond(
                    """{"status": 0}""", // always "still running"
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val generator = RemoteMusicGenerator("http://192.168.1.10:9200", engine, pollIntervalMs = 1L, maxPolls = 3)

        val result = generator.generate("upbeat lofi")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("timed out") == true)
    }

    @Test
    fun `generate rejects a result response larger than the size ceiling`() = runTest {
        val oversized = ByteArray(33 * 1024 * 1024) // 33 MB, over the 32 MB cap
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/generate" -> respond(
                    """{"task_id": "big-task"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                "/status/big-task" -> respond(
                    """{"status": 1}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                "/result/big-task" -> respond(
                    content = oversized,
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("audio/wav"),
                        HttpHeaders.ContentLength to listOf(oversized.size.toString())
                    )
                )
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val generator = RemoteMusicGenerator("http://192.168.1.10:9200", engine, pollIntervalMs = 1L)

        val result = generator.generate("upbeat lofi")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("too large") == true)
    }
}
