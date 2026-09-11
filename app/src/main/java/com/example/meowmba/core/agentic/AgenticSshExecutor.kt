package com.example.meowmba.core.agentic

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Phase-4 SSH exec surface for [AgenticRuntime].
 *
 * Android APKs do not ship OpenSSH. This executor:
 * 1. Returns [SshExecResult.NotEnabled] when the feature flag is off.
 * 2. Resolves credentials via [CredentialResolver] (clear error if missing).
 * 3. Attempts `ssh` / `sshpass` via [ProcessBuilder] only if a client binary
 *    is present (e.g. Termux PATH / rooted device).
 * 4. Otherwise returns [SshExecResult.Unsupported] documenting the Termux /
 *    runtime-worker requirement.
 *
 * Existing chat-agent [com.example.meowmba.core.agent.tools.SshTool] (JSch)
 * remains separate; this path is the agentic tool registry entry point.
 */
sealed class SshExecResult {
    data class Ok(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) : SshExecResult()

    data class NotEnabled(
        val message: String = "agentic_runtime_v1 is OFF",
    ) : SshExecResult()

    data class Unsupported(
        val message: String,
    ) : SshExecResult()

    data class Error(
        val message: String,
    ) : SshExecResult()
}

fun interface AgenticSshExecutor {
    fun exec(profileId: String, argv: List<String>, timeoutMs: Long): SshExecResult
}

class DefaultAgenticSshExecutor(
    private val enabled: () -> Boolean,
    private val profiles: ConnectionProfileStore,
    private val credentials: CredentialResolver,
) : AgenticSshExecutor {

    override fun exec(profileId: String, argv: List<String>, timeoutMs: Long): SshExecResult {
        if (!enabled()) {
            return SshExecResult.NotEnabled()
        }
        if (profileId.isBlank()) {
            return SshExecResult.Error("profileId is required")
        }
        if (argv.isEmpty()) {
            return SshExecResult.Error("argv must not be empty")
        }

        val profile = profiles.get(profileId)
            ?: return SshExecResult.Error("Unknown connection profile \"$profileId\"")

        if (profile.type != ConnectionProfileType.SSH) {
            return SshExecResult.Error(
                "Profile \"${profile.displayName}\" is ${profile.type}; sshExec requires SSH",
            )
        }

        val secret = credentials.resolve(profile.credentialRef)
        if (secret.isNullOrEmpty()) {
            return SshExecResult.Error(
                "No credential configured for ref \"${profile.credentialRef}\". " +
                    "Store it via SecureStorage (agentic_cred_<ref>); never put secrets in prompts.",
            )
        }

        val sshBinary = findSshBinary()
            ?: return SshExecResult.Unsupported(
                "OpenSSH client not available in the APK. " +
                    "Install ssh on-device (Termux) or run via an HTTPS_RUNTIME worker. " +
                    "ProcessBuilder path requires a local ssh binary.",
            )

        return runProcessBuilder(sshBinary, profile, secret, argv, timeoutMs.coerceAtLeast(1_000L))
    }

    private fun runProcessBuilder(
        sshBinary: String,
        profile: ConnectionProfile,
        secret: String,
        argv: List<String>,
        timeoutMs: Long,
    ): SshExecResult {
        val sshpass = findSshpassBinary()
        val remoteCommand = argv.joinToString(" ")
        val target = "${profile.username}@${profile.host}"
        // hostKeyFingerprint / workspaceRoot remain on the profile for Termux
        // workers / future known_hosts verify; not applied on ProcessBuilder yet.
        val baseSsh = listOf(
            sshBinary,
            "-p", profile.port.toString(),
            "-o", "BatchMode=no",
            "-o", "StrictHostKeyChecking=accept-new",
            "-o", "ConnectTimeout=10",
        )

        val command: List<String>
        val envExtra: Map<String, String>
        if (sshpass != null) {
            command = listOf(sshpass, "-e") + baseSsh + listOf(target, remoteCommand)
            envExtra = mapOf("SSHPASS" to secret)
        } else {
            // Without sshpass, password auth via ProcessBuilder is unsupported
            // (would require interactive or key files). Prefer key-based later.
            return SshExecResult.Unsupported(
                "ssh found at $sshBinary but sshpass is missing; " +
                    "password-ref auth needs sshpass or a Termux/runtime key agent. " +
                    "Credential was resolved but not passed on argv (no secret leakage).",
            )
        }

        return try {
            val pb = ProcessBuilder(command)
            pb.redirectErrorStream(false)
            val env = pb.environment()
            env.putAll(envExtra)
            val proc = pb.start()
            // Clear local copy reference after handing to process env
            val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return SshExecResult.Error("SSH timed out after ${timeoutMs}ms")
            }
            val stdout = proc.inputStream.bufferedReader().use { it.readText() }.take(MAX_OUTPUT)
            val stderr = proc.errorStream.bufferedReader().use { it.readText() }.take(MAX_OUTPUT)
            SshExecResult.Ok(exitCode = proc.exitValue(), stdout = stdout, stderr = stderr)
        } catch (e: Exception) {
            SshExecResult.Error("SSH process failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        private const val MAX_OUTPUT = 8_192

        private val SSH_CANDIDATES = listOf(
            "/data/data/com.termux/files/usr/bin/ssh",
            "/system/bin/ssh",
            "/system/xbin/ssh",
            "ssh",
        )

        private val SSHPASS_CANDIDATES = listOf(
            "/data/data/com.termux/files/usr/bin/sshpass",
            "/system/bin/sshpass",
            "/system/xbin/sshpass",
            "sshpass",
        )

        fun findSshBinary(): String? = firstExisting(SSH_CANDIDATES)

        fun findSshpassBinary(): String? = firstExisting(SSHPASS_CANDIDATES)

        private fun firstExisting(candidates: List<String>): String? {
            for (c in candidates) {
                if (c.contains('/')) {
                    if (File(c).canExecute()) return c
                } else {
                    // bare name: only useful if PATH is set (Termux); probe which
                    val which = tryWhich(c)
                    if (which != null) return which
                }
            }
            return null
        }

        private fun tryWhich(name: String): String? {
            return try {
                val p = ProcessBuilder("sh", "-c", "command -v $name").start()
                if (!p.waitFor(2, TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                    return null
                }
                val out = p.inputStream.bufferedReader().readText().trim()
                out.takeIf { it.isNotEmpty() && File(it).canExecute() }
            } catch (_ex: Exception) {
                null
            }
        }
    }
}
