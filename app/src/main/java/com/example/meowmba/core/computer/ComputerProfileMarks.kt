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
    const val WORKER_PORT = 8766

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
