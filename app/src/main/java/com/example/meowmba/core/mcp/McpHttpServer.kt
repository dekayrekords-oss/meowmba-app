package com.example.meowmba.core.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Serves an [McpProtocol.Server] over the LAN on a plain [ServerSocket] with a
 * minimal HTTP/1.1 POST transport — the shape an external MCP client such as
 * Android Studio's (which connects to a phone via `httpUrl`) needs for
 * `initialize`, `tools/list` and `tools/call`.
 *
 * ## Why hand-rolled HTTP and not a server framework
 *
 * The app only ships the ktor *client*. Adding a server stack for one read-only
 * endpoint would grow the binary and the attack surface. A POST/JSON-in,
 * JSON-out loop is ~80 lines and dependency-free on Android.
 *
 * ## Transport notes
 *
 * MCP "streamable HTTP" is negotiated with the `Accept` header; a synchronous
 * JSON response to a `tools/list`/`tools/call` POST is the compatible
 * baseline, so that is what this speaks. SSE/notification mode is a
 * refinement this endpoint deliberately does not attempt — the tools are
 * read-only and short-lived, so a request/response model costs nothing here.
 *
 * ## Security
 *
 * The transported tool set stays permissive is a phrase this endpoint does
 * NOT subscribe to: it binds on all interfaces because Android Studio connects
 * from another host, but the guarded tool set
 * ([com.example.meowmba.core.mcp.PhoneMcpServer]) is read-only and carries no
 * shell or destructive capability.
 */
class McpHttpServer(
    private val mcpServer: McpProtocol.Server,
    private val scope: CoroutineScope,
    private val desiredPort: Int = 0,
) {
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private val workers: ExecutorService =
        Executors.newCachedThreadPool { r -> Thread(r, "mcp-http").apply { isDaemon = true } }

    /** Actual listening port once started; -1 before [start]. */
    val port: Int get() = serverSocket?.localPort ?: -1

    fun start(): Boolean {
        if (running) return true
        return runCatching {
            val ss = ServerSocket(desiredPort).also {
                running = true
                it.soTimeout = 1_000
            }
            serverSocket = ss
            scope.launch(workers.asCoroutineDispatcher()) { acceptLoop() }
            true
        }.getOrElse { false }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        workers.shutdown()
    }

    private fun acceptLoop() {
        val ss = serverSocket ?: return
        while (running) {
            val client = try {
                ss.accept()
            } catch (e: Exception) {
                if (!running) break else continue
            }
            workers.execute { serve(client) }
        }
    }

    private fun serve(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 30_000
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: ""

                // CORS pre-flight from the MCP Inspector running in a browser.
                if (method.equals("OPTIONS", ignoreCase = true)) {
                    write(s, 204, "", "application/json")
                    return
                }

                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break
                    val idx = line.indexOf(':')
                    if (idx > 0 && line.substring(0, idx).trim()
                            .equals("Content-Length", ignoreCase = true)
                    ) {
                        contentLength = line.substring(idx + 1).trim().toIntOrNull() ?: 0
                    }
                }

                val body = if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = reader.read(buf, read, contentLength - read)
                        if (n < 0) break
                        read += n
                    }
                    String(buf, 0, read)
                } else ""

                val response = if (method.equals("POST", ignoreCase = true) && body.isNotBlank()) {
                    mcpResponse(body)
                } else {
                    JSONObject().put("error", JSONObject().put("code", -32600)
                        .put("message", "expected a POST with a JSON-RPC body")).toString()
                }
                write(s, 200, response, "application/json")
            } catch (_: Exception) {
                runCatching { write(s, 500, "internal error", "text/plain") }
            }
        }
    }

    /** Blocking bridge into the suspend [McpProtocol.handle] (we are on an IO thread). */
    private fun mcpResponse(body: String): String =
        runBlocking { McpProtocol.handle(mcpServer, body) }

    private fun write(socket: Socket, status: Int, body: String, contentType: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $status ")
            append(if (status == 200) "OK" else if (status == 204) "No Content" else "Error")
            append("\r\n")
            append("Content-Type: $contentType; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            // Answer cross-origin MCP Inspectors (browser) without auth shouting.
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, Accept, Authorization, Mcp-Session-Id\r\n")
            append("Connection: close\r\n\r\n")
        }
        socket.getOutputStream().use { it.write(headers.toByteArray(Charsets.UTF_8)); it.write(bytes) }
    }
}
