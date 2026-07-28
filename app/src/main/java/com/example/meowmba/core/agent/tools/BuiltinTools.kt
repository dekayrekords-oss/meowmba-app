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
        description = "Read a text file from the workspace.",
        inputSchema = mapOf("path" to "path"),
        riskLevel = RiskLevel.SAFE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult.Failure("Missing 'path'")
        val content = workspace.read(path)
        return if (content != null) {
            ToolResult.Success(content.take(4000))
        } else {
            ToolResult.Failure("Cannot read '$path' (missing, unreadable, or outside the workspace)")
        }
    }
}

class FileWriteTool(private val workspace: Workspace) : Tool {
    override val definition = ToolDefinition(
        id = "file.write",
        displayName = "Write file",
        description = "Create or overwrite a text file in the workspace.",
        inputSchema = mapOf("path" to "path", "content" to "string"),
        riskLevel = RiskLevel.MODERATE,
        confirmationPolicy = ConfirmationPolicy.ALWAYS
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult.Failure("Missing 'path'")
        val content = args["content"] ?: ""
        return if (workspace.write(path, content)) {
            ToolResult.Success("Wrote $path (${content.length} chars)")
        } else {
            ToolResult.Failure("Cannot write '$path' (outside the workspace)")
        }
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
        description = "List everything in the workspace.",
        inputSchema = emptyMap(),
        riskLevel = RiskLevel.SAFE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val entries = workspace.list()
        return ToolResult.Success(
            if (entries.isEmpty()) "Workspace is empty."
            else entries.take(50).joinToString("\n")
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
