package com.example.meowmba.core.agentic

/**
 * Connection target for agentic runtime v1 (Phase 4+).
 *
 * [credentialRef] is a Keystore / SecureStorage key name only — never store
 * passwords or private keys in this model or in event/prompt text.
 *
 * ## Host-key policy
 * - [hostKeyFingerprint]: when set, SSH exec **rejects** a mismatch (MITM guard).
 * - [pinHostKeyOnFirstConnect]: when true and fingerprint is null, the first
 *   successful probe may **pin** the observed fingerprint onto the profile
 *   (trust-on-first-use). When false and fingerprint is null, exec fails until
 *   the operator sets a fingerprint in Settings.
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
    val pinHostKeyOnFirstConnect: Boolean = true,
)
