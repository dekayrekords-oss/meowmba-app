package com.example.meowmba.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Calls a user-configured HTTP server to generate an image — opt-in, only
 * constructed when the user has entered a server URL in Settings (see
 * `CompanionViewModel.setImageServerUrl`). Never assumes any particular
 * host; the URL is entirely the user's own infrastructure.
 *
 * Wire contract this expects the server to speak:
 *   POST {baseUrl}/generate
 *   body: {"prompt": "..."}  (application/json)
 *   response: raw image bytes, any format BitmapFactory can decode (PNG/JPEG)
 */
class RemoteImageGenerator(
    private val baseUrl: String,
    engine: HttpClientEngine = Android.create()
) : ImageGenerator {

    private val client = HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
    }

    /** No network call — just checks a URL was configured at all. */
    override fun isAvailable(): Boolean = baseUrl.isNotBlank()

    override suspend fun generate(
        prompt: String,
        iterations: Int,
        seed: Int
    ): Result<Bitmap> {
        if (baseUrl.isBlank()) {
            return Result.failure(IllegalStateException("No image server configured"))
        }
        return try {
            val response = client.post("$baseUrl/generate") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(GenerateRequest.serializer(), GenerateRequest(prompt)))
            }
            if (!response.status.isSuccess()) {
                return Result.failure(
                    IllegalStateException("Image server HTTP ${response.status.value}")
                )
            }
            val bytes = response.readBytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return Result.failure(IllegalStateException("Image server returned an undecodable response"))
            Result.success(bitmap)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Image server unreachable: ${e.message ?: "network error"}"))
        }
    }
}

@Serializable
private data class GenerateRequest(val prompt: String)
