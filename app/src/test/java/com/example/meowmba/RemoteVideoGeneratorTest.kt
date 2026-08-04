package com.example.meowmba

import com.example.meowmba.core.media.RemoteVideoGenerator
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

class RemoteVideoGeneratorTest {

    @Test
    fun `isAvailable is true whenever a base URL is configured`() {
        val generator = RemoteVideoGenerator("http://192.168.1.10:9300", MockEngine { respond("") })
        assertTrue(generator.isAvailable())
    }

    @Test
    fun `generate reports progress while polling and returns bytes when done`() = runTest {
        var pollCount = 0
        val progressUpdates = mutableListOf<String>()
        val fakeVideoBytes = byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70) // real MP4 ftyp box header magic
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/generate" ->
                    respond(
                        """{"job_id": "vid-123"}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                request.url.encodedPath == "/status/vid-123" -> {
                    pollCount++
                    val status = when {
                        pollCount == 1 -> "queued"
                        pollCount < 3 -> "running"
                        else -> "done"
                    }
                    respond(
                        """{"status": "$status", "error": null}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                request.url.encodedPath == "/result/vid-123" ->
                    respond(
                        content = fakeVideoBytes, status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "video/mp4")
                    )
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val generator = RemoteVideoGenerator("http://192.168.1.10:9300", engine, pollIntervalMs = 1L)

        val result = generator.generate("a cat playing", onProgress = { progressUpdates.add(it) })

        assertTrue(result.isSuccess)
        assertEquals(3, pollCount)
        assertTrue(progressUpdates.isNotEmpty())
    }

    @Test
    fun `a failed job status is reported as a Result failure`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/generate" -> respond(
                    """{"job_id": "vid-456"}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                "/status/vid-456" -> respond(
                    """{"status": "failed", "error": "CUDA out of memory"}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val generator = RemoteVideoGenerator("http://192.168.1.10:9300", engine, pollIntervalMs = 1L)

        val result = generator.generate("a cat playing")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("CUDA out of memory") == true)
    }
}
