package com.example.meowmba.core.computer

import com.example.meowmba.core.agentic.ConnectionProfile
import com.example.meowmba.core.security.SecureStorageManager

/**
 * Mark SSH / HTTPS_RUNTIME profiles as the user's **Computer** without a Room
 * schema migration. Uses SecureStorage id + workspace path convention.
 */
object ComputerProfileMarks {
    const val PREF_PROFILE_ID = "computer_profile_id"
    const val WORKSPACE_MARKER = "meow-computer"
    const val DEFAULT_REMOTE_WORKSPACE = "/opt/meow-computer"
    const val DEFAULT_REMOTE_WORKSPACE_HOME = "~/meow-computer"
    const val WORKER_HEALTH_PATH = "/health"
    /**
     * Meow Computer HTTP worker port. Intentionally **not** 8766 — Desktop Bridge
     * UDP discovery uses 8766 ([com.example.meowmba.core.bridge.BridgeDiscovery]).
     */
    const val WORKER_PORT = 18766
    /** Example NetBird IP on meow (operator LAN); use with http://HOST:WORKER_PORT. */
    const val EXAMPLE_NETBIRD_HOST = "100.77.97.236"

    fun isComputer(profile: ConnectionProfile, storage: SecureStorageManager?): Boolean {
        val markedId = storage?.getPreference(PREF_PROFILE_ID)
        if (!markedId.isNullOrBlank() && markedId == profile.id) return true
        val root = profile.workspaceRoot.orEmpty()
        return root.contains(WORKSPACE_MARKER)
    }

    fun mark(storage: SecureStorageManager, profileId: String) {
        storage.savePreference(PREF_PROFILE_ID, profileId)
    }

    fun clear(storage: SecureStorageManager) {
        storage.savePreference(PREF_PROFILE_ID, "")
    }

    /** Honest reachability hint when HTTPS_RUNTIME points at loopback. */
    fun httpsReachabilityHint(hostOrUrl: String, port: Int = WORKER_PORT): String {
        val raw = hostOrUrl.trim().lowercase()
        val loopback = raw.contains("127.0.0.1") || raw.contains("localhost") ||
            raw == "0.0.0.0" || raw.endsWith("://0.0.0.0")
        return if (loopback) {
            "Fold cannot reach 127.0.0.1 on meow. Prefer SSH day-to-day, or " +
                "ssh -L $port:127.0.0.1:$port user@meow, or bind the worker to a " +
                "NetBird IP (e.g. http://$EXAMPLE_NETBIRD_HOST:$port)."
        } else {
            "HTTPS_RUNTIME needs a URL the phone can reach (NetBird IP or SSH " +
                "-L). Worker default port is $WORKER_PORT (not Bridge 8766)."
        }
    }
}

enum class ComputerSetupStatus {
    NotSetUp,
    Connected,
    Installing,
    Ready,
    Error,
}

fun ComputerSetupStatus.toHumanSentence(): String = when (this) {
    ComputerSetupStatus.NotSetUp ->
        "Not set up yet. Install Termux on this phone, or add a Linux server."
    ComputerSetupStatus.Connected ->
        "Connected. You can open the terminal or keep working."
    ComputerSetupStatus.Installing ->
        "Installing Meow Computer on the server. You can watch the log below."
    ComputerSetupStatus.Ready ->
        "Ready. Your Computer workspace is available."
    ComputerSetupStatus.Error ->
        "Something went wrong. Check the message below — passwords are never shown."
}
