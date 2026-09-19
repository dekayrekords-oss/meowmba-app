package com.example.meowmba.core.computer

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Manual terminal only. Never registered as an agent tool. Credentials never enter a URL/WebView. */
internal class OpenCodeTerminal(private val password: String) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private var ownedId: String? = null
    private var socket: WebSocket? = null
    private val address = OpenCodeLocalConnection.ADDRESS
    private fun request(path: String) = Request.Builder().url(address + path)
        .header("Authorization", Credentials.basic("opencode", password))

    private fun call(method: String, path: String, body: JSONObject? = null): JSONObject {
        check(password.isNotBlank()) { "Enter and verify your Ubuntu server password in Computer setup first." }
        val payload = body?.toString()?.toRequestBody("application/json".toMediaType())
        client.newCall(request(path).method(method, payload).build()).execute().use { response ->
            check(response.isSuccessful) { "Ubuntu terminal HTTP ${response.code}. Check Computer setup and update OpenCode if needed." }
            val bytes = response.body?.byteStream()?.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    check(out.size() + count <= 64_000) { "Terminal response is too large." }
                    out.write(buffer, 0, count)
                }
                out.toString("UTF-8")
            }.orEmpty()
            return if (bytes.startsWith('{')) JSONObject(bytes) else JSONObject()
        }
    }

    suspend fun connect(onOutput: (String) -> Unit, onClosed: () -> Unit) = withContext(Dispatchers.IO) {
        OpenCodeLocalConnection.verify(password, address)
        val paths = call("GET", "/path")
        val home = paths.optString("home")
        check(home.startsWith('/') && !home.startsWith("/data/")) {
            "OpenCode is not running inside the Ubuntu guest. Run Setup computer first."
        }
        val info = call("POST", "/pty", JSONObject().put("command", "/bin/bash")
            .put("args", org.json.JSONArray().put("-l")).put("title", "Meow manual Ubuntu terminal"))
        val id = OpenCodeComputer.id(info.getString("id"))
        ownedId = id
        try {
            currentCoroutineContext().ensureActive()
            withTimeout(10_000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    socket = client.newWebSocket(request("/pty/$id/connect").build(), object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        override fun onMessage(webSocket: WebSocket, text: String) { onOutput(text.take(64_000)) }
                        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                            // OpenCode uses binary frames for cursor metadata, not terminal text.
                        }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Ubuntu terminal connection failed. Check Computer setup."))
                            onClosed()
                        }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { onClosed() }
                    })
                    continuation.invokeOnCancellation { socket?.cancel() }
                }
            }
        } catch (error: Throwable) { close(); throw error }
    }

    fun send(bytes: ByteArray) { check(socket?.send(bytes.toString(Charsets.UTF_8)) == true) { "Ubuntu terminal disconnected." } }
    fun resize(cols: Int, rows: Int) {
        val id = ownedId ?: return
        call("PUT", "/pty/$id", JSONObject().put("size", JSONObject().put("cols", cols.coerceIn(20, 500)).put("rows", rows.coerceIn(8, 200))))
    }
    fun close() {
        socket?.cancel(); socket = null
        val id = ownedId; ownedId = null
        if (id != null) runCatching { call("DELETE", "/pty/$id") }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
