package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.database.ImportanceLevel
import com.example.meowmba.core.database.MemoryType
import com.example.meowmba.core.memory.MemoryRepository

/**
 * Built-in Phase 5 tools. Honest capabilities only:
 *  - memory.save / memory.search work against the real Room memory store
 *  - file.* tools work strictly inside the sandboxed [Workspace]
 *  - project.create scaffolds a folder with a manifest
 *  - task.list reports the orchestrator's own task state
 */

// ── Memory tools ────────────────────────────────────────────────────────────

class MemorySaveTool(private val memoryRepository: MemoryRepository) : Tool {
    override val definition = ToolDefinition(
        id = "memory.save",
        displayName = "Save to memory",
        description = "Store a fact in long-term memory.",
        inputSchema = mapOf("content" to "string"),
        riskLevel = RiskLevel.SAFE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val content = args["content"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'content'")
        return runCatching {
            memoryRepository.addMemory(
                content = content,
                type = MemoryType.SEMANTIC,
                importance = ImportanceLevel.MEDIUM,
                tags = listOf("agent")
            )
            ToolResult.Success("Saved to memory: ${content.take(60)}")
        }.getOrElse { ToolResult.Failure(it.message ?: "Save failed") }
    }
}

class MemorySearchTool(private val memoryRepository: MemoryRepository) : Tool {
    override val definition = ToolDefinition(
        id = "memory.search",
        displayName = "Search memory",
        description = "Full-text search across long-term memory.",
        inputSchema = mapOf("query" to "string"),
        riskLevel = RiskLevel.SAFE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val query = args["query"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'query'")
        return runCatching {
            val results = memoryRepository.searchMemories(query)
            if (results.isEmpty()) {
                ToolResult.Success("No memories match \"$query\".")
            } else {
                ToolResult.Success(
                    results.take(5).joinToString("\n") { "• ${it.content.take(100)}" }
                )
            }
        }.getOrElse { ToolResult.Failure(it.message ?: "Search failed") }
    }
}

// ── Workspace file tools ────────────────────────────────────────────────────

class FileReadTool(private val workspace: Workspace) : Tool {
    override val definition = ToolDefinition(
        id = "file.read",
        displayName = "Read file",
        description = "Read a text file. Relative names prefer the current project's files, then shared workspace references. A full projects/… path or leading / explicitly addresses the workspace root.",
        inputSchema = mapOf("path" to "path"),
        riskLevel = RiskLevel.SAFE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult.Failure("Missing 'path'")
        val file = workspace.resolveRead(path)?.takeIf { it.isFile }
            ?: return ToolResult.Failure("Cannot read '$path' (missing or outside the workspace)")
        if (com.example.meowmba.core.files.AssetLibrary.category(file.name) != "Documents")
            return ToolResult.Failure("This is a media or archive file, not text. Open it in Files with a compatible viewer.")
        // Imported videos/documents can be gigabytes. Never read the whole file just to return 4 KB.
        return runCatching {
            val buffer = CharArray(4001)
            val count = file.bufferedReader().use { reader ->
                var total = 0
                while (total < buffer.size) { val n = reader.read(buffer, total, buffer.size - total); if (n < 0) break; total += n }
                total
            }
            ToolResult.Success(String(buffer, 0, count.coerceAtMost(4000)) + if (count > 4000) "\n[Text preview truncated]" else "")
        }.getOrElse { ToolResult.Failure("Could not read '$path': ${it.message}") }
    }
}

class FileWriteTool(private val workspace: Workspace,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO) : Tool {
    override val definition = ToolDefinition(
        id = "file.write",
        displayName = "Write file",
        description = "Create or overwrite a text document in the workspace and show the saved result. Use .md, .txt or .html for documents. This tool does not encode PDF, Office, images or other binary formats; never label plain text with those extensions.",
        inputSchema = mapOf("path" to "path", "content" to "string"),
        riskLevel = RiskLevel.MODERATE,
        confirmationPolicy = ConfirmationPolicy.ALWAYS
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult.Failure("Missing 'path'")
        val content = args["content"] ?: ""
        if (java.io.File(path).extension.lowercase() in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "png", "jpg", "jpeg", "webp", "gif", "mp4", "webm", "mp3", "wav", "zip")) {
            return ToolResult.Failure("file.write creates text, not binary files. Use .md, .txt or .html for a readable document, or a real export/generation tool for this format. No file was written.")
        }
        return kotlinx.coroutines.withContext(ioDispatcher) { if (workspace.write(path, content)) {
            val file = workspace.resolveWrite(path) ?: return@withContext ToolResult.Failure("The saved file could not be located.", ToolOutcome.UNCONFIRMED)
            // Bounded readback avoids a second full document allocation on older phones.
            val verified = file.bufferedReader().use { reader ->
                val buffer = CharArray(8_192)
                var offset = 0
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (offset + count > content.length) return@use false
                    for (index in 0 until count) if (buffer[index] != content[offset + index]) return@use false
                    offset += count
                }
                offset == content.length
            }
            if (!verified) return@withContext ToolResult.Failure("The file write returned, but its contents could not be verified. Check the file before repeating it.", ToolOutcome.UNCONFIRMED)
            ToolResult.Success("Wrote ${file.relativeTo(workspace.root).path} (${content.length} chars)\nMEDIA_FILE:${file.absolutePath}", ToolOutcome.VERIFIED)
        } else {
            ToolResult.Failure("Cannot write '$path' (outside the workspace)")
        } }
    }
}

class FileDeleteTool(private val workspace: Workspace) : Tool {
    override val definition = ToolDefinition(
        id = "file.delete",
        displayName = "Delete file",
        description = "Delete a file or empty folder from the workspace. Irreversible.",
        inputSchema = mapOf("path" to "path"),
        riskLevel = RiskLevel.DESTRUCTIVE,
        confirmationPolicy = ConfirmationPolicy.ALWAYS
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult.Failure("Missing 'path'")
        return if (workspace.delete(path)) {
            ToolResult.Success("Deleted $path")
        } else {
            ToolResult.Failure("Cannot delete '$path' (missing, non-empty folder, or outside the workspace)")
        }
    }
}

class FileListTool(private val workspace: Workspace) : Tool {
    override val definition = ToolDefinition(
        id = "file.list",
        displayName = "List files",
        description = "List a bounded preview of this project's files and folders, or the whole library when no project is active, excluding Trash and linked paths. Returns workspace-relative paths for reading. Use file.search to find other library references.",
        inputSchema = emptyMap(),
        riskLevel = RiskLevel.SAFE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val scan = runCatching { workspace.scanFiles() }.getOrElse {
            return ToolResult.Failure("Cannot list files: ${it.message}")
        }
        val entries = scan.entries.map { it.path + if (it.folder) "/" else "" }.sorted()
        return ToolResult.Success(
            if (entries.isEmpty()) "Workspace is empty."
            else entries.take(50).joinToString("\n") + if (scan.truncated || entries.size > 50) "\n[Partial listing; use file.search or Files]" else ""
        )
    }
}

// ── Project tool ────────────────────────────────────────────────────────────

class ProjectCreateTool(private val workspace: Workspace) : Tool {
    override val definition = ToolDefinition(
        id = "project.create",
        displayName = "Create project",
        description = "Scaffold a project folder with a manifest and README.",
        inputSchema = mapOf("name" to "string"),
        riskLevel = RiskLevel.MODERATE,
        confirmationPolicy = ConfirmationPolicy.ALWAYS
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val name = args["name"]?.trim()?.replace(Regex("[^a-zA-Z0-9 _-]"), "")
            ?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing or invalid 'name'")
        val slug = name.lowercase().replace(' ', '-')
        val created = workspace.write(
            "projects/$slug/memba-project.json",
            "{\n  \"name\": \"$name\",\n  \"created\": ${System.currentTimeMillis()},\n  \"type\": \"project\"\n}\n"
        ) && workspace.write(
            "projects/$slug/README.md",
            "# $name\n\nCreated by Memba.\n"
        )
        return if (created) {
            ToolResult.Success("Project '$name' created at projects/$slug/")
        } else {
            ToolResult.Failure("Could not create project '$name'")
        }
    }
}

// ── Task introspection tool ─────────────────────────────────────────────────

class TaskListTool(private val tasksProvider: () -> List<String>) : Tool {
    override val definition = ToolDefinition(
        id = "task.list",
        displayName = "List agent tasks",
        description = "Show current and recent agent tasks.",
        inputSchema = emptyMap(),
        riskLevel = RiskLevel.SAFE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val lines = tasksProvider()
        return ToolResult.Success(
            if (lines.isEmpty()) "No tasks right now." else lines.joinToString("\n")
        )
    }
}
