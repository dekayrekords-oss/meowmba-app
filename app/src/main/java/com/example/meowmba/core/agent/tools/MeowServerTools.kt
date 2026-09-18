package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.agentic.AgenticOutput
import com.example.meowmba.core.agentic.AgenticSshPolicy
import com.example.meowmba.core.computer.TermuxComputerLinks
import com.example.meowmba.core.ssh.SshHost
import com.example.meowmba.core.ssh.SshHostRegistry
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared JSch exec for meow-host tools. Never logs [SshHost.password].
 * Callers enforce allowlist / approval policy before invoking.
 */
internal object MeowHostSshExec {
    private const val MAX_OUTPUT = 8_000
    private const val CONNECT_TIMEOUT_MS = 12_000

    fun exec(server: SshHost, registry: SshHostRegistry, command: String, timeoutMs: Long): ToolResult {
        val jsch = JSch()
        val session = jsch.getSession(server.user, server.host, server.port)
        session.setPassword(server.password)
        session.setConfig("StrictHostKeyChecking", "no")
        session.timeout = CONNECT_TIMEOUT_MS
        session.connect(CONNECT_TIMEOUT_MS)
        try {
            val fingerprint = session.hostKey?.getFingerPrint(jsch).orEmpty()
            val saved = registry.hostKey(server.host, server.port)
            when {
                saved == null && fingerprint.isNotBlank() ->
                    registry.saveHostKey(server.host, server.port, fingerprint)
                saved != null && fingerprint.isNotBlank() && saved != fingerprint ->
                    return ToolResult.Failure(
                        "Host key for ${server.host}:${server.port} changed! Refusing to connect. " +
                            "If the server was reinstalled, remove and re-add it in Settings → Servers.",
                    )
            }

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.setInputStream(null)
            channel.setErrStream(null)
            channel.connect(CONNECT_TIMEOUT_MS)
            val out = channel.inputStream.bufferedReader().use { it.readText() }
            val err = channel.errStream.bufferedReader().use { it.readText() }
            val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(1_000L)
            while (!channel.isClosed && System.currentTimeMillis() < deadline) Thread.sleep(40)
            val exit = channel.exitStatus
            channel.disconnect()

            val body = buildString {
                if (out.isNotBlank()) append(AgenticOutput.scrub(out.trim(), MAX_OUTPUT))
                if (err.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("stderr: ").append(AgenticOutput.scrub(err.trim(), 1_500))
                }
                if (isEmpty()) append("(no output)")
                append("\n(exit code $exit)")
            }
            return ToolResult.Success(body.take(MAX_OUTPUT + 1_200))
        } finally {
            session.disconnect()
        }
    }

    fun resolveHost(
        registry: SshHostRegistry,
        requested: String?,
        defaultHost: String,
    ): Pair<SshHost, String>? {
        val preferred = requested?.trim().orEmpty().ifBlank { defaultHost.trim() }
        if (preferred.isNotBlank()) {
            registry.get(preferred)?.let { return it to preferred }
        }
        // Fallbacks: exact default, then common meow labels, then first saved.
        val candidates = listOf(defaultHost, "meow", "meow-nb", "meowmba")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        for (name in candidates) {
            registry.get(name)?.let { return it to name }
        }
        val first = registry.list().firstOrNull() ?: return null
        return first to first.name
    }

    fun shellSingleQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}

/**
 * `ssh.meow` — read-only management on the default meow SSH host
 * (Settings → Meta glasses bridge / Servers). Same allowlist as [SshTool].
 */
class SshMeowTool(
    private val registry: SshHostRegistry,
    private val defaultHost: () -> String,
) : Tool {
    private val inner = SshTool(registry)

    override val definition = ToolDefinition(
        id = "ssh.meow",
        displayName = "SSH meow (read-only)",
        description = "Run a read-only management command on the default meow SSH host " +
            "(or host=…). Allowed: ls, df, free, uptime, systemctl status, journalctl tail, docker ps. " +
            "For OpenCode use opencode.run; for broader shell use server.shell (needs approval).",
        inputSchema = mapOf("host" to "string", "command" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.SERVER),
        timeoutMs = 35_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val host = args["host"]?.trim().orEmpty().ifBlank { defaultHost() }
        return inner.execute(args + ("host" to host))
    }
}

/**
 * `opencode.run` — invoke the OpenCode CLI on the meow host over SSH.
 * Builds a safe remote command (single-quoted prompt). Always confirms.
 */
class OpenCodeRunTool(
    private val registry: SshHostRegistry,
    private val defaultHost: () -> String,
) : Tool {
    override val definition = ToolDefinition(
        id = "opencode.run",
        displayName = "Run OpenCode on meow",
        description = "SSH to the default meow server and run the OpenCode CLI " +
            "(PATH includes ~/.opencode/bin). Inputs: prompt (required), host (optional), " +
            "args (optional extra CLI flags). Install hint: " + TermuxComputerLinks.OPENCODE_ZEN_KEY_URL,
        inputSchema = mapOf(
            "prompt" to "string",
            "host" to "string",
            "args" to "string",
        ),
        riskLevel = RiskLevel.DESTRUCTIVE,
        environments = setOf(ToolEnvironment.SERVER),
        confirmationPolicy = ConfirmationPolicy.ALWAYS,
        timeoutMs = 180_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val prompt = args["prompt"]?.trim().orEmpty()
        if (prompt.isEmpty()) {
            return ToolResult.Failure("Missing prompt — say what OpenCode should do.")
        }
        if (prompt.length > 4_000) {
            return ToolResult.Failure("Prompt too long (max 4000 chars).")
        }
        val extra = args["args"]?.trim().orEmpty()
        if (extra.any { it in FORBIDDEN_META }) {
            return ToolResult.Failure("Extra args contain shell metacharacters; refused.")
        }
        val resolved = MeowHostSshExec.resolveHost(registry, args["host"], defaultHost())
            ?: return ToolResult.Failure(
                "No SSH server saved. Add meow in Settings → Servers " +
                    "(e.g. prog@100.77.97.236) or Connections, then retry.",
            )
        val (server, label) = resolved
        val remote = buildString {
            append("export PATH=\"\$HOME/.opencode/bin:\$PATH\"; ")
            append("if ! command -v opencode >/dev/null 2>&1; then ")
            append("echo \"OpenCode CLI not found on $label. Install with: curl -fsSL https://opencode.ai/install | bash\"; ")
            append("exit 127; fi; ")
            if (extra.isNotBlank()) {
                append("opencode ")
                append(extra)
                append(' ')
            } else {
                append("opencode run ")
            }
            append(MeowHostSshExec.shellSingleQuote(prompt))
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(170_000L) {
                    MeowHostSshExec.exec(server, registry, remote, 160_000L)
                }
            }.getOrElse { e ->
                ToolResult.Failure("OpenCode SSH failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        private val FORBIDDEN_META = charArrayOf(';', '|', '&', '`', '$', '>', '<', '(', ')', '\n', '\r')
    }
}

/**
 * `server.shell` — run a shell command on a saved SSH host (default meow).
 *
 * Safety matches Computer / desktop script UX: ConfirmationPolicy.ALWAYS so the
 * orchestrator shows an approval card before [execute]. Prefer ssh.meow for
 * allowlisted read-only checks and opencode.run for OpenCode.
 */
class ServerShellTool(
    private val registry: SshHostRegistry,
    private val defaultHost: () -> String,
) : Tool {
    override val definition = ToolDefinition(
        id = "server.shell",
        displayName = "Shell on meow / saved server",
        description = "Execute a shell command on the default meow SSH host (or host=…). " +
            "Always asks for confirmation before running (same class as desktop scripts). " +
            "Prefer ssh.meow for allowlisted read-only checks and opencode.run for OpenCode.",
        inputSchema = mapOf(
            "host" to "string",
            "command" to "string",
        ),
        riskLevel = RiskLevel.DESTRUCTIVE,
        environments = setOf(ToolEnvironment.SERVER),
        confirmationPolicy = ConfirmationPolicy.ALWAYS,
        timeoutMs = 90_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val command = args["command"]?.trim().orEmpty()
        if (command.isEmpty()) return ToolResult.Failure("No command given.")
        if (command.length > 8_000) return ToolResult.Failure("Command too long.")
        // Soft guidance only — orchestrator already confirmed DESTRUCTIVE tools.
        val argv = shellArgv(command)
        if (AgenticSshPolicy.isSideEffecting(argv)) {
            // Keep going; confirmation card already shown. Scrubbed output below.
        }

        val resolved = MeowHostSshExec.resolveHost(registry, args["host"], defaultHost())
            ?: return ToolResult.Failure(
                "No SSH server saved. Add one in Settings → Servers first " +
                    "(meow NetBird: 100.77.97.236 user prog).",
            )
        val (server, _) = resolved
        return withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(85_000L) {
                    MeowHostSshExec.exec(server, registry, command, 75_000L)
                }
            }.getOrElse { e ->
                ToolResult.Failure("server.shell failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        /** Split a simple command into argv for [AgenticSshPolicy]; keeps quoted chunks coarse. */
        fun shellArgv(command: String): List<String> {
            val parts = command.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return parts.ifEmpty { listOf(command.trim()) }
        }
    }
}

/** One-shot latch used by tests / debug to prove bridge tools registered. */
object MeowServerToolsProbe {
    val registered = AtomicBoolean(false)
}
