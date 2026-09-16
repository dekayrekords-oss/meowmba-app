package com.example.meowmba.core.agentic

import com.example.meowmba.core.security.SecureStorageManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Persist [ConnectionProfile] records for agentic SSH / HTTPS runtime targets.
 *
 * When [FeatureFlags.agenticRuntimeV1] is OFF, [AgenticRuntime] returns empty
 * lists and does not call mutators — this store is only active for flag-ON
 * callers. Secrets stay in [CredentialResolver] / SecureStorage, never here.
 */
interface ConnectionProfileStore {
    fun list(): List<ConnectionProfile>
    fun get(id: String): ConnectionProfile?
    fun save(profile: ConnectionProfile): ConnectionProfile
    fun delete(id: String): Boolean
}

/** Always-empty store for flag-OFF / design-time callers. */
class EmptyConnectionProfileStore : ConnectionProfileStore {
    override fun list(): List<ConnectionProfile> = emptyList()
    override fun get(id: String): ConnectionProfile? = null
    override fun save(profile: ConnectionProfile): ConnectionProfile = profile
    override fun delete(id: String): Boolean = false
}

/** Process-local concurrent map (mirrors [InMemoryAgenticGoalStore]). */
class InMemoryConnectionProfileStore : ConnectionProfileStore {
    private val profiles = ConcurrentHashMap<String, ConnectionProfile>()

    override fun list(): List<ConnectionProfile> =
        profiles.values.sortedBy { it.displayName.lowercase() }

    override fun get(id: String): ConnectionProfile? = profiles[id]

    override fun save(profile: ConnectionProfile): ConnectionProfile {
        val id = profile.id.ifBlank { UUID.randomUUID().toString() }
        val saved = if (profile.id == id) profile else profile.copy(id = id)
        profiles[saved.id] = saved
        return saved
    }

    override fun delete(id: String): Boolean = profiles.remove(id) != null
}

/**
 * Encrypted SharedPreferences persistence (legacy / fallback).
 * Prefer [RoomConnectionProfileStore] when Room is available.
 * Stores metadata + [ConnectionProfile.credentialRef] only — no password fields.
 */
class SecureConnectionProfileStore(
    private val storage: SecureStorageManager,
) : ConnectionProfileStore {
    private val key = "agentic_connection_profiles"

    override fun list(): List<ConnectionProfile> = runCatching {
        val raw = storage.getPreference(key) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i -> decode(arr.getJSONObject(i)) }
    }.getOrDefault(emptyList())

    override fun get(id: String): ConnectionProfile? =
        list().firstOrNull { it.id == id }

    override fun save(profile: ConnectionProfile): ConnectionProfile {
        val id = profile.id.ifBlank { UUID.randomUUID().toString() }
        val saved = if (profile.id == id) profile else profile.copy(id = id)
        val others = list().filterNot { it.id == saved.id }
        persist(others + saved)
        return saved
    }

    override fun delete(id: String): Boolean {
        val before = list()
        val remaining = before.filterNot { it.id == id }
        if (remaining.size == before.size) return false
        persist(remaining)
        return true
    }

    private fun persist(profiles: List<ConnectionProfile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            val o = JSONObject()
                .put("id", p.id)
                .put("displayName", p.displayName)
                .put("type", p.type.name)
                .put("host", p.host)
                .put("port", p.port)
                .put("username", p.username)
                .put("credentialRef", p.credentialRef)
                .put("pinHostKeyOnFirstConnect", p.pinHostKeyOnFirstConnect)
            if (p.hostKeyFingerprint != null) o.put("hostKeyFingerprint", p.hostKeyFingerprint)
            if (p.workspaceRoot != null) o.put("workspaceRoot", p.workspaceRoot)
            arr.put(o)
        }
        storage.savePreference(key, arr.toString())
    }

    private fun decode(o: JSONObject): ConnectionProfile? = runCatching {
        val typeName = o.getString("type")
        val type = enumValues<ConnectionProfileType>().firstOrNull { it.name == typeName }
            ?: return null
        ConnectionProfile(
            id = o.getString("id"),
            displayName = o.getString("displayName"),
            type = type,
            host = o.getString("host"),
            port = o.optInt("port", 22),
            username = o.getString("username"),
            credentialRef = o.optString("credentialRef", ""),
            hostKeyFingerprint = optionalString(o, "hostKeyFingerprint"),
            workspaceRoot = optionalString(o, "workspaceRoot"),
            pinHostKeyOnFirstConnect = o.optBoolean("pinHostKeyOnFirstConnect", true),
        )
    }.getOrNull()

    private fun optionalString(o: JSONObject, name: String): String? {
        if (!o.has(name) || o.isNull(name)) return null
        return o.optString(name, "").takeIf { it.isNotBlank() }
    }
}
