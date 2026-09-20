package com.example.meowmba.core.audio

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

/** Pin the cancellable OkHttp engine. Auto-selection can choose Android's
 * HttpURLConnection engine, whose concurrent gzip/chunked stream close crashed
 * during speech cancellation on the Fold (Unbalanced enter/exit, preview147).
 * Do not swallow cancellation or let an old transcription finish a new turn. */
internal fun speechHttpClient(
    connectTimeoutMs: Long,
    requestTimeoutMs: Long,
    followRedirects: Boolean = false,
): HttpClient = HttpClient(OkHttp) {
    this.followRedirects = followRedirects
    install(HttpTimeout) {
        connectTimeoutMillis = connectTimeoutMs
        requestTimeoutMillis = requestTimeoutMs
        socketTimeoutMillis = requestTimeoutMs
    }
}
