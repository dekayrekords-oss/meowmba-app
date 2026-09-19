package com.example.meowmba.ui.companion

import android.content.Context
import com.example.meowmba.core.agentic.ConnectionProfile
import com.example.meowmba.core.agentic.ConnectionProfileType
import com.example.meowmba.core.agentic.HttpsRuntimeClient
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
            probeKind == LocalLinuxKind.TERMUX_LINUX.name ->
                "Termux's shell is available. Open Terminal to check your Linux workspace."
            ready ->
                if (runCatching { com.example.meowmba.core.computer.TermuxSetupSupport.detect(context) }.getOrNull() == com.example.meowmba.core.computer.TermuxSetupSupport.GUIDED)
                    "Termux is installed, but this edition needs guided setup. No command permission can be granted. Open Set up Computer; keep your existing Ubuntu files."
                else "Termux is installed. Finish Computer setup and verify the private connection before opening the Ubuntu Terminal."
            else ->
                "Termux has not been detected. Use Set up Computer → This phone to get started."
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
        if (!install && profile.type == ConnectionProfileType.HTTPS_RUNTIME) {
            return@withContext testHttpsRuntime(credentials, profile)
        }
        val profiles = com.example.meowmba.core.agentic.RoomConnectionProfileStore(
            com.example.meowmba.core.database.MeowmbaDatabase.getDatabase(context).connectionProfileDao())
        val currentProfile = profiles.get(profile.id) ?: profile
        val client = ComputerInstallOverSsh(credentials) { profiles.save(it) }
        if (!install) return@withContext client.testConnection(currentProfile)
        val script = loadInstallScript(context)
        client.install(currentProfile, script, onLogLine = onLog)
    }

    /** Health-check an HTTPS_RUNTIME worker; surfaces reachability honestly. */
    fun testHttpsRuntime(
        credentials: CredentialResolver,
        profile: ConnectionProfile,
    ): Result<String> {
        if (profile.type != ConnectionProfileType.HTTPS_RUNTIME) {
            return Result.failure(IllegalArgumentException("Expected HTTPS_RUNTIME profile"))
        }
        val token = credentials.resolve(profile.credentialRef)
        val base = HttpsRuntimeClient.baseUrlFor(profile)
        val health = HttpsRuntimeClient(baseUrl = base, bearerToken = token).health()
        return if (health.ok) {
            Result.success("HTTPS_RUNTIME OK at $base (${health.detail.take(120)})")
        } else {
            val hint = ComputerProfileMarks.httpsReachabilityHint(base, profile.port.takeIf { it > 0 }
                ?: ComputerProfileMarks.WORKER_PORT)
            Result.failure(
                IllegalStateException(
                    "HTTPS_RUNTIME unreachable: ${health.detail.take(160)}. $hint",
                ),
            )
        }
    }
}
