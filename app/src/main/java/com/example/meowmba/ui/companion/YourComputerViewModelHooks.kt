package com.example.meowmba.ui.companion

import android.content.Context
import com.example.meowmba.core.agentic.ConnectionProfile
import com.example.meowmba.core.agentic.ConnectionProfileStore
import com.example.meowmba.core.agentic.CredentialResolver
import com.example.meowmba.core.agentic.SecureStorageCredentialResolver
import com.example.meowmba.core.computer.ComputerInstallOverSsh
import com.example.meowmba.core.computer.ComputerProfileMarks
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

/**
 * Shared helpers for Your Computer actions (Settings). Keeps secrets out of logs.
 */
object YourComputerViewModelHooks {

    fun loadInstallScript(context: Context): String {
        return context.assets.open("computer/install.sh").use { inp ->
            inp.readBytes().toString(StandardCharsets.UTF_8)
        }
    }

    fun phoneProbeSummary(context: Context): Pair<String, Boolean> {
        val termuxInstalled = runCatching {
            context.packageManager.getPackageInfo("com.termux", 0)
            true
        }.getOrDefault(false)
        val probeKind = runCatching {
            LocalLinuxSupport.probe(context).kind.name
        }.getOrNull()
        val ready = termuxInstalled ||
            (probeKind?.contains("TERMUX", true) == true) ||
            (probeKind?.contains("PROOT", true) == true)
        val summary = when {
            ready && probeKind != null ->
                "Termux / PRoot guest detected ($probeKind). This phone can be your Computer."
            ready ->
                "Termux is installed. Open Terminal → Computer, or finish proot-distro setup."
            else ->
                "No Termux guest yet. Install Termux from F-Droid or GitHub, then open Terminal → Computer."
        }
        return summary to ready
    }

    suspend fun saveProfile(
        store: ConnectionProfileStore,
        storage: SecureStorageManager,
        credentials: CredentialResolver,
        profile: ConnectionProfile,
        secret: String,
    ): ConnectionProfile {
        val marked = if (profile.workspaceRoot.isNullOrBlank()) {
            profile.copy(workspaceRoot = ComputerProfileMarks.DEFAULT_REMOTE_WORKSPACE)
        } else {
            profile
        }
        val saved = withContext(Dispatchers.IO) { store.save(marked) }
        ComputerProfileMarks.mark(storage, saved.id)
        if (secret.isNotEmpty()) {
            SecureStorageCredentialResolver.saveCredential(storage, saved.credentialRef, secret)
        }
        return saved
    }

    suspend fun testOrInstall(
        context: Context,
        credentials: CredentialResolver,
        profile: ConnectionProfile,
        install: Boolean,
        onLog: (String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        val client = ComputerInstallOverSsh(credentials)
        if (!install) return@withContext client.testConnection(profile)
        val script = loadInstallScript(context)
        client.install(profile, script, onLogLine = onLog)
    }
}
