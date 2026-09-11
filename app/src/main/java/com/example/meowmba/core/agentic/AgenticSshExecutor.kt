package com.example.meowmba.core.agentic

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Phase-4+ SSH exec surface for [AgenticRuntime].
 *
 * Android APKs do not ship OpenSSH. This executor:
 * 1. Returns [SshExecResult.NotEnabled] when the feature flag is off.
 * 2. Resolves credentials via [CredentialResolver] (clear error if missing).
 * 3. Verifies / pins [ConnectionProfile.hostKeyFingerprint] (see host-key policy).
 * 4. Prefers Termux / system `ssh` + `sshpass` via [ProcessBuilder] when executable.
 * 5. Optionally falls back to in-process JSch when ProcessBuilder OpenSSH is missing.
 * 6. Otherwise returns [SshExecResult.Unsupported] with [AgenticSshCapability] guidance
 *    (Termux RUN_COMMAND / HTTPS_RUNTIME worker).
 *
 * Chat-agent SshTool remains separate (SshHostRegistry); this path uses ConnectionProfile
 * credentialRef only.
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
    private val allowJschFallback: Boolean = true,
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

        if (profile.type == ConnectionProfileType.HTTPS_RUNTIME) {
            val token = credentials.resolve(profile.credentialRef)
            val client = HttpsRuntimeClient(
                baseUrl = HttpsRuntimeClient.baseUrlFor(profile),
                bearerToken = token,
            )
            return client.runArgv(
                argv = argv,
                cwd = profile.workspaceRoot,
                overallTimeoutMs = timeoutMs.coerceAtLeast(1_000L),
            )
        }

        if (profile.type != ConnectionProfileType.SSH) {
            return SshExecResult.Error(
                "Profile \"${profile.displayName}\" is ${profile.type}; sshExec requires SSH or HTTPS_RUNTIME",
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
        val sshpass = findSshpassBinary()
        val sshOk = sshBinary != null && isUsableBinary(sshBinary)
        val passOk = sshpass != null && isUsableBinary(sshpass)

        if (sshOk && passOk) {
            return runProcessBuilder(
                sshBinary!!,
                effectiveProfile,
                secret,
                argv,
                timeoutMs.coerceAtLeast(1_000L),
            )
        }

        if (allowJschFallback) {
            return runJsch(effectiveProfile, secret, argv, timeoutMs.coerceAtLeast(1_000L))
        }

        val cap = AgenticSshCapability.probe(jschAvailable = false)
        return SshExecResult.Unsupported(
            "OpenSSH ProcessBuilder path unavailable (ssh=${sshBinary ?: "none"}, " +
                "sshpass=${sshpass ?: "none"}). Capability: ${cap.summary}. " +
                "Install Termux ssh/sshpass (allow-external-apps + RUN_COMMAND) " +
                "or enable JSch fallback / HTTPS_RUNTIME worker.",
        )
    }

    /**
     * Host-key verification / TOFU pin.
     * @return updated [ConnectionProfile] on success, or [SshExecResult] on failure.
     */
    private fun verifyOrPinHostKey(profile: ConnectionProfile): Any {
        val observed = probeHostKeyFingerprint(profile.host, profile.port)
        val saved = profile.hostKeyFingerprint?.trim()?.takeIf { it.isNotEmpty() }

        if (observed == null) {
            return when {
                saved != null -> profile
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
        // Never argv.joinToString — that smashes spaces inside -lc scripts.
        // Prefer: ssh host bash -lc <script> as separate ProcessBuilder tokens.
        val target = "${profile.username}@${profile.host}"
        val fullScript = buildFullRemoteScript(profile.workspaceRoot, argv)

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

        val knownHosts = writeKnownHostsIfPossible(profile)
        if (knownHosts != null) {
            baseSsh += listOf("-o", "UserKnownHostsFile=${knownHosts.absolutePath}")
            baseSsh += listOf("-o", "GlobalKnownHostsFile=/dev/null")
        }

        val command: List<String>
        val envExtra: Map<String, String>
        if (sshpass != null && isUsableBinary(sshpass)) {
            command = listOf(sshpass, "-e") + baseSsh + listOf(target, "bash", "-lc", fullScript)
            envExtra = mapOf("SSHPASS" to secret)
        } else {
            knownHosts?.delete()
            return SshExecResult.Unsupported(
                "ssh found at $sshBinary but sshpass is missing or not executable; " +
                    "password-ref auth needs sshpass, Termux RUN_COMMAND, or JSch fallback. " +
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

    private fun runJsch(
        profile: ConnectionProfile,
        secret: String,
        argv: List<String>,
        timeoutMs: Long,
    ): SshExecResult {
        return try {
            val jsch = JSch()
            val session = jsch.getSession(profile.username, profile.host, profile.port)
            session.setPassword(secret)
            session.setConfig("StrictHostKeyChecking", "no")
            session.timeout = 10_000
            session.connect(10_000)
            try {
                val channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(buildRemoteExecCommand(profile.workspaceRoot, argv))
                channel.setInputStream(null)
                channel.connect(10_000)
                val stdout = channel.inputStream.bufferedReader().use { it.readText() }.take(MAX_OUTPUT)
                val stderr = channel.errStream.bufferedReader().use { it.readText() }.take(MAX_OUTPUT)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                    Thread.sleep(40)
                }
                val exit = channel.exitStatus
                channel.disconnect()
                SshExecResult.Ok(exitCode = exit, stdout = stdout, stderr = stderr)
            } finally {
                session.disconnect()
            }
        } catch (e: Exception) {
            SshExecResult.Error("JSch SSH failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        /** POSIX single-quote; keeps spaces inside bash -lc scripts intact. */
        fun shellSingleQuote(raw: String): String =
            "'" + raw.replace("'", "'\"'\"'") + "'"

        /**
         * Turn argv into a shell script body.
         * If already `bash|sh -lc|-c <script>`, use the script alone (do not re-join).
         */
        fun remoteScriptFromArgv(argv: List<String>): String {
            if (argv.isEmpty()) return "true"
            if (argv.size >= 3) {
                val a0 = argv[0].substringAfterLast('/')
                val a1 = argv[1]
                if ((a0 == "bash" || a0 == "sh") && (a1 == "-lc" || a1 == "-c")) {
                    return argv[2]
                }
            }
            return argv.joinToString(" ") { shellSingleQuote(it) }
        }

        /** cd home/workspace then the script body (ProcessBuilder bash -lc arg). */
        fun buildFullRemoteScript(workspaceRoot: String?, argv: List<String>): String {
            val script = remoteScriptFromArgv(argv)
            val landing = workspaceRoot?.trim()?.takeIf { it.isNotEmpty() }
            val cd = if (landing != null) {
                "cd " + shellSingleQuote(landing) + " 2>/dev/null || cd \"\$HOME\" 2>/dev/null || true; "
            } else {
                "cd \"\$HOME\" 2>/dev/null || true; "
            }
            return cd + script
        }

        /** ChannelExec: cd home/workspace then bash -lc '<script>'. */
        fun buildRemoteExecCommand(workspaceRoot: String?, argv: List<String>): String {
            val script = remoteScriptFromArgv(argv)
            val landing = workspaceRoot?.trim()?.takeIf { it.isNotEmpty() }
            val cd = if (landing != null) {
                "cd " + shellSingleQuote(landing) + " 2>/dev/null || cd \"\$HOME\" 2>/dev/null || true; "
            } else {
                "cd \"\$HOME\" 2>/dev/null || true; "
            }
            return cd + "bash -lc " + shellSingleQuote(script)
        }

        private const val MAX_OUTPUT = 8_192

        private val SSH_CANDIDATES = listOf(
            "/data/data/com.termux/files/usr/bin/ssh",
            "/data/data/com.termux/files/usr/local/bin/ssh",
            "/system/bin/ssh",
            "/system/xbin/ssh",
            "/vendor/bin/ssh",
            "ssh",
        )

        private val SSHPASS_CANDIDATES = listOf(
            "/data/data/com.termux/files/usr/bin/sshpass",
            "/data/data/com.termux/files/usr/local/bin/sshpass",
            "/system/bin/sshpass",
            "/system/xbin/sshpass",
            "/vendor/bin/sshpass",
            "sshpass",
        )

        private val KEYSCAN_CANDIDATES = listOf(
            "/data/data/com.termux/files/usr/bin/ssh-keyscan",
            "/data/data/com.termux/files/usr/local/bin/ssh-keyscan",
            "/system/bin/ssh-keyscan",
            "/system/xbin/ssh-keyscan",
            "ssh-keyscan",
        )

        private val KEYGEN_CANDIDATES = listOf(
            "/data/data/com.termux/files/usr/bin/ssh-keygen",
            "/data/data/com.termux/files/usr/local/bin/ssh-keygen",
            "/system/bin/ssh-keygen",
            "/system/xbin/ssh-keygen",
            "ssh-keygen",
        )

        fun findSshBinary(): String? = firstExisting(SSH_CANDIDATES)

        fun findSshpassBinary(): String? = firstExisting(SSHPASS_CANDIDATES)

        fun findKeyscanBinary(): String? = firstExisting(KEYSCAN_CANDIDATES)

        fun findKeygenBinary(): String? = firstExisting(KEYGEN_CANDIDATES)

        fun isUsableBinary(path: String): Boolean {
            if (!path.contains('/')) return true
            val f = File(path)
            return f.canExecute()
        }

        fun normalizeFingerprint(raw: String): String {
            val t = raw.trim()
            val sha = Regex("""SHA256:[A-Za-z0-9+/=]+""").find(t)?.value
            if (sha != null) return sha
            val md5 = Regex("""(?i)(?:MD5:)?([0-9a-f]{2}(?::[0-9a-f]{2}){15})""").find(t)?.groupValues?.get(1)
            if (md5 != null) return "MD5:$md5"
            return t
        }

        fun probeHostKeyFingerprint(host: String, port: Int): String? {
            val keyscan = findKeyscanBinary() ?: return null
            val keygen = findKeygenBinary() ?: return null
            if (!isUsableBinary(keyscan) || !isUsableBinary(keygen)) return null
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
            if (!isUsableBinary(keyscan)) return null
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
                    val f = File(c)
                    if (f.canExecute()) return c
                    // Surface Termux paths even when not executable from this UID
                    // so Capability / Unsupported can recommend RUN_COMMAND.
                    if (f.exists() && c.contains("com.termux")) return c
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
