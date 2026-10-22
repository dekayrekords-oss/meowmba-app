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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Talks to meow-music's orchestration wrapper (not ACE-Step's own API
 * directly — the wrapper owns the Ollama-swap and re-exposes a stable
 * job/poll contract). Opt-in: only constructed when the user has entered a
 * server URL in Settings.
 *
 * Wire contract: POST {baseUrl}/generate {"prompt","duration_sec"} ->
 * {"task_id"}; GET {baseUrl}/status/{id} -> {"status": 0|1|2}; GET
 * {baseUrl}/result/{id} -> raw audio bytes once status is 1.
 */
class RemoteMusicGenerator(
    private val baseUrl: String,
    engine: HttpClientEngine = Android.create(),
    private val pollIntervalMs: Long = 2_000L,
    private val maxPolls: Int = 60 // 2min at 2s intervals; generation is expected in seconds-to-low-tens-of-seconds per Task 1's measured timing
) : MusicGenerator {

    private val client = HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 32 * 1024 * 1024 // 32MB ceiling, same discipline as RemoteImageGenerator
    }

    override fun isAvailable(): Boolean = baseUrl.isNotBlank()

    override suspend fun generate(prompt: String, durationSec: Int): Result<ByteArray> {
        if (baseUrl.isBlank()) {
            return Result.failure(IllegalStateException("No music server configured"))
        }
        return try {
            val taskId = startGeneration(prompt, durationSec)
                ?: return Result.failure(IllegalStateException("Music server did not return a task id"))
            pollUntilDone(taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Music server unreachable: ${e.message ?: "network error"}"))
        } finally {
            client.close()
        }
    }

    private suspend fun startGeneration(prompt: String, durationSec: Int): String? {
        val response = client.post("$baseUrl/generate") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(MusicGenerateRequest.serializer(), MusicGenerateRequest(prompt, durationSec)))
        }
        if (!response.status.isSuccess()) return null
        val body = Json.decodeFromString(MusicTaskIdResponse.serializer(), String(response.readBytes()))
        return body.task_id
    }

    private suspend fun pollUntilDone(taskId: String): Result<ByteArray> {
        repeat(maxPolls) {
            val response = client.get("$baseUrl/status/$taskId")
            if (response.status.isSuccess()) {
                val status = Json.decodeFromString(MusicStatusResponse.serializer(), String(response.readBytes()))
                when (status.status) {
                    1 -> return fetchResult(taskId)
                    2 -> return Result.failure(IllegalStateException("Music generation failed on the server"))
                    // 0 = still running — keep polling
                }
            }
            delay(pollIntervalMs)
        }
        return Result.failure(IllegalStateException("Music generation timed out waiting for the server"))
    }

    private suspend fun fetchResult(taskId: String): Result<ByteArray> {
        val response = client.get("$baseUrl/result/$taskId")
        if (!response.status.isSuccess()) {
            return Result.failure(IllegalStateException("Music server HTTP ${response.status.value} fetching result"))
        }
        val contentLength = response.contentLength()
        if (contentLength != null && contentLength > MAX_RESPONSE_BYTES) {
            return Result.failure(IllegalStateException("Music server response too large (${contentLength} bytes)"))
        }
        val bytes = response.readBytes()
        if (bytes.size > MAX_RESPONSE_BYTES) {
            return Result.failure(IllegalStateException("Music server response too large (${bytes.size} bytes)"))
        }
        return Result.success(bytes)
    }
}

@Serializable
private data class MusicGenerateRequest(val prompt: String, val duration_sec: Int)

@Serializable
private data class MusicTaskIdResponse(val task_id: String)

@Serializable
private data class MusicStatusResponse(val status: Int)
