package com.example.meowmba.core.agentic

import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.ssh.SshHost
import com.example.meowmba.core.ssh.SshHostRegistry
import java.util.UUID

/**
 * Bridge between agentic [ConnectionProfile] / [AgenticRuntime.sshExec] and the
 * legacy chat-agent [SshHostRegistry] / [com.example.meowmba.core.agent.tools.SshTool].
 *
 * Design:
 * - Agentic profiles store **credentialRef only** (SecureStorage).
 * - Legacy registry stores encrypted password inline on [SshHost].
 * - Bridge copies host/user/port metadata both ways without putting secrets
 *   into agentic events or prompts.
 * - When importing a legacy host, password is written to `agentic_cred_<ref>`
 *   and the profile references that ref — secrets stay in SecureStorage.
 */
object AgenticSshBridge {

    fun listLegacyHosts(storage: SecureStorageManager): List<SshHost> =
        SshHostRegistry(storage).list()

    /**
     * Import a legacy [SshHost] into a [ConnectionProfile] + credential ref.
     * Returns the saved profile, or null if name/host blank.
     */
    fun importLegacyHost(
        storage: SecureStorageManager,
        profileStore: ConnectionProfileStore,
        credentials: SecureStorageCredentialResolver,
        hostName: String,
    ): ConnectionProfile? {
        val host = SshHostRegistry(storage).get(hostName) ?: return null
        val ref = "legacy_${host.name.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_").take(40)}"
        if (host.password.isNotEmpty()) {
            SecureStorageCredentialResolver.saveCredential(storage, ref, host.password)
        }
        val fingerprint = SshHostRegistry(storage).hostKey(host.host, host.port)
        val profile = ConnectionProfile(
            id = "ssh-legacy-${host.name.trim().lowercase().take(24)}-${UUID.randomUUID().toString().take(6)}",
            displayName = host.name,
            type = ConnectionProfileType.SSH,
            host = host.host,
            port = host.port,
            username = host.user,
            credentialRef = ref,
            hostKeyFingerprint = fingerprint,
            pinHostKeyOnFirstConnect = fingerprint == null,
        )
        return profileStore.save(profile)
    }

    /**
     * Export an agentic SSH profile into the legacy registry (for SshTool).
     * Resolves credentialRef → password into the encrypted registry entry.
     * Returns false when profile missing / wrong type / credential unresolved.
     */
    fun exportToLegacyRegistry(
        storage: SecureStorageManager,
        profile: ConnectionProfile,
        credentials: CredentialResolver,
    ): Boolean {
        if (profile.type != ConnectionProfileType.SSH) return false
        val secret = credentials.resolve(profile.credentialRef) ?: return false
        val registry = SshHostRegistry(storage)
        val name = profile.displayName.ifBlank { profile.host }.take(64)
        registry.save(
            SshHost(
                name = name,
                host = profile.host,
                port = profile.port,
                user = profile.username,
                password = secret,
            ),
        )
        profile.hostKeyFingerprint?.let { fp ->
            registry.saveHostKey(profile.host, profile.port, fp)
        }
        return true
    }

    /**
     * Resolve a profile id **or** legacy host name for sshExec.
     * Prefer ConnectionProfile; fall back to ephemeral profile from registry.
     */
    fun resolveProfileId(
        profileIdOrHostName: String,
        profileStore: ConnectionProfileStore,
        storage: SecureStorageManager,
        credentials: SecureStorageCredentialResolver,
    ): String? {
        val id = profileIdOrHostName.trim()
        if (id.isEmpty()) return null
        profileStore.get(id)?.let { return it.id }
        // Match by displayName
        profileStore.list().firstOrNull {
            it.displayName.equals(id, ignoreCase = true) ||
                it.host.equals(id, ignoreCase = true)
        }?.let { return it.id }
        // Import on the fly from legacy registry
        val imported = importLegacyHost(storage, profileStore, credentials, id) ?: return null
        return imported.id
    }
}
