package com.example.meowmba.core.ssh

import com.example.meowmba.core.security.SecureStorageManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encrypted registry of SSH servers meow is allowed to manage.
 * Credentials live in Keystore-backed EncryptedSharedPreferences only —
 * never logged, never backed up, never sent anywhere but the server itself.
 */
data class SshHost(
    val name: String,
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
)

class SshHostRegistry(private val storage: SecureStorageManager) {
    private val key = "ssh_hosts"

    fun list(): List<SshHost> = runCatching {
        val raw = storage.getPreference(key) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SshHost(
                name = o.getString("name"),
                host = o.getString("host"),
                port = o.optInt("port", 22),
                user = o.getString("user"),
                password = o.optString("password", ""),
            )
        }
    }.getOrDefault(emptyList())

    fun get(name: String): SshHost? = list().firstOrNull {
        it.name.equals(name.trim(), ignoreCase = true)
    }

    fun save(host: SshHost) {
        val others = list().filterNot { it.name.equals(host.name, ignoreCase = true) }
        val arr = JSONArray()
        (others + host).forEach { h ->
            arr.put(
                JSONObject()
                    .put("name", h.name)
                    .put("host", h.host)
                    .put("port", h.port)
                    .put("user", h.user)
                    .put("password", h.password)
            )
        }
        storage.savePreference(key, arr.toString())
    }

    fun delete(name: String) {
        val remaining = list().filterNot { it.name.equals(name, ignoreCase = true) }
        val arr = JSONArray()
        remaining.forEach { h ->
            arr.put(
                JSONObject()
                    .put("name", h.name).put("host", h.host).put("port", h.port)
                    .put("user", h.user).put("password", h.password)
            )
        }
        storage.savePreference(key, arr.toString())
    }

    /** Trust-on-first-use host key fingerprints, keyed by host:port. */
    fun hostKey(host: String, port: Int): String? =
        storage.getPreference("ssh_hostkey_$host:$port")

    fun saveHostKey(host: String, port: Int, fingerprint: String) {
        storage.savePreference("ssh_hostkey_$host:$port", fingerprint)
    }
}
