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
    private val transportFallback = BridgeTransportFallback()
    @Volatile private var connectedHost: String? = null

    fun connectionDescription(): String =
        if (connectedHost == "127.0.0.1") "USB tunnel" else "network"

    data class PairedBridge(val host: String, val port: Int, val name: String, val certSha256: String)

    data class BridgeStatus(
        val name: String,
        val version: String,
        val platform: String,
        val uptimeSeconds: Long,
        val workspaceRoots: List<String>,
        val pairedPhone: String,
        val logicProReadiness: String = "Logic Pro readiness not reported by this helper.",
    )

    data class BridgeFileEntry(val name: String, val dir: Boolean, val size: Long)

    data class BridgeDirListing(val path: String, val entries: List<BridgeFileEntry>, val truncated: Boolean)

    data class BridgeFileContent(val path: String, val size: Long, val content: String, val truncated: Boolean)

    data class BridgeWriteResult(val path: String, val size: Long, val created: Boolean, val backup: String?)

    data class BridgeBuildResult(
        val name: String,
        val exitCode: Int,
        val durationSeconds: Double,
        val timedOut: Boolean,
        val output: String
    )

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

    /**
     * Persist a completed owner-confirmed pairing.
     */
    fun savePairing(host: String, port: Int, name: String, certSha256: String, secretB64: String) {
        secureStorage.savePreference(KEY_HOST, host)
        secureStorage.savePreference(KEY_PORT, port.toString())
        secureStorage.savePreference(KEY_NAME, name)
        secureStorage.savePreference(KEY_CERT, certSha256.lowercase())
        secureStorage.savePreference(KEY_SECRET, secretB64)
    }

    // ------------------------------------------------------------------
    // Pairing
    // ------------------------------------------------------------------

    /**
     * Pair with a desktop using the payload printed by `meowmba-bridge.py --pair`.
     * Returns the desktop's name on success.
     */
    suspend fun pair(payloadBlob: String, phoneName: String): Result<String> =
        DesktopBridgeProtocol.decodePairingPayload(payloadBlob).fold(
            onSuccess = { pairWith(it.host, it.port, it.name, it.certSha256, it.pairToken, phoneName) },
            onFailure = { Result.failure(it) }
        )

    /**
     * Pair with a discovered desktop using the short 8-char code shown by
     * `--pair`. The fingerprint came over the wire in the daemon's own
     * broadcast; the code is the human proof of physical access.
     */
    suspend fun pairWithCode(
        discovered: DesktopBridgeProtocol.DiscoveredBridge,
        code: String,
        phoneName: String
    ): Result<String> = pairWith(
        discovered.host, discovered.port, discovered.name,
        discovered.certSha256, code, phoneName
    )

    private suspend fun pairWith(
        host: String, port: Int, name: String, certSha256: String,
        token: String, phoneName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val body = buildJsonObject {
            put("token", token)
            put("secret", DesktopBridgeProtocol.b64EncodeStd(secret))
            put("phone_name", phoneName)
        }.toString().toByteArray(Charsets.UTF_8)

        val result = runCatching {
            post(host, port, "/pair", body, certSha256, emptyMap())
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
                secureStorage.savePreference(KEY_HOST, host)
                secureStorage.savePreference(KEY_PORT, port.toString())
                secureStorage.savePreference(KEY_NAME, desktopName.ifBlank { name })
                secureStorage.savePreference(KEY_CERT, certSha256.lowercase())
                secureStorage.savePreference(KEY_SECRET, DesktopBridgeProtocol.b64EncodeStd(secret))
                Result.success(desktopName.ifBlank { name })
            }
            403 -> Result.failure(Exception("pairing rejected — wrong or expired code; check the desktop"))
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
            pairedPhone = obj["paired_phone"]?.jsonPrimitive?.content ?: "",
            logicProReadiness = LogicProPreflight.describe(obj["logic_pro"]),
        )
    }

    data class BridgeScreenshot(val path: String, val bytes: Long, val pngBase64: String)

    suspend fun readUi(): Result<kotlinx.serialization.json.JsonObject> = rpc("ui_snapshot").mapCatching { Json.parseToJsonElement(it).jsonObject }
    suspend fun logicCall(method: String, params: Map<String, Any?>): Result<String> {
        require(method in setOf("logic_inspect", "logic_edit", "logic_receipt", "logic_verify_audio", "logic_screen", "logic_act", "logic_cancel"))
        return rpc(method, params, if (method == "logic_verify_audio") 140_000 else 40_000)
    }
    suspend fun uiAction(snapshotId: String, elementId: String, action: String, value: String): Result<String> =
        rpc("ui_action", mapOf("snapshot_id" to snapshotId, "element_id" to elementId, "action" to action, "value" to value), 30_000).map { messageOf(it, "Action dispatched; read a fresh screen to verify.") }
    suspend fun createBlenderScene(scene: String): Result<String> = runCatching {
        val data = Json.parseToJsonElement(scene).jsonObject
        rpc("blender_scene", mapOf("scene" to data), 200_000).getOrThrow()
    }

    // ------------------------------------------------------------------
    // Control tools (Phase F1): desktop actions behind the paired bridge.
    // Every call degrades to an honest failure when a helper (xdotool,
    // xclip, playerctl, …) is missing on the desktop.
    // ------------------------------------------------------------------

    private fun messageOf(body: String, fallback: String): String = try {
        val obj = Json.parseToJsonElement(body).jsonObject
        obj["message"]?.jsonPrimitive?.content ?: fallback
    } catch (e: Exception) { fallback }

    suspend fun openApp(name: String): Result<String> =
        rpc("open_app", mapOf("name" to name)).map { messageOf(it, "Opened $name.") }

    suspend fun openUrl(url: String): Result<String> =
        rpc("open_url", mapOf("url" to url)).map { messageOf(it, "Opened $url.") }

    suspend fun openFile(path: String): Result<String> =
        rpc("open_file", mapOf("path" to path)).map { messageOf(it, "Opened $path.") }

    suspend fun clipboardGet(): Result<String> = rpc("clipboard_get").mapCatching { body ->
        Json.parseToJsonElement(body).jsonObject["text"]?.jsonPrimitive?.content
            ?: throw Exception("desktop clipboard was empty or unreadable")
    }

    suspend fun clipboardSet(text: String): Result<String> =
        rpc("clipboard_set", mapOf("text" to text)).map { messageOf(it, "Clipboard updated.") }

    /** action: play | pause | next | volume (with [volume] 0-100). */
    suspend fun mediaControl(action: String, volume: Int? = null): Result<String> {
        val params = mutableMapOf<String, Any?>("action" to action)
        volume?.let { params["volume"] = it.coerceIn(0, 100) }
        return rpc("media", params).map { messageOf(it, "Media: $action.") }
    }

    /** PNG screenshot of the desktop; daemon returns path + base64 data. */
    suspend fun screenshot(): Result<BridgeScreenshot> = rpc("screenshot").mapCatching { body ->
        val obj = Json.parseToJsonElement(body).jsonObject
        val b64 = obj["data_b64"]?.jsonPrimitive?.content
            ?: throw Exception("desktop returned no screenshot data")
        BridgeScreenshot(
            path = obj["path"]?.jsonPrimitive?.content ?: "",
            bytes = obj["bytes"]?.jsonPrimitive?.long ?: 0L,
            pngBase64 = b64
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

    /**
     * Run a named, pre-declared build command on the desktop (P6d). The
     * daemon only ever runs fixed argv from its config.json — no shell, no
     * arbitrary commands. Long-running: the daemon enforces its own timeout.
     */
    suspend fun runBuild(name: String): Result<BridgeBuildResult> =
        rpc("run_build", mapOf("name" to name)).mapCatching { body ->
            val obj = Json.parseToJsonElement(body).jsonObject
            BridgeBuildResult(
                name = obj["name"]?.jsonPrimitive?.content ?: name,
                exitCode = obj["exit_code"]?.jsonPrimitive?.long?.toInt() ?: -1,
                durationSeconds = obj["duration_s"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                timedOut = obj["timed_out"]?.jsonPrimitive?.boolean ?: false,
                output = obj["output"]?.jsonPrimitive?.content ?: ""
            )
        }

    /**
     * Run an arbitrary script (e.g. a .py file) on the paired desktop.
     * The daemon executes the script with /usr/bin/env and streams output
     * back. Requires the bridge daemon to support the `run_script` RPC.
     */
    suspend fun runScript(path: String, args: String = "", timeoutSec: Int = 60): Result<BridgeBuildResult> =
        rpc("run_script", mapOf("path" to path, "args" to args, "timeout_sec" to timeoutSec)).mapCatching { body ->
            val obj = Json.parseToJsonElement(body).jsonObject
            BridgeBuildResult(
                name = obj["name"]?.jsonPrimitive?.content ?: path,
                exitCode = obj["exit_code"]?.jsonPrimitive?.long?.toInt() ?: -1,
                durationSeconds = obj["duration_s"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                timedOut = obj["timed_out"]?.jsonPrimitive?.boolean ?: false,
                output = obj["output"]?.jsonPrimitive?.content ?: ""
            )
        }

    /** Signed POST /rpc. Returns raw response body on HTTP 200. */
    private suspend fun rpc(method: String, params: Map<String, Any?> = emptyMap(), readTimeoutMs: Int = 15_000): Result<String> = withContext(Dispatchers.IO) {
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
                            is kotlinx.serialization.json.JsonElement -> put(k, v)
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
                mapOf("X-Meowmba-Timestamp" to ts, "X-Meowmba-Signature" to signature), readTimeoutMs
            )
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            return@withContext Result.failure(Exception("desktop unreachable: ${it.message}"))
        }

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
    private suspend fun post(
        host: String, port: Int, path: String, body: ByteArray,
        expectedCertSha256: String, extraHeaders: Map<String, String>, readTimeoutMs: Int = 15_000
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

        suspend fun connect(endpoint: String): HttpsURLConnection {
        val conn = (URL("https://$endpoint:$port$path").openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = sslContext.socketFactory
            // Certificate is pinned by fingerprint; hostname checking adds nothing here.
            hostnameVerifier = HostnameVerifier { _, _ -> true }
            connectTimeout = if (endpoint == "127.0.0.1") 1_500 else 3_000
            readTimeout = readTimeoutMs
            requestMethod = "POST"
            doOutput = true
            // Streaming disables HttpURLConnection's automatic POST replay.
            setFixedLengthStreamingMode(body.size)
            setRequestProperty("Content-Type", "application/json")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            cancellableBridgeIo(conn::disconnect) { conn.connect() }
            connectedHost = endpoint
            return conn
        } catch (e: Exception) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { conn.disconnect() }
            throw e
        }
        }
        suspend fun send(conn: HttpsURLConnection): Pair<Int, ByteArray> = cancellableBridgeIo(conn::disconnect) {
        try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val bytes = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.use { it.readBytes() } ?: ByteArray(0)
            code to bytes
        } catch (e: java.io.IOException) {
            throw java.io.IOException("Desktop request may have started; inspect its state before retrying. ${e.message}", e)
        } finally {
            // Disconnect on EVERY path: unreachable desktops throw from
            // write/responseCode, and the retry loops hit that constantly —
            // leaking a socket per attempt on exactly the failures that
            // repeat.
            conn.disconnect()
        }
        }
        // Pairing still goes only to the explicitly selected desktop. Fallback
        // is available solely after that desktop's certificate has been pinned.
        return if (path == "/rpc") transportFallback.execute(
            "$host:$port:$expectedCertSha256", host, ::connect, ::send
        ) else send(connect(host))
    }

    companion object {
        private const val KEY_HOST = "bridge_host"
        private const val KEY_PORT = "bridge_port"
        private const val KEY_NAME = "bridge_name"
        private const val KEY_CERT = "bridge_cert_sha256"
        private const val KEY_SECRET = "bridge_secret"
    }
}
