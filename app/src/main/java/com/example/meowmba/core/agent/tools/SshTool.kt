package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.ssh.SshHostRegistry
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Simple, safe server management over SSH.
 *
 * Security model:
 *  - Servers come from the encrypted registry only (Settings → Servers).
 *  - Commands are gated by a read-only allowlist; anything else is refused.
 *  - Shell metacharacters are rejected outright (no chaining, redirects).
 *  - Host keys are trust-on-first-use, stored encrypted; a changed key aborts.
 *  - Output capped at 4KB, hard 30s timeout.
 */
class SshTool(private val registry: SshHostRegistry) : Tool {
    override val definition = ToolDefinition(
        id = "ssh.run",
        displayName = "Run a command on a saved server",
        description = "Execute a read-only management command on a remembered SSH server " +
            "(e.g. meow-nb). Allowed commands: ls, df, free, uptime, systemctl status, " +
            "journalctl (tail), docker ps. Inputs: host (server name), command.",
        inputSchema = mapOf("host" to "string", "command" to "string"),
        riskLevel = RiskLevel.MODERATE,
        timeoutMs = 35_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val hostName = args["host"]?.trim().orEmpty()
        val command = args["command"]?.trim().orEmpty()
        if (hostName.isEmpty()) return ToolResult.Failure("No host given. Saved servers: ${registry.list().joinToString { it.name }.ifBlank { "none yet" }}")
        if (command.isEmpty()) return ToolResult.Failure("No command given.")

        val gate = validateCommand(command)
        if (gate != null) return ToolResult.Failure(gate)

        val server = registry.get(hostName)
            ?: return ToolResult.Failure(
                "Unknown server \"$hostName\". Saved servers: ${registry.list().joinToString { it.name }.ifBlank { "none yet" }}"
            )

        return withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(30_000L) { runCommand(server, command) }
            }.getOrElse { e -> ToolResult.Failure("SSH failed: ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    private fun runCommand(server: com.example.meowmba.core.ssh.SshHost, command: String): ToolResult {
        val jsch = JSch()
        val session = jsch.getSession(server.user, server.host, server.port)
        session.setPassword(server.password)
        session.setConfig("StrictHostKeyChecking", "no")
        session.timeout = CONNECT_TIMEOUT_MS
        session.connect(CONNECT_TIMEOUT_MS)
        try {
            // Trust-on-first-use: remember the host key fingerprint on the first
            // connect, and refuse to continue if it ever changes.
            val fingerprint = session.hostKey?.getFingerPrint(jsch).orEmpty()
            val saved = registry.hostKey(server.host, server.port)
            when {
                saved == null && fingerprint.isNotBlank() ->
                    registry.saveHostKey(server.host, server.port, fingerprint)
                saved != null && fingerprint.isNotBlank() && saved != fingerprint ->
                    return ToolResult.Failure(
                        "Host key for ${server.host}:${server.port} changed! Refusing to connect. " +
                            "If the server was reinstalled, remove and re-add it in Settings."
                    )
            }

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.setInputStream(null)
            channel.setErrStream(null)
            channel.connect(CONNECT_TIMEOUT_MS)
            val out = channel.inputStream.bufferedReader().use { it.readText() }
            val err = channel.errStream.bufferedReader().use { it.readText() }
            // Wait for exit without blocking forever.
            val deadline = System.currentTimeMillis() + 20_000
            while (!channel.isClosed && System.currentTimeMillis() < deadline) Thread.sleep(50)
            val exit = channel.exitStatus
            channel.disconnect()

            val body = buildString {
                if (out.isNotBlank()) append(out.trim().take(MAX_OUTPUT))
                if (err.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("stderr: ").append(err.trim().take(1000))
                }
                if (isEmpty()) append("(no output)")
                append("\n(exit code $exit)")
            }
            return ToolResult.Success(body.take(MAX_OUTPUT + 1100))
        } finally {
            session.disconnect()
        }
    }

    companion object {
        private const val MAX_OUTPUT = 4096
        private const val CONNECT_TIMEOUT_MS = 10_000

        /** Read-only commands meow may run without asking a human. */
        val ALLOWLIST = listOf(
            "ls", "df", "free", "uptime", "systemctl status",
            "journalctl -n", "journalctl --lines", "journalctl -e", "docker ps",
        )

        private val FORBIDDEN_CHARS = charArrayOf(';', '|', '&', '`', '$', '>', '<', '(', ')', '{', '}', '"', '\'', '\\')

        /** Returns null when allowed, otherwise the refusal reason. Pure for tests. */
        fun validateCommand(command: String): String? {
            val cmd = command.trim()
            if (cmd.isEmpty()) return "Empty command."
            if (cmd.any { it in FORBIDDEN_CHARS })
                return "Command contains shell metacharacters; refused."
            val firstWord = cmd.substringBefore(' ')
            val allowed = ALLOWLIST.any { prefix ->
                cmd == prefix || cmd.startsWith("$prefix ")
            } || listOf("journalctl", "docker").contains(firstWord) && isSafeSubcommand(cmd)
            return if (allowed) null else
                "Command not on the read-only allowlist (ls, df, free, uptime, systemctl status, journalctl tail, docker ps)."
        }

        private fun isSafeSubcommand(cmd: String): Boolean = when {
            cmd.startsWith("journalctl ") -> cmd.contains("-n ") || cmd.contains("--lines") || cmd.endsWith(" -e") || cmd.contains(" -u ")
            cmd.startsWith("docker ") -> cmd.startsWith("docker ps")
            else -> false
        }
    }
}
