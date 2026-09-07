package com.example.meowmba.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.util.Base64

/** Only these safe, authored messages may be shown as provider errors. Never echo server bodies/keys. */
class ImageServiceException(message: String) : IllegalStateException(message)

private fun imageClient(engine: HttpClientEngine) = HttpClient(engine) {
    followRedirects = false // Never forward credentials to a provider-supplied redirect.
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 120_000
        socketTimeoutMillis = 120_000
    }
}

private suspend fun HttpClient.imageJson(
    url: String, service: String, method: HttpMethod = HttpMethod.Get,
    headers: Map<String, String> = emptyMap(), body: JsonObject? = null,
): JsonObject = prepareRequest(url) {
    this.method = method
    headers.forEach { (key, value) -> header(key, value) }
    if (body != null) { contentType(ContentType.Application.Json); setBody(body.toString()) }
}.execute { response ->
    if (!response.status.isSuccess()) throw ImageServiceException(when (response.status.value) {
        401, 403 -> "$service rejected access. Check the account, API token and permissions in Settings → Images."
        429 -> "$service is busy or its free quota is exhausted. Try again later or check your account limits."
        400, 422 -> "$service could not accept this image request. Try a shorter, simpler prompt."
        503 -> "$service has no capacity right now. Try again later."
        else -> "$service returned HTTP ${response.status.value}. Try again later."
    })
    // Streaming limit, before allocation/JSON parsing. Includes base64 image payload.
    val limit = 16 * 1024 * 1024
    if ((response.contentLength() ?: 0) > limit) throw ImageServiceException("$service returned an oversized image.")
    val output = ByteArrayOutputStream()
    val channel = response.bodyAsChannel()
    val chunk = ByteArray(8192)
    while (true) {
        val count = channel.readAvailable(chunk, 0, chunk.size)
        if (count == -1) break
        if (output.size() + count > limit) throw ImageServiceException("$service returned an oversized image.")
        output.write(chunk, 0, count)
    }
    Json.parseToJsonElement(output.toString("UTF-8")).jsonObject
}

private fun decodeImage(encoded: String): Bitmap {
    if (encoded.length > 16 * 1024 * 1024) throw ImageServiceException("The returned image is too large for this phone.")
    val bytes = Base64.getDecoder().decode(encoded)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) {
        throw ImageServiceException("The service returned an invalid or oversized image.")
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw ImageServiceException("The service returned an unreadable image.")
}

private suspend fun <T> safeImageCall(service: String, block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: ImageServiceException) {
    Result.failure(e)
} catch (_: Exception) {
    Result.failure(ImageServiceException("$service could not complete the request. Check your connection and try again."))
} catch (_: OutOfMemoryError) {
    Result.failure(ImageServiceException("Not enough free memory to open the generated image. Close other apps and retry."))
}

class CloudflareImageGenerator(
    private val accountId: String,
    private val token: String,
    engine: HttpClientEngine? = null,
) : ImageGenerator {
    private val client by lazy { imageClient(engine ?: Android.create()) }
    private val headers get() = mapOf("Authorization" to "Bearer $token")
    override fun isAvailable() = accountId.matches(Regex("[a-fA-F0-9]{32}")) && token.isNotBlank()

    /** Checks account access without creating an image or consuming generation quota. */
    suspend fun checkConnection(): Result<String> = try {
        safeImageCall("Cloudflare") {
            if (!isAvailable()) throw ImageServiceException("Enter your 32-character Cloudflare account ID and API token.")
            val data = client.imageJson("$BASE/$accountId/ai/models/search", "Cloudflare", headers = headers)
            if (data["success"]?.jsonPrimitive?.booleanOrNull != true) throw ImageServiceException("Cloudflare did not confirm account access.")
            "Account reachable. Generation also requires Workers AI write permission and available quota."
        }
    } finally { client.close() }

    override suspend fun generate(prompt: String, iterations: Int, seed: Int): Result<Bitmap> = try {
        safeImageCall("Cloudflare") {
            if (!isAvailable()) throw ImageServiceException("Set up Cloudflare in Settings → Images first.")
            if (prompt.length > 2048) throw ImageServiceException("Cloudflare image prompts must be 2,048 characters or fewer.")
            val data = client.imageJson("$BASE/$accountId/ai/run/@cf/black-forest-labs/flux-1-schnell",
                "Cloudflare", HttpMethod.Post, headers, buildJsonObject { put("prompt", prompt); put("steps", 4) })
            if (data["success"]?.jsonPrimitive?.booleanOrNull != true) throw ImageServiceException("Cloudflare did not generate an image. Check your Workers AI permissions and quota.")
            val encoded = data["result"]?.jsonObject?.get("image")?.jsonPrimitive?.contentOrNull
                ?: throw ImageServiceException("Cloudflare returned no image.")
            decodeImage(encoded)
        }
    } finally { client.close() }

    private companion object { const val BASE = "https://api.cloudflare.com/client/v4/accounts" }
}

class HordeImageGenerator(
    private val apiKey: String = "",
    private val anonymousSharingAccepted: Boolean = false,
    private val onProgress: (String) -> Unit = {},
    engine: HttpClientEngine? = null,
    private val pollMillis: Long = 5_000,
) : ImageGenerator {
    private val client by lazy { imageClient(engine ?: Android.create()) }
    private val anonymous get() = apiKey.isBlank() || apiKey == ANONYMOUS_KEY
    private val headers get() = mapOf("apikey" to apiKey.ifBlank { ANONYMOUS_KEY }, "Client-Agent" to "MeowAssistant:1.3.29:dekayrekords@gmail.com")
    override fun isAvailable() = !anonymous || anonymousSharingAccepted

    suspend fun checkConnection(): Result<String> = try {
        safeImageCall("AI Horde") {
            client.imageJson("$BASE/status/heartbeat", "AI Horde")
            "AI Horde is reachable. This does not verify a personal key or guarantee available workers."
        }
    } finally { client.close() }

    override suspend fun generate(prompt: String, iterations: Int, seed: Int): Result<Bitmap> {
        var requestId: String? = null
        var finished = false
        try {
            return safeImageCall("AI Horde") {
                if (!isAvailable()) throw ImageServiceException("Review anonymous image sharing in Settings → Images before using AI Horde.")
                val bitmap = withTimeoutOrNull(540_000) {
                    onProgress("Submitting image to AI Horde…")
                    val submitted = client.imageJson("$BASE/generate/async", "AI Horde", HttpMethod.Post, headers,
                        buildJsonObject {
                            put("prompt", prompt)
                            putJsonObject("params") { put("width", 512); put("height", 512); put("steps", iterations.coerceIn(1, 30)); put("n", 1); put("seed", seed.toString()) }
                            put("nsfw", false); put("censor_nsfw", true); put("trusted_workers", true)
                            put("validated_backends", true); put("slow_workers", true); put("allow_downgrade", true)
                            // Inline base64 avoids fetching untrusted worker-provided URLs.
                            put("r2", false); put("shared", anonymous)
                        })
                    val id = submitted["id"]?.jsonPrimitive?.contentOrNull
                    if (id == null || !id.matches(Regex("[a-fA-F0-9-]{36}"))) throw ImageServiceException("AI Horde did not return a valid queue ticket.")
                    requestId = id
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val state = client.imageJson("$BASE/generate/check/$id", "AI Horde", headers = headers)
                        if (state["faulted"]?.jsonPrimitive?.booleanOrNull == true) throw ImageServiceException("AI Horde could not generate this image. Please retry later.")
                        if (state["done"]?.jsonPrimitive?.booleanOrNull == true) break
                        if (state["is_possible"]?.jsonPrimitive?.booleanOrNull == false) throw ImageServiceException("No compatible AI Horde workers are available. Try again later.")
                        val position = state["queue_position"]?.jsonPrimitive?.intOrNull
                        val seconds = state["wait_time"]?.jsonPrimitive?.intOrNull
                        onProgress("AI Horde: ${if (position != null) "queue position $position" else "waiting for a worker"}${if (seconds != null) " · about ${seconds.coerceAtLeast(0)}s" else ""}. You can cancel this task.")
                        delay(pollMillis)
                    }
                    val result = client.imageJson("$BASE/generate/status/$id", "AI Horde", headers = headers)
                    finished = true
                    val generation = result["generations"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?: throw ImageServiceException("AI Horde completed without returning an image.")
                    if (generation["censored"]?.jsonPrimitive?.booleanOrNull == true) throw ImageServiceException("AI Horde filtered this request. Try a different prompt.")
                    decodeImage(generation["img"]?.jsonPrimitive?.contentOrNull
                        ?: throw ImageServiceException("AI Horde returned no image data."))
                }
                bitmap ?: throw ImageServiceException("AI Horde's queue took too long. The request was cancelled; try again later.")
            }
        } finally {
            if (!finished && requestId != null) withContext(NonCancellable) {
                withTimeoutOrNull(5_000) {
                    try { client.imageJson("$BASE/generate/status/$requestId", "AI Horde", HttpMethod.Delete, headers) }
                    catch (_: Exception) { /* best-effort remote cleanup; never hide local cancellation */ }
                }
            }
            client.close()
        }
    }

    companion object {
        const val ANONYMOUS_KEY = "0000000000"
        private const val BASE = "https://aihorde.net/api/v2"
    }
}
