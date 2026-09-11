package com.example.meowmba.core.agentic

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Phase-4+ SSH exec surface for [AgenticRuntime].
 *
 * Android APKs do not ship OpenSSH. This executor:
 * 1. Returns [SshExecResult.NotEnabled] when the feature flag is off.
 * 2. Resolves credentials via [CredentialResolver] (clear error if missing).
 * 3. Verifies / pins [ConnectionProfile.hostKeyFingerprint] (see host-key policy).
 * 4. Attempts `ssh` / `sshpass` via [ProcessBuilder] only if a client binary
 *    is present (e.g. Termux PATH / rooted device).
 * 5. Otherwise returns [SshExecResult.Unsupported] documenting the Termux /
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

        val hostKey = verifyOrPinHostKey(profile)
        if (hostKey is SshExecResult) return hostKey
        val effectiveProfile = hostKey as ConnectionProfile

        val sshBinary = findSshBinary()
            ?: return SshExecResult.Unsupported(
                "OpenSSH client not available in the APK. " +
                    "Install ssh on-device (Termux) or run via an HTTPS_RUNTIME worker. " +
                    "ProcessBuilder path requires a local ssh binary.",
            )

        return runProcessBuilder(sshBinary, effectiveProfile, secret, argv, timeoutMs.coerceAtLeast(1_000L))
    }

    /**
     * Host-key verification / TOFU pin.
     * @return updated [ConnectionProfile] on success, or [SshExecResult] on failure.
     */
    private fun verifyOrPinHostKey(profile: ConnectionProfile): Any {
        val observed = probeHostKeyFingerprint(profile.host, profile.port)
        val saved = profile.hostKeyFingerprint?.trim()?.takeIf { it.isNotEmpty() }

        if (observed == null) {
            // Cannot probe (no ssh-keyscan). If already pinned, proceed with
            // StrictHostKeyChecking=accept-new only when unpinned+policy; if
            // pinned we still refuse blind connect without a probe when possible.
            return when {
                saved != null -> profile // will enforce via known_hosts if we have key material later
                profile.pinHostKeyOnFirstConnect -> profile
                else -> SshExecResult.Error(
                    "No hostKeyFingerprint on profile \"${profile.displayName}\" and " +
                        "pinHostKeyOnFirstConnect=false. Set a fingerprint in Settings.",
                )
            }
        }

        val normalizedObserved = normalizeFingerprint(observed)
        if (saved != null) {
            val normalizedSaved = normalizeFingerprint(saved)
            if (normalizedSaved != normalizedObserved) {
                return SshExecResult.Error(
                    "Host key fingerprint mismatch for ${profile.host}:${profile.port}. " +
                        "Refusing to connect. If the server was reinstalled, clear/update " +
                        "hostKeyFingerprint on the connection profile.",
                )
            }
            return profile
        }

        if (!profile.pinHostKeyOnFirstConnect) {
            return SshExecResult.Error(
                "First connect for \"${profile.displayName}\" but pinHostKeyOnFirstConnect=false. " +
                    "Set hostKeyFingerprint manually before SSH exec.",
            )
        }

        val pinned = profile.copy(hostKeyFingerprint = normalizedObserved)
        profiles.save(pinned)
        return pinned
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

        val strictMode = if (profile.hostKeyFingerprint.isNullOrBlank()) {
            "accept-new"
        } else {
            "yes"
        }

        val baseSsh = mutableListOf(
            sshBinary,
            "-p", profile.port.toString(),
            "-o", "BatchMode=no",
            "-o", "StrictHostKeyChecking=$strictMode",
            "-o", "ConnectTimeout=10",
        )

        // Prefer a dedicated known_hosts when we have a fingerprint from keyscan.
        val knownHosts = writeKnownHostsIfPossible(profile)
        if (knownHosts != null) {
            baseSsh += listOf("-o", "UserKnownHostsFile=${knownHosts.absolutePath}")
            baseSsh += listOf("-o", "GlobalKnownHostsFile=/dev/null")
        }

        val command: List<String>
        val envExtra: Map<String, String>
        if (sshpass != null) {
            command = listOf(sshpass, "-e") + baseSsh + listOf(target, remoteCommand)
            envExtra = mapOf("SSHPASS" to secret)
        } else {
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
        } finally {
            knownHosts?.delete()
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

        private val KEYSCAN_CANDIDATES = listOf(
            "/data/data/com.termux/files/usr/bin/ssh-keyscan",
            "/system/bin/ssh-keyscan",
            "/system/xbin/ssh-keyscan",
            "ssh-keyscan",
        )

        private val KEYGEN_CANDIDATES = listOf(
            "/data/data/com.termux/files/usr/bin/ssh-keygen",
            "/system/bin/ssh-keygen",
            "/system/xbin/ssh-keygen",
            "ssh-keygen",
        )

        fun findSshBinary(): String? = firstExisting(SSH_CANDIDATES)

        fun findSshpassBinary(): String? = firstExisting(SSHPASS_CANDIDATES)

        fun findKeyscanBinary(): String? = firstExisting(KEYSCAN_CANDIDATES)

        fun findKeygenBinary(): String? = firstExisting(KEYGEN_CANDIDATES)

        fun normalizeFingerprint(raw: String): String {
            val t = raw.trim()
            // Keep SHA256:… form; strip leading "SHA256 " variants / host labels.
            val sha = Regex("""SHA256:[A-Za-z0-9+/=]+""").find(t)?.value
            if (sha != null) return sha
            val md5 = Regex("""(?i)(?:MD5:)?([0-9a-f]{2}(?::[0-9a-f]{2}){15})""").find(t)?.groupValues?.get(1)
            if (md5 != null) return "MD5:$md5"
            return t
        }

        fun probeHostKeyFingerprint(host: String, port: Int): String? {
            val keyscan = findKeyscanBinary() ?: return null
            val keygen = findKeygenBinary() ?: return null
            return try {
                val scan = ProcessBuilder(keyscan, "-T", "5", "-p", port.toString(), host)
                    .redirectErrorStream(true)
                    .start()
                if (!scan.waitFor(8, TimeUnit.SECONDS)) {
                    scan.destroyForcibly()
                    return null
                }
                val keyLine = scan.inputStream.bufferedReader().readText()
                    .lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                    ?: return null

                val gen = ProcessBuilder(keygen, "-lf", "-")
                    .redirectErrorStream(true)
                    .start()
                gen.outputStream.bufferedWriter().use { it.write(keyLine); it.flush() }
                gen.outputStream.close()
                if (!gen.waitFor(5, TimeUnit.SECONDS)) {
                    gen.destroyForcibly()
                    return null
                }
                val out = gen.inputStream.bufferedReader().readText()
                normalizeFingerprint(out).takeIf { it.isNotBlank() }
            } catch (_ex: Exception) {
                null
            }
        }

        private fun writeKnownHostsIfPossible(profile: ConnectionProfile): File? {
            val keyscan = findKeyscanBinary() ?: return null
            return try {
                val scan = ProcessBuilder(keyscan, "-T", "5", "-p", profile.port.toString(), profile.host)
                    .redirectErrorStream(true)
                    .start()
                if (!scan.waitFor(8, TimeUnit.SECONDS)) {
                    scan.destroyForcibly()
                    return null
                }
                val body = scan.inputStream.bufferedReader().readText()
                    .lineSequence()
                    .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
                    .joinToString("\n")
                if (body.isBlank()) return null
                val f = File.createTempFile("agentic_known_hosts_", ".txt")
                f.writeText(body + "\n")
                f
            } catch (_ex: Exception) {
                null
            }
        }

        private fun firstExisting(candidates: List<String>): String? {
            for (c in candidates) {
                if (c.contains('/')) {
                    if (File(c).canExecute()) return c
                } else {
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
