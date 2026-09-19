package com.example.meowmba.core.computer

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Separate from SSH and OpenCode Zen: this is the worker inside phone Ubuntu.
 * Fixed loopback destination and disabled redirects prevent credential forwarding.
 */
object OpenCodeLocalConnection {
    const val CREDENTIAL = "opencode_phone_worker"
    const val ADDRESS = "http://127.0.0.1:4096"

    suspend fun verify(password: String, address: String = ADDRESS): String = withContext(Dispatchers.IO) {
        requireLoopback(address)
        require(password.isNotBlank()) { "Enter the server password you chose in Ubuntu." }
        val unauthenticated = request(null, address)
        check(unauthenticated.first == 401) {
            if (unauthenticated.first in 200..299) "This server is not password protected. Restart it with OPENCODE_SERVER_PASSWORD."
            else "OpenCode returned HTTP ${unauthenticated.first}. Check its server setup."
        }
        val (status, body) = request(password, address)
        check(status != 401) { "The server rejected that password. Check it and try again." }
        check(status in 200..299) { "OpenCode returned HTTP $status." }
        val health = Json.parseToJsonElement(body).jsonObject
        check(health["healthy"]?.jsonPrimitive?.booleanOrNull == true) { "OpenCode has not reported a healthy server." }
        health["version"]?.jsonPrimitive?.contentOrNull?.take(80) ?: "unknown version"
    }

    internal fun requireLoopback(address: String) {
        val url = URL(address)
        require(url.protocol == "http" && url.host == "127.0.0.1" && url.port in 1..65535 &&
            url.userInfo == null && url.query == null && url.ref == null && url.path.isEmpty()) { "Computer must use a local loopback server." }
    }

    private fun request(password: String?, address: String): Pair<Int, String> {
        val connection = URL("$address/global/health").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            if (password != null) connection.setRequestProperty("Authorization", "Basic " +
                Base64.getEncoder().encodeToString("opencode:$password".toByteArray(Charsets.UTF_8)))
            val status = connection.responseCode
            val body = if (status in 200..299) connection.inputStream.bufferedReader().use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(512)
                while (out.length <= 4096) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    out.append(buffer, 0, count)
                }
                check(out.length <= 4096) { "Invalid OpenCode health response." }
                out.toString()
            } else ""
            return status to body
        } finally { connection.disconnect() }
    }
}
