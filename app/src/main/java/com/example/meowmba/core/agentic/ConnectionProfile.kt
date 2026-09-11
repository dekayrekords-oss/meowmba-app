package com.example.meowmba.core.agentic

/**
 * Connection target for agentic runtime v1 (Phase 4).
 *
 * [credentialRef] is a Keystore / SecureStorage key name only — never store
 * passwords or private keys in this model or in event/prompt text.
 */
enum class ConnectionProfileType {
    SSH,
    HTTPS_RUNTIME,
}

data class ConnectionProfile(
    val id: String,
    val displayName: String,
    val type: ConnectionProfileType,
    val host: String,
    val port: Int = 22,
    val username: String,
    /** SecureStorage / Keystore reference id — not a secret value. */
    val credentialRef: String,
    val hostKeyFingerprint: String? = null,
    val workspaceRoot: String? = null,
)
