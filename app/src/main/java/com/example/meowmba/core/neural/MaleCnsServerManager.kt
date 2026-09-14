package com.example.meowmba.core.neural

import com.example.meowmba.core.bridge.DesktopBridgeManager
import com.example.meowmba.core.bridge.DesktopBridgeProtocol
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.UUID

/** Public configuration contains no pairing code, certificate secret or authentication material. */
data class MaleCnsServerSelection(
    val useDedicated: Boolean = false,
    val serverName: String = "Paired computer",
    val endpoint: String = "",
    val paired: Boolean = false,
)

internal interface MaleCnsServerBackend {
    fun pairedComputer(): DesktopBridgeManager.PairedCredentials?
    suspend fun pair(payload: DesktopBridgeProtocol.PairingPayload,
        phoneName: String): Result<DesktopBridgeManager.PairedCredentials>
    fun transport(credentials: DesktopBridgeManager.PairedCredentials): MaleCnsTransport
}

/**
 * A separate server selection does not replace desktop automation credentials. Its entire record
 * is one encrypted preference, written only after the exact full graph has answered successfully.
 * Each transport captures an immutable identity so an old session's stop cannot hit a new server.
 */
class MaleCnsServerManager internal constructor(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val backend: MaleCnsServerBackend,
) {
    constructor(storage: SecureStorageManager) : this(
        { storage.getPreference(KEY) },
        { storage.saveSetupSelection(mapOf(KEY to it)) },
        DesktopBridgeManager(storage).let { bridge -> object : MaleCnsServerBackend {
            override fun pairedComputer() = bridge.pairedCredentials()
            override suspend fun pair(payload: DesktopBridgeProtocol.PairingPayload, phoneName: String) =
                bridge.pairCandidate(payload, phoneName)
            override fun transport(credentials: DesktopBridgeManager.PairedCredentials) = bridge.maleCnsTransport(credentials)
        } },
    )

    private val changes = Mutex()
    private class Stored(val dedicated: Boolean, val credentials: DesktopBridgeManager.PairedCredentials?)

    fun selection(): MaleCnsServerSelection {
        val stored = load()
        val credentials = if (stored.dedicated) stored.credentials else pairedComputer()
        return MaleCnsServerSelection(stored.dedicated,
            credentials?.bridge?.let { displayName(it) } ?: if (stored.dedicated) "Custom neural server" else "Paired computer",
            credentials?.bridge?.let { DesktopBridgeProtocol.endpointUrl(it.host, it.port, "/rpc").let { u ->
                "${u.host}:${u.port}"
            } }.orEmpty(), credentials != null)
    }

    fun createTransport(): MaleCnsTransport {
        val stored = load()
        val credentials = if (stored.dedicated) stored.credentials else pairedComputer()
        return credentials?.let(backend::transport) ?: object : MaleCnsTransport {
            override suspend fun step(request: MaleCnsRequest): Result<MaleCnsSnapshot> = Result.failure(
                IllegalStateException(if (stored.dedicated) "Pair the selected neural server again in Neural setup"
                else "Pair your computer or connect a neural server in Neural setup"))
            override suspend fun stop(session: String) = Unit
        }
    }

    suspend fun pairServer(payload: String, phoneName: String): Result<String> = operation {
        val decoded = DesktopBridgeProtocol.decodePairingPayload(payload).getOrThrow()
        val stored = load()
        // A helper has one phone secret. Re-pairing a known certificate would revoke the existing
        // desktop channel; re-use its already trusted secret even when the owner supplies a new IP.
        val computer = pairedComputer()
        val known = listOfNotNull(computer, stored.credentials).firstOrNull {
            it.bridge.certSha256.equals(decoded.certSha256, ignoreCase = true)
        }
        var candidate = known?.let {
            DesktopBridgeManager.PairedCredentials(DesktopBridgeManager.PairedBridge(decoded.host,
                decoded.port, it.bridge.name, decoded.certSha256.lowercase()), it.secretB64)
        } ?: backend.pair(decoded, phoneName).getOrThrow()
        var verified = probe(backend.transport(candidate))
        val isDesktopIdentity = computer?.bridge?.certSha256?.equals(decoded.certSha256, ignoreCase = true) == true
        if (known != null && !isDesktopIdentity && verified.isFailure &&
            verified.exceptionOrNull()?.message.orEmpty().let { "signature rejected" in it || "forgot the pairing" in it }) {
            // Explicit Connect includes a fresh one-use code. Only repair a custom-only identity;
            // a desktop secret is owned by the existing desktop pairing workflow.
            candidate = backend.pair(decoded, phoneName).getOrThrow()
            verified = probe(backend.transport(candidate))
        }
        verified.getOrThrow()
        save(Stored(true, candidate))
        "Connected · ${displayName(candidate.bridge)} · full MaleCNS graph verified"
    }

    /** Returning to the default preserves both the desktop pairing and saved custom credentials. */
    suspend fun usePairedComputer(): Result<String> = operation {
        save(Stored(false, load().credentials))
        if (pairedComputer() != null) "Using your paired computer for MaleCNS"
        else "Using paired computer · pair a computer to connect MaleCNS"
    }

    suspend fun testConnection(): Result<String> = operation {
        val current = selection()
        require(current.paired) { "Pair your computer or connect a neural server first" }
        probe(createTransport()).getOrThrow()
        "Connected · ${current.serverName} · full MaleCNS graph verified"
    }

    private suspend fun <T> operation(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        changes.withLock {
            try { Result.success(block()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Result.failure(IllegalStateException(safeFailure(failure))) }
        }
    }

    private suspend fun probe(transport: MaleCnsTransport): Result<Unit> {
        val request = MaleCnsRequest(UUID.randomUUID().toString(), 1, 0f, 0f, 0f, 0f, 0f, 0f,
            MaleCnsGraphProfile.FULL)
        return try {
            transport.step(request).mapCatching { snapshot ->
                require(snapshot.graph == MaleCnsGraphProfile.FULL) { "Server does not provide the required full MaleCNS graph" }
            }
        } finally {
            // The probe is a separate short-lived session; it must not leave a simulated animal
            // running after navigation/cancellation. Production stop has a bounded 2 s timeout.
            withContext(NonCancellable) { runCatching { transport.stop(request.session) } }
        }
    }

    /** Remote error bodies can contain arbitrary text. Never repeat them in user-facing state. */
    private fun safeFailure(failure: Exception): String {
        val detail = generateSequence<Throwable>(failure) { it.cause }.take(5)
            .joinToString(" ") { it.message.orEmpty().take(512) }.lowercase()
        return when {
            "payload" in detail || "base64" in detail || "invalid bridge" in detail ->
                "The pairing payload is invalid. Paste the complete payload shown by the server bridge."
            "certificate" in detail || "ssl" in detail || "trust" in detail ->
                "The server identity could not be verified. Check the pairing payload on that server."
            "pairing rejected" in detail || "expired code" in detail ->
                "Pairing was rejected. Open a new pairing window on the server and copy its new payload."
            "pairing window" in detail ->
                "Open the server bridge's pairing window, then try its new payload."
            "signature" in detail || "clocks" in detail ->
                "Authentication failed. Check both clocks and the server pairing."
            "forgot the pairing" in detail ->
                "The server no longer recognizes this pairing. Open a new pairing window and reconnect."
            "save" in detail || "storage" in detail ->
                "The verified connection could not be saved securely. Please try again."
            "pair your computer" in detail ->
                "Pair your computer or connect a neural server first."
            "full" in detail || "graph" in detail || "measurement" in detail ->
                "The server did not verify the required full MaleCNS graph. Check its worker and graph manifest."
            "unavailable" in detail || "unreachable" in detail || "timeout" in detail || "timed out" in detail || "refused" in detail ->
                "The neural server could not be reached. Check its address, network and running bridge."
            else -> "Could not verify the neural server. Check that its compatible bridge and full MaleCNS worker are running."
        }
    }

    private fun pairedComputer(): DesktopBridgeManager.PairedCredentials? = backend.pairedComputer()?.takeIf {
        runCatching {
            DesktopBridgeProtocol.endpointUrl(it.bridge.host, it.bridge.port, "/rpc")
            require(it.bridge.certSha256.matches(Regex("[a-fA-F0-9]{64}")))
            require(DesktopBridgeProtocol.b64Decode(it.secretB64).size == 32)
        }.isSuccess
    }

    private fun load(): Stored {
        val raw = read()?.takeIf { it.isNotBlank() } ?: return Stored(false, null)
        return runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            val dedicated = obj.getValue("dedicated").jsonPrimitive.boolean
            val encoded = obj["server"]?.takeUnless { it is JsonNull }?.jsonObject
            val credentials = encoded?.let {
                val bridge = DesktopBridgeManager.PairedBridge(it.getValue("host").jsonPrimitive.content,
                    it.getValue("port").jsonPrimitive.int, it.getValue("name").jsonPrimitive.content,
                    it.getValue("certificate").jsonPrimitive.content)
                DesktopBridgeProtocol.endpointUrl(bridge.host, bridge.port, "/rpc")
                require(bridge.certSha256.matches(Regex("[a-f0-9]{64}")))
                val secret = it.getValue("secret").jsonPrimitive.content
                require(DesktopBridgeProtocol.b64Decode(secret).size == 32)
                DesktopBridgeManager.PairedCredentials(bridge, secret)
            }
            Stored(dedicated, credentials)
        }.getOrElse { Stored(true, null) } // Corruption cannot silently route to a different server.
    }

    private fun save(stored: Stored) = write(buildJsonObject {
        put("dedicated", stored.dedicated)
        stored.credentials?.let { credentials -> putJsonObject("server") {
            put("host", credentials.bridge.host); put("port", credentials.bridge.port)
            put("name", credentials.bridge.name); put("certificate", credentials.bridge.certSha256)
            put("secret", credentials.secretB64)
        } }
    }.toString())

    private fun displayName(bridge: DesktopBridgeManager.PairedBridge): String =
        bridge.name.ifBlank { bridge.host }.replace(Regex("[\\p{Cntrl}]"), " ").take(80)

    internal companion object { const val KEY = "malecns_server_selection_v1" }
}
