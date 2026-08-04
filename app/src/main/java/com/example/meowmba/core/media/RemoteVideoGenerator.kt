package com.example.meowmba.core.media

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Talks to meow-video. Generation genuinely takes minutes (verified
 * against real hardware in this plan's Task 1) — onProgress lets the
 * caller surface something to the user instead of nine minutes of
 * silence.
 *
 * Wire contract: POST {baseUrl}/generate {"prompt"} -> {"job_id"};
 * GET {baseUrl}/status/{id} -> {"status": "queued"|"running"|"done"|"failed", "error"};
 * GET {baseUrl}/result/{id} -> raw MP4 bytes once status is "done".
 */
class RemoteVideoGenerator(
    private val baseUrl: String,
    engine: HttpClientEngine = Android.create(),
    private val pollIntervalMs: Long = 10_000L,
    private val maxPolls: Int = 90 // 15min at 10s intervals — generous given Task 1's ~9min measured baseline
) : VideoGenerator {

    private val client = HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1024 * 1024 // video is bigger than audio; 64MB ceiling
    }

    override fun isAvailable(): Boolean = baseUrl.isNotBlank()

    override suspend fun generate(prompt: String, onProgress: (String) -> Unit): Result<ByteArray> {
        if (baseUrl.isBlank()) {
            return Result.failure(IllegalStateException("No video server configured"))
        }
        return try {
            onProgress("Starting video generation...")
            val jobId = startGeneration(prompt)
                ?: return Result.failure(IllegalStateException("Video server did not return a job id"))
            pollUntilDone(jobId, onProgress)
        } catch (e: Throwable) {
            Result.failure(IllegalStateException("Video server unreachable: ${e.message ?: "network error"}"))
        } finally {
            client.close()
        }
    }

    private suspend fun startGeneration(prompt: String): String? {
        val response = client.post("$baseUrl/generate") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(VideoGenerateRequest.serializer(), VideoGenerateRequest(prompt)))
        }
        if (!response.status.isSuccess()) return null
        val body = Json.decodeFromString(VideoJobIdResponse.serializer(), String(response.readBytes()))
        return body.job_id
    }

    private suspend fun pollUntilDone(jobId: String, onProgress: (String) -> Unit): Result<ByteArray> {
        repeat(maxPolls) { attempt ->
            val response = client.get("$baseUrl/status/$jobId")
            if (response.status.isSuccess()) {
                val status = Json.decodeFromString(VideoStatusResponse.serializer(), String(response.readBytes()))
                when (status.status) {
                    "done" -> return fetchResult(jobId)
                    "failed" -> return Result.failure(
                        IllegalStateException(status.error ?: "Video generation failed on the server")
                    )
                    "running" -> onProgress("Still generating your video (this can take several minutes)...")
                    "queued" -> onProgress("Video generation queued...")
                }
            }
            delay(pollIntervalMs)
        }
        return Result.failure(IllegalStateException("Video generation timed out waiting for the server"))
    }

    private suspend fun fetchResult(jobId: String): Result<ByteArray> {
        val response = client.get("$baseUrl/result/$jobId")
        if (!response.status.isSuccess()) {
            return Result.failure(IllegalStateException("Video server HTTP ${response.status.value} fetching result"))
        }
        val contentLength = response.contentLength()
        if (contentLength != null && contentLength > MAX_RESPONSE_BYTES) {
            return Result.failure(IllegalStateException("Video server response too large (${contentLength} bytes)"))
        }
        val bytes = response.readBytes()
        if (bytes.size > MAX_RESPONSE_BYTES) {
            return Result.failure(IllegalStateException("Video server response too large (${bytes.size} bytes)"))
        }
        return Result.success(bytes)
    }
}

@Serializable
private data class VideoGenerateRequest(val prompt: String)

@Serializable
private data class VideoJobIdResponse(val job_id: String)

@Serializable
private data class VideoStatusResponse(val status: String, val error: String? = null)
