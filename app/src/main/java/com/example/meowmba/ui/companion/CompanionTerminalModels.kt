package com.example.meowmba.ui.companion

import com.example.meowmba.core.agentic.ConnectionProfile
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.ssh.SshHostRegistry
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import java.io.File
import java.util.concurrent.TimeUnit

/** Dropdown entries for the companion Terminal tab. */
sealed class CompanionTerminalTarget {
    abstract val id: String
    abstract val label: String

    data object Local : CompanionTerminalTarget() {
        override val id: String = LOCAL_ID
        override val label: String = "Local shell"
    }

    data class AgenticProfile(val profile: ConnectionProfile) : CompanionTerminalTarget() {
        override val id: String = profile.id
        override val label: String = "${profile.displayName} (${profile.username}@${profile.host})"
    }

    data class LegacyHost(val name: String) : CompanionTerminalTarget() {
        override val id: String = LEGACY_PREFIX + name
        override val label: String = "SSH · $name"
    }

    companion object {
        const val LOCAL_ID = "local"
        const val LEGACY_PREFIX = "legacy:"
    }
}

/** Local / Termux / legacy-host one-shot helpers (agentic-style). Prefer [CompanionTerminalSession] for the UI tab. */
internal object CompanionTerminalLocalShell {
    private const val MAX_OUTPUT = 64_000

    fun exec(command: String, timeoutMs: Long, storage: SecureStorageManager? = null): String {
        val shell = findLocalShell()
            ?: return "error: no local shell found (/system/bin/sh or Termux)"
        return try {
            val pb = ProcessBuilder(shell, "-c", command)
            pb.redirectErrorStream(true) // show stderr in the captured stream
            val cwd = if (storage != null) {
                CompanionTerminalSession.resolveLocalCwd(storage, shell)
            } else {
                fallbackCwd(shell)
            }
            if (cwd != null) pb.directory(cwd)
            val env = pb.environment()
            env["PATH"] = CompanionTerminalSession.mergePath(env["PATH"])
            if (cwd != null) env["HOME"] = cwd.absolutePath
            val proc = pb.start()
            val finished = proc.waitFor(timeoutMs.coerceAtLeast(1_000L), TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return "error: local shell timed out after ${timeoutMs}ms"
            }
            val out = proc.inputStream.bufferedReader().use { it.readText() }.take(MAX_OUTPUT)
            if (out.isNotEmpty()) out else "(exit ${proc.exitValue()})"
        } catch (e: Exception) {
            "error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun execViaLegacyHost(
        storage: SecureStorageManager,
        hostName: String,
        command: String,
        timeoutMs: Long,
    ): String {
        val server = SshHostRegistry(storage).get(hostName)
            ?: return "error: unknown saved server \"$hostName\""
        return try {
            val jsch = JSch()
            val session = jsch.getSession(server.user, server.host, server.port)
            session.setPassword(server.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.timeout = 10_000
            session.connect(10_000)
            try {
                val channel = session.openChannel("exec") as ChannelExec
                // Land in home; do not smash a multi-word command — caller passes one shell string.
                channel.setCommand("cd \"\$HOME\" 2>/dev/null; $command")
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
                buildString {
                    if (stdout.isNotEmpty()) append(stdout)
                    if (stderr.isNotEmpty()) {
                        if (isNotEmpty()) append('\n')
                        append(stderr)
                    }
                    if (isEmpty()) append("(exit $exit)")
                    else if (exit != 0) append("\n[exit $exit]")
                }
            } finally {
                session.disconnect()
            }
        } catch (e: Exception) {
            "error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun findLocalShell(): String? {
        val candidates = listOf(
            "/data/data/com.termux/files/usr/bin/bash",
            "/data/data/com.termux/files/usr/bin/sh",
            "/system/bin/sh",
            "/bin/sh",
        )
        return candidates.firstOrNull { path ->
            val f = File(path)
            f.exists() && f.canExecute()
        }
    }

    private fun fallbackCwd(shellPath: String): File? {
        if (shellPath.contains("com.termux")) {
            val home = File("/data/data/com.termux/files/home")
            if (home.isDirectory) return home
        }
        val home = System.getenv("HOME")?.let { File(it) }
        if (home != null && home.isDirectory && home.canRead()) return home
        return null
    }
}
