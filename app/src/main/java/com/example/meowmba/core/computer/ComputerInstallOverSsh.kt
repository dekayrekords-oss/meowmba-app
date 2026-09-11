package com.example.meowmba.core.computer

import com.example.meowmba.core.agentic.ConnectionProfile
import com.example.meowmba.core.agentic.ConnectionProfileType
import com.example.meowmba.core.agentic.CredentialResolver
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Uploads [install.sh] via SFTP (or stdin cat) and runs `bash` on the remote.
 * Streams combined stdout/stderr through [onLogLine]. Never logs secrets.
 */
class ComputerInstallOverSsh(
    private val credentials: CredentialResolver,
) {
    fun testConnection(profile: ConnectionProfile): Result<String> {
        if (profile.type != ConnectionProfileType.SSH) {
            return Result.failure(IllegalArgumentException("Test connection needs an SSH profile"))
        }
        return runCatching {
            withSession(profile) { session ->
                val out = exec(session, "echo ok; uname -srm; id -un; pwd", timeoutMs = 20_000L)
                if (!out.ok) error(out.stderr.ifBlank { "SSH test failed (exit ${out.exitCode})" })
                "Connected to ${profile.username}@${profile.host}. ${out.stdout.trim().take(200)}"
            }
        }
    }

    /**
     * @param installScriptUtf8 contents of repo `computer/install.sh`
     */
    fun install(
        profile: ConnectionProfile,
        installScriptUtf8: String,
        onLogLine: (String) -> Unit,
        timeoutMs: Long = 600_000L,
    ): Result<String> {
        if (profile.type != ConnectionProfileType.SSH) {
            return Result.failure(IllegalArgumentException("Install Computer needs an SSH profile"))
        }
        if (installScriptUtf8.isBlank()) {
            return Result.failure(IllegalArgumentException("install.sh is empty"))
        }
        return runCatching {
            withSession(profile) { session ->
                onLogLine("Uploading install.sh…")
                val remotePath = "/tmp/meow-computer-install.sh"
                try {
                    val sftp = session.openChannel("sftp") as ChannelSftp
                    sftp.connect(15_000)
                    try {
                        sftp.put(
                            ByteArrayInputStream(installScriptUtf8.toByteArray(StandardCharsets.UTF_8)),
                            remotePath,
                        )
                    } finally {
                        sftp.disconnect()
                    }
                    onLogLine("Uploaded. Running bash (idempotent)…")
                } catch (e: Exception) {
                    onLogLine("SFTP unavailable (${e.javaClass.simpleName}); piping via cat…")
                    val b64 = android.util.Base64.encodeToString(
                        installScriptUtf8.toByteArray(StandardCharsets.UTF_8),
                        android.util.Base64.NO_WRAP,
                    )
                    val pipe = exec(
                        session,
                        "echo '$b64' | base64 -d > $remotePath && chmod +x $remotePath",
                        timeoutMs = 60_000L,
                        onChunk = onLogLine,
                    )
                    if (!pipe.ok) error("Could not stage install.sh: ${pipe.stderr.take(240)}")
                }
                val run = exec(
                    session,
                    "bash $remotePath; ec=\$?; rm -f $remotePath; exit \$ec",
                    timeoutMs = timeoutMs,
                    onChunk = onLogLine,
                )
                if (!run.ok) {
                    error("Install finished with exit ${run.exitCode}. ${run.stderr.take(300)}")
                }
                "Install finished successfully."
            }
        }
    }

    fun setRemotePower(profile: ConnectionProfile, on: Boolean): Result<String> {
        val cmd = if (on) {
            "systemctl --user start meow-computer.service 2>/dev/null || " +
                "(cd \"\${MEOW_COMPUTER_HOME:-\$HOME/meow-computer}\" && " +
                "nohup python3 bin/meow-computer-worker.py >/dev/null 2>&1 & echo started)"
        } else {
            "systemctl --user stop meow-computer.service 2>/dev/null || " +
                "(kill \"\$(cat \"\${MEOW_COMPUTER_HOME:-\$HOME/meow-computer}/logs/worker.pid\" 2>/dev/null)\" 2>/dev/null; echo stopped)"
        }
        return runCatching {
            withSession(profile) { session ->
                val out = exec(session, cmd, timeoutMs = 20_000L)
                out.stdout.ifBlank { if (on) "Power On requested" else "Power Off requested" }
            }
        }
    }

    fun discoverVncUrl(profile: ConnectionProfile): String? {
        return runCatching {
            withSession(profile) { session ->
                val out = exec(
                    session,
                    "test -f \"\${MEOW_COMPUTER_HOME:-\$HOME/meow-computer}/COMPUTER_READY.json\" && " +
                        "grep -Eo 'https?://[^\" ]+:[0-9]+' " +
                        "\"\${MEOW_COMPUTER_HOME:-\$HOME/meow-computer}/\"*.json 2>/dev/null | head -1; " +
                        "ss -ltn 2>/dev/null | grep -E ':6080|:5901' >/dev/null && " +
                        "echo \"http://${profile.host}:6080/vnc.html\" || true",
                    timeoutMs = 15_000L,
                )
                out.stdout.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("http") }
            }
        }.getOrNull()
    }

    private fun <T> withSession(profile: ConnectionProfile, block: (Session) -> T): T {
        val secret = credentials.resolve(profile.credentialRef)
            ?: error(
                "No credential for ref \"${profile.credentialRef}\". " +
                    "Save a password or key material under that ref first.",
            )
        val jsch = JSch()
        val session = jsch.getSession(profile.username, profile.host, profile.port.coerceAtLeast(1))
        session.setPassword(secret)
        session.setConfig("StrictHostKeyChecking", "no")
        session.connect(20_000)
        try {
            return block(session)
        } finally {
            session.disconnect()
        }
    }

    private data class ExecOut(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = exitCode == 0
    }

    private fun exec(
        session: Session,
        command: String,
        timeoutMs: Long,
        onChunk: ((String) -> Unit)? = null,
    ): ExecOut {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        channel.setErrStream(null)
        val input = channel.inputStream
        val err = channel.errStream
        channel.connect(15_000)
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val buf = ByteArray(4096)
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(1_000L)
        try {
            while (true) {
                while (input.available() > 0) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    val chunk = String(buf, 0, n, StandardCharsets.UTF_8)
                    stdout.append(chunk)
                    onChunk?.invoke(chunk)
                }
                while (err.available() > 0) {
                    val n = err.read(buf)
                    if (n <= 0) break
                    val chunk = String(buf, 0, n, StandardCharsets.UTF_8)
                    stderr.append(chunk)
                    onChunk?.invoke(chunk)
                }
                if (channel.isClosed) {
                    while (input.available() > 0) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        val chunk = String(buf, 0, n, StandardCharsets.UTF_8)
                        stdout.append(chunk)
                        onChunk?.invoke(chunk)
                    }
                    break
                }
                if (System.currentTimeMillis() > deadline) {
                    channel.disconnect()
                    error("remote command timed out")
                }
                Thread.sleep(80)
            }
        } finally {
            channel.disconnect()
        }
        return ExecOut(channel.exitStatus, stdout.toString().take(400_000), stderr.toString().take(80_000))
    }
}
