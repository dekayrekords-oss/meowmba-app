package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.vault.VaultRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Obsidian vault tools: the LLM can read, write, search, and manage Markdown
 * notes stored in the user's on-device vault.
 *
 * All five tools are SAFE (read-only or reversible writes), so they run
 * without confirmation and are registered unconditionally when a vault exists.
 */

private val TS = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

// ── vault.note.list ─────────────────────────────────────────────────────────

class VaultListTool(private val vault: VaultRepository) : Tool {
    override val definition = ToolDefinition(
        id = "vault.note.list",
        displayName = "List vault notes",
        description = "List all Markdown notes in the Obsidian vault. Returns titles, tags, " +
            "and a one-line preview of each note, newest first. Use this to discover what " +
            "notes exist before reading or searching.",
        inputSchema = emptyMap(),
        riskLevel = RiskLevel.SAFE,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val notes = vault.list()
        if (notes.isEmpty()) return ToolResult.Success("Vault is empty — no notes yet.")
        val lines = notes.map { n ->
            buildString {
                append("**${n.title}**")
                if (n.tags.isNotEmpty()) append(" [${n.tags.joinToString(", ")}]")
                append(" — ${n.preview.ifBlank { "(no preview)" }}")
                append(" (${TS.format(Date(n.modifiedAt))})")
            }
        }
        return ToolResult.Success("${notes.size} note(s):\n" + lines.joinToString("\n"))
    }
}

// ── vault.note.read ─────────────────────────────────────────────────────────

class VaultReadTool(private val vault: VaultRepository) : Tool {
    override val definition = ToolDefinition(
        id = "vault.note.read",
        displayName = "Read a vault note",
        description = "Read the full Markdown content of one note by title. " +
            "Use vault.note.list first if you're not sure of the exact title.",
        inputSchema = mapOf("title" to "string"),
        riskLevel = RiskLevel.SAFE,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val title = args["title"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'title'.")
        val note = vault.get(title)
            ?: return ToolResult.Failure("No note titled \"$title\". Use vault.note.list to see what exists.")
        return ToolResult.Success(
            "# ${note.title}\n" +
            "Tags: ${note.tags.joinToString(", ").ifBlank { "(none)" }}\n" +
            "Created: ${TS.format(Date(note.createdAt))} | Modified: ${TS.format(Date(note.modifiedAt))}\n\n" +
            note.body
        )
    }
}

// ── vault.note.write ────────────────────────────────────────────────────────

class VaultWriteTool(private val vault: VaultRepository) : Tool {
    override val definition = ToolDefinition(
        id = "vault.note.write",
        displayName = "Write a vault note",
        description = "Create or overwrite a Markdown note in the vault. Use standard Markdown " +
            "syntax: # headings, **bold**, *italic*, [[wikilinks]] to other notes, " +
            "- bullet lists, > blockquotes. Set append=true to add text to an existing " +
            "note instead of replacing it. Tags are optional, comma-separated.",
        inputSchema = mapOf(
            "title" to "string",
            "body" to "string (Markdown content)",
            "tags" to "string (comma-separated, optional)",
            "append" to "string (\"true\" to append to existing note, optional)",
        ),
        riskLevel = RiskLevel.SAFE,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val title = args["title"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'title'.")
        val body = args["body"] ?: return ToolResult.Failure("Missing 'body'.")
        val tags = args["tags"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val doAppend = args["append"]?.trim()?.lowercase() == "true"

        val note = if (doAppend) vault.append(title, body)
                   else vault.save(title, body, tags)

        val action = if (doAppend) "appended to" else "saved"
        return ToolResult.Success(
            "Note \"${note.title}\" $action (${note.body.length} chars, ${note.tags.size} tag(s))."
        )
    }
}

// ── vault.note.delete ───────────────────────────────────────────────────────

class VaultDeleteTool(private val vault: VaultRepository) : Tool {
    override val definition = ToolDefinition(
        id = "vault.note.delete",
        displayName = "Delete a vault note",
        description = "Permanently delete one note by title.",
        inputSchema = mapOf("title" to "string"),
        riskLevel = RiskLevel.MODERATE,
        confirmationPolicy = ConfirmationPolicy.ALWAYS,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val title = args["title"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'title'.")
        return if (vault.delete(title)) ToolResult.Success("Deleted \"$title\".")
               else ToolResult.Failure("No note titled \"$title\" found.")
    }
}

// ── vault.note.search ───────────────────────────────────────────────────────

class VaultSearchTool(private val vault: VaultRepository) : Tool {
    override val definition = ToolDefinition(
        id = "vault.note.search",
        displayName = "Search vault notes",
        description = "Full-text search across all vault notes. Returns titles and matching " +
            "snippets. Title matches are ranked above body matches.",
        inputSchema = mapOf("query" to "string"),
        riskLevel = RiskLevel.SAFE,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val query = args["query"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'query'.")
        val results = vault.search(query)
        if (results.isEmpty()) return ToolResult.Success("No notes match \"$query\".")
        val lines = results.map { n ->
            val q = query.lowercase()
            val snippet = n.body.lines()
                .firstOrNull { q in it.lowercase() }
                ?.trim()?.take(100)
                ?: n.preview
            "**${n.title}** — …${snippet}…"
        }
        return ToolResult.Success("${results.size} result(s) for \"$query\":\n" + lines.joinToString("\n"))
    }
}

/** Register all five vault tools. */
fun ToolRegistry.registerVaultTools(vault: VaultRepository) {
    register(VaultListTool(vault))
    register(VaultReadTool(vault))
    register(VaultWriteTool(vault))
    register(VaultDeleteTool(vault))
    register(VaultSearchTool(vault))
}
