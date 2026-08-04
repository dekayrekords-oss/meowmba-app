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
import io.ktor.http.contentLength
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

    /**
     * Note: [iterations] and [seed] are part of the [ImageGenerator] interface for
     * conformance with the on-device backend, but are not forwarded to the server —
     * the remote server controls its own step count and determinism; this backend
     * has no wire-contract field to carry them.
     */
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
            val declaredLength = response.contentLength()
            if (declaredLength != null && declaredLength > MAX_RESPONSE_BYTES) {
                return Result.failure(
                    IllegalStateException(
                        "Image server response too large (${declaredLength} bytes, " +
                            "max $MAX_RESPONSE_BYTES)"
                    )
                )
            }
            val bytes = response.readBytes()
            if (bytes.size > MAX_RESPONSE_BYTES) {
                return Result.failure(
                    IllegalStateException(
                        "Image server response too large (${bytes.size} bytes, " +
                            "max $MAX_RESPONSE_BYTES)"
                    )
                )
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return Result.failure(IllegalStateException("Image server returned an undecodable response"))
            Result.success(bitmap)
        } catch (e: Throwable) {
            // BitmapFactory.decodeByteArray can throw OutOfMemoryError (an Error, not Exception)
            // on large or malformed payloads from network/untrusted sources. Catch Throwable
            // to prevent crashes on misbehaving or malicious servers.
            Result.failure(IllegalStateException("Image server unreachable: ${e.message ?: "network error"}"))
        } finally {
            // Constructed fresh per call (see class KDoc / CompanionViewModel's
            // remoteGenerator supplier) — nothing else holds a reference to this
            // client, so it must close here or its dispatcher/connection pool leaks
            // on every image generation. Safe to close even when `engine` is an
            // injected test MockEngine.
            client.close()
        }
    }

    private companion object {
        /** Guards against a misbehaving/compromised/typo'd server returning an
         *  enormous body or decompression-bomb image; the app also holds an LLM
         *  in memory, so unbounded reads here are a real OOM risk. */
        const val MAX_RESPONSE_BYTES = 32 * 1024 * 1024L
    }
}

@Serializable
private data class GenerateRequest(val prompt: String)
