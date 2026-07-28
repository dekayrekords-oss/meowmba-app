package com.example.meowmba.core.bridge

import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URL
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Android-side client for the Meowmba desktop bridge (Phase 6 P6a).
 *
 * Trust model:
 *  - The daemon's self-signed certificate is pinned by SHA-256 fingerprint,
 *    learned from the pairing payload (shown on the desktop out-of-band).
 *    Any other certificate is rejected, so hostname verification is moot.
 *  - Pairing establishes a 256-bit shared secret (generated here, sent once
 *    over the pinned channel). Every later request is HMAC-SHA256 signed
 *    with a 60-second replay window enforced by the daemon.
 *  - The secret lives in [SecureStorageManager] (Keystore-encrypted prefs).
 *
 * Honesty contract: every suspend function returns [Result] with a
 * human-readable failure — nothing throws, nothing is faked.
 *
 * Note: JSON is built/parsed as element trees because the project does not
 * apply the kotlinx-serialization compiler plugin (no generated serializers).
 */
class DesktopBridgeManager(
    private val secureStorage: SecureStorageManager,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 }
) {
    data class PairedBridge(val host: String, val port: Int, val name: String, val certSha256: String)

    data class BridgeStatus(
        val name: String,
        val version: String,
        val platform: String,
        val uptimeSeconds: Long,
        val workspaceRoots: List<String>,
        val pairedPhone: String
    )

    data class BridgeFileEntry(val name: String, val dir: Boolean, val size: Long)

    data class BridgeDirListing(val path: String, val entries: List<BridgeFileEntry>, val truncated: Boolean)

    data class BridgeFileContent(val path: String, val size: Long, val content: String, val truncated: Boolean)

    data class BridgeWriteResult(val path: String, val size: Long, val created: Boolean, val backup: String?)

    // ------------------------------------------------------------------
    // Pairing state
    // ------------------------------------------------------------------

    fun pairedBridge(): PairedBridge? {
        val host = secureStorage.getPreference(KEY_HOST)?.takeIf { it.isNotBlank() } ?: return null
        val port = secureStorage.getPreference(KEY_PORT)?.toIntOrNull() ?: return null
        val name = secureStorage.getPreference(KEY_NAME) ?: ""
        val fp = secureStorage.getPreference(KEY_CERT)?.takeIf { it.isNotBlank() } ?: return null
        secureStorage.getPreference(KEY_SECRET)?.takeIf { it.isNotBlank() } ?: return null
        return PairedBridge(host, port, name, fp)
    }

    fun isPaired(): Boolean = pairedBridge() != null

    fun unpair() {
        listOf(KEY_HOST, KEY_PORT, KEY_NAME, KEY_CERT, KEY_SECRET)
            .forEach { secureStorage.savePreference(it, "") }
    }

    // ------------------------------------------------------------------
    // Pairing
    // ------------------------------------------------------------------

    /**
     * Pair with a desktop using the payload printed by `meowmba-bridge.py --pair`.
     * Returns the desktop's name on success.
     */
    suspend fun pair(payloadBlob: String, phoneName: String): Result<String> = withContext(Dispatchers.IO) {
        val payload = DesktopBridgeProtocol.decodePairingPayload(payloadBlob).getOrElse {
            return@withContext Result.failure(it)
        }
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val body = buildJsonObject {
            put("token", payload.pairToken)
            put("secret", DesktopBridgeProtocol.b64EncodeStd(secret))
            put("phone_name", phoneName)
        }.toString().toByteArray(Charsets.UTF_8)

        val result = runCatching {
            post(payload.host, payload.port, "/pair", body, payload.certSha256, emptyMap())
        }.getOrElse { return@withContext Result.failure(Exception("desktop unreachable: ${it.message}")) }

        when (result.first) {
            200 -> {
                val desktopName = runCatching {
                    Json.parseToJsonElement(String(result.second, Charsets.UTF_8))
                        .jsonObject["desktop_name"]?.jsonPrimitive?.content
                }.getOrNull()
                if (desktopName == null) {
                    return@withContext Result.failure(Exception("desktop sent an unreadable pairing reply"))
                }
                secureStorage.savePreference(KEY_HOST, payload.host)
                secureStorage.savePreference(KEY_PORT, payload.port.toString())
                secureStorage.savePreference(KEY_NAME, desktopName.ifBlank { payload.name })
                secureStorage.savePreference(KEY_CERT, payload.certSha256.lowercase())
                secureStorage.savePreference(KEY_SECRET, DesktopBridgeProtocol.b64EncodeStd(secret))
                Result.success(desktopName.ifBlank { payload.name })
            }
            403 -> Result.failure(Exception("pairing rejected — wrong or expired token; run --pair again on the desktop"))
            409 -> Result.failure(Exception("desktop has no open pairing window — start it with --pair"))
            else -> Result.failure(Exception("pairing failed (http ${result.first}): ${String(result.second, Charsets.UTF_8).take(160)}"))
        }
    }

    // ------------------------------------------------------------------
    // Signed RPCs (P6a methods: ping, status)
    // ------------------------------------------------------------------

    suspend fun ping(): Result<Long> = rpc("ping").mapCatching { body ->
        Json.parseToJsonElement(body).jsonObject["ts"]?.jsonPrimitive?.long
            ?: throw Exception("bridge sent an unreadable pong")
    }

    suspend fun status(): Result<BridgeStatus> = rpc("status").mapCatching { body ->
        val obj = Json.parseToJsonElement(body).jsonObject
        BridgeStatus(
            name = obj["name"]?.jsonPrimitive?.content ?: "",
            version = obj["version"]?.jsonPrimitive?.content ?: "",
            platform = obj["platform"]?.jsonPrimitive?.content ?: "",
            uptimeSeconds = obj["uptime_s"]?.jsonPrimitive?.long ?: 0,
            workspaceRoots = obj["workspace_roots"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            pairedPhone = obj["paired_phone"]?.jsonPrimitive?.content ?: ""
        )
    }

    // ------------------------------------------------------------------
    // File tools (P6b): read-only, workspace-confined by the daemon
    // ------------------------------------------------------------------

    /** List a directory inside the desktop's workspace roots ("" = first root). */
    suspend fun listFiles(path: String): Result<BridgeDirListing> =
        rpc("list_files", mapOf("path" to path)).mapCatching { body ->
            val obj = Json.parseToJsonElement(body).jsonObject
            BridgeDirListing(
                path = obj["path"]?.jsonPrimitive?.content ?: path,
                entries = obj["entries"]?.jsonArray?.map { e ->
                    val o = e.jsonObject
                    BridgeFileEntry(
                        name = o["name"]?.jsonPrimitive?.content ?: "",
                        dir = o["dir"]?.jsonPrimitive?.boolean ?: false,
                        size = o["size"]?.jsonPrimitive?.long ?: 0
                    )
                } ?: emptyList(),
                truncated = obj["truncated"]?.jsonPrimitive?.boolean ?: false
            )
        }

    /** Read a utf-8 text file inside the workspace (size-capped by the daemon). */
    suspend fun readFile(path: String, maxBytes: Int = 8192): Result<BridgeFileContent> =
        rpc("read_file", mapOf("path" to path, "max_bytes" to maxBytes)).mapCatching { body ->
            val obj = Json.parseToJsonElement(body).jsonObject
            BridgeFileContent(
                path = obj["path"]?.jsonPrimitive?.content ?: path,
                size = obj["size"]?.jsonPrimitive?.long ?: 0,
                content = obj["content"]?.jsonPrimitive?.content ?: "",
                truncated = obj["truncated"]?.jsonPrimitive?.boolean ?: false
            )
        }

    /**
     * Write utf-8 text inside the workspace (P6c). The daemon writes
     * atomically and backs up any existing file first; the caller is
     * responsible for user approval (see DesktopWriteFileTool's gate).
     */
    suspend fun writeFile(path: String, content: String): Result<BridgeWriteResult> =
        rpc("write_file", mapOf("path" to path, "content" to content)).mapCatching { body ->
            val obj = Json.parseToJsonElement(body).jsonObject
            BridgeWriteResult(
                path = obj["path"]?.jsonPrimitive?.content ?: path,
                size = obj["size"]?.jsonPrimitive?.long ?: 0,
                created = obj["created"]?.jsonPrimitive?.boolean ?: false,
                backup = obj["backup"]?.jsonPrimitive?.content
            )
        }

    /** Signed POST /rpc. Returns raw response body on HTTP 200. */
    private suspend fun rpc(method: String, params: Map<String, Any?> = emptyMap()): Result<String> = withContext(Dispatchers.IO) {
        val bridge = pairedBridge()
            ?: return@withContext Result.failure(Exception("not paired — pair a desktop bridge first"))
        val secret = try {
            DesktopBridgeProtocol.b64Decode(secureStorage.getPreference(KEY_SECRET)!!)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("stored bridge secret is corrupt — unpair and re-pair"))
        }
        val body = buildJsonObject {
            put("method", method)
            if (params.isNotEmpty()) {
                putJsonObject("params") {
                    params.forEach { (k, v) ->
                        when (v) {
                            null -> put(k, JsonNull)
                            is String -> put(k, v)
                            is Int -> put(k, v)
                            is Long -> put(k, v)
                            is Boolean -> put(k, v)
                            else -> put(k, v.toString())
                        }
                    }
                }
            }
        }.toString().toByteArray(Charsets.UTF_8)
        val ts = clock().toString()
        val canonical = DesktopBridgeProtocol.canonicalMessage(
            "POST", "/rpc", ts, DesktopBridgeProtocol.sha256Hex(body)
        )
        val signature = DesktopBridgeProtocol.signHmacSha256Base64(secret, canonical)

        val result = runCatching {
            post(
                bridge.host, bridge.port, "/rpc", body, bridge.certSha256,
                mapOf("X-Meowmba-Timestamp" to ts, "X-Meowmba-Signature" to signature)
            )
        }.getOrElse { return@withContext Result.failure(Exception("desktop unreachable: ${it.message}")) }

        when (result.first) {
            200 -> Result.success(String(result.second, Charsets.UTF_8))
            401 -> Result.failure(Exception("signature rejected — clocks out of sync or secret mismatch"))
            428 -> Result.failure(Exception("desktop forgot the pairing — re-pair"))
            else -> {
                // The daemon's errors are JSON {"error": "human-readable"} — surface them as-is
                val daemonMessage = runCatching {
                    Json.parseToJsonElement(String(result.second, Charsets.UTF_8))
                        .jsonObject["error"]?.jsonPrimitive?.content
                }.getOrNull()
                Result.failure(Exception(daemonMessage ?: "bridge error (http ${result.first})"))
            }
        }
    }

    // ------------------------------------------------------------------
    // Pinned-TLS transport
    // ------------------------------------------------------------------

    /** POST JSON over TLS pinned to [expectedCertSha256]; returns (status, body). */
    private fun post(
        host: String, port: Int, path: String, body: ByteArray,
        expectedCertSha256: String, extraHeaders: Map<String, String>
    ): Pair<Int, ByteArray> {
        val trustManager = object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val fp = DesktopBridgeProtocol.sha256Hex(chain[0].encoded)
                if (!fp.equals(expectedCertSha256, ignoreCase = true)) {
                    throw CertificateException("bridge certificate fingerprint mismatch — refusing to connect")
                }
            }

            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("client auth not used")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)

        val conn = (URL("https://$host:$port$path").openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = sslContext.socketFactory
            // Certificate is pinned by fingerprint; hostname checking adds nothing here.
            hostnameVerifier = HostnameVerifier { _, _ -> true }
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { it.write(body) }
        val code = conn.responseCode
        val bytes = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.use { it.readBytes() } ?: ByteArray(0)
        conn.disconnect()
        return code to bytes
    }

    companion object {
        private const val KEY_HOST = "bridge_host"
        private const val KEY_PORT = "bridge_port"
        private const val KEY_NAME = "bridge_name"
        private const val KEY_CERT = "bridge_cert_sha256"
        private const val KEY_SECRET = "bridge_secret"
    }
}
