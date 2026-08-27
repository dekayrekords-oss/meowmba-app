package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.ssh.SshHost
import com.example.meowmba.core.ssh.SshHostRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `ssh.add_host` — lets the agent save a new SSH server to the encrypted
 * registry so future `ssh.run` commands can target it.  Triggered by voice
 * commands like "SSH into 192.168.1.50" or "connect to my-server".
 *
 * MODERATE risk: shown on an approval card before saving.
 */
class SshAddHostTool(private val registry: SshHostRegistry) : Tool {
    override val definition = ToolDefinition(
        id = "ssh.add_host",
        displayName = "Add an SSH server",
        description = "Save a new SSH server so meow can run commands on it later. " +
            "Inputs: name (friendly label), host (IP or hostname), port (default 22), " +
            "user (default root), password (optional, blank = key-based).",
        inputSchema = mapOf(
            "name" to "string",
            "host" to "string",
            "port" to "string",
            "user" to "string",
            "password" to "string",
        ),
        riskLevel = RiskLevel.MODERATE,
        timeoutMs = 5_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val name = args["name"]?.trim().orEmpty()
        val host = args["host"]?.trim().orEmpty()
        if (name.isEmpty()) return@withContext ToolResult.Failure("A friendly name is required (e.g. \"my-server\").")
        if (host.isEmpty()) return@withContext ToolResult.Failure("A host (IP or hostname) is required.")
        if (name.length > 40) return@withContext ToolResult.Failure("Name too long (max 40 chars).")

        val port = args["port"]?.trim()?.toIntOrNull() ?: 22
        val user = args["user"]?.trim()?.ifBlank { "root" } ?: "root"
        val password = args["password"]?.trim() ?: ""

        val existing = registry.get(name)
        registry.save(SshHost(name = name, host = host, port = port, user = user, password = password))
        val verb = if (existing != null) "Updated" else "Added"
        ToolResult.Success(
            "$verb server \"$name\" → $user@$host:$port. " +
                "You can now say \"run ls on $name\" or \"check disk on $name\"."
        )
    }
}
