package com.example.meowmba.core.agentic

import com.example.meowmba.core.security.SecureStorageManager

/**
 * Resolves [ConnectionProfile.credentialRef] to secret material from
 * Keystore-backed storage. Never logs or returns secrets into agentic events.
 */
interface CredentialResolver {
    /**
     * @return secret string for [credentialRef], or null if not configured.
     */
    fun resolve(credentialRef: String): String?
}

/** Always-null stub for unit tests / flag-OFF design. */
class NullCredentialResolver : CredentialResolver {
    override fun resolve(credentialRef: String): String? = null
}

/**
 * Reads `pref_agentic_cred_<ref>` via [SecureStorageManager].
 * Callers must configure the credential out-of-band (Settings / adb / future UI).
 */
class SecureStorageCredentialResolver(
    private val storage: SecureStorageManager,
) : CredentialResolver {
    override fun resolve(credentialRef: String): String? {
        val ref = credentialRef.trim()
        if (ref.isEmpty()) return null
        return storage.getPreference(prefKey(ref))?.takeIf { it.isNotEmpty() }
    }

    /** Persist a credential for [credentialRef]. Never log [secret]. */
    fun store(credentialRef: String, secret: String) {
        saveCredential(storage, credentialRef, secret)
    }

    companion object {
        fun prefKey(credentialRef: String): String = "agentic_cred_$credentialRef"

        /** Write helper for debug / settings — not for prompts. */
        fun saveCredential(storage: SecureStorageManager, credentialRef: String, secret: String) {
            storage.savePreference(prefKey(credentialRef.trim()), secret)
        }
    }
}
