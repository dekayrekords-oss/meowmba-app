package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.files.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Agent file operations stay inside the same library, with normal orchestrator approvals. */
class AssetTool(private val library: AssetLibrary, private val operation: String, private val openBrowser: () -> Unit = {}) : Tool {
    override val definition = ToolDefinition(
        id = "file.$operation",
        displayName = when (operation) { "browser" -> "Open Files"; "search" -> "Search assets"; "cleanup" -> "Analyze library cleanup"; "trash" -> "Move asset to Trash"; "restore" -> "Restore asset"; else -> "Move / rename asset" },
        description = when (operation) {
            "browser" -> "Open the Files tile: projects, photos, videos, imports, AI search and reviewable cleanup."
            "search" -> "Search actual library filenames, relative paths and categories. Returns real file paths for subsequent tools. Not image-content recognition."
            "cleanup" -> "Analyze duplicate, empty and large workspace files. Suggestions only; never deletes anything."
            "trash" -> "Move one library file into recoverable Trash after approval. Does not free space until manually purged in Files."
            "restore" -> "Restore an exact Trash ID to its original location; never overwrite an existing file."
            else -> "Move or rename one existing library file after approval. Paths are relative, destination must not exist."
        },
        inputSchema = when (operation) { "search" -> mapOf("query" to "string"); "move" -> mapOf("path" to "path", "destination" to "path"); "trash" -> mapOf("path" to "path"); "restore" -> mapOf("id" to "string"); else -> emptyMap() },
        riskLevel = if (operation in setOf("move", "trash", "restore")) RiskLevel.MODERATE else RiskLevel.SAFE,
        confirmationPolicy = if (operation in setOf("move", "trash", "restore")) ConfirmationPolicy.ALWAYS else ConfirmationPolicy.NONE,
        timeoutMs = if (operation == "cleanup") 120_000 else 15_000
    )
    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val result = when (operation) {
                "browser" -> { withContext(Dispatchers.Main) { openBrowser() }; "Files library opened." }
                "search" -> {
                    val scan = library.search(args["query"]?.takeIf { it.isNotBlank() } ?: error("Missing query"))
                    buildJsonObject {
                        put("truncated", scan.truncated || scan.entries.size > 50)
                        put("files", JsonArray(scan.entries.take(50).map { buildJsonObject { put("path", it.path); put("type", it.category); put("bytes", it.bytes) } }))
                    }.toString()
                }
                "cleanup" -> { val (items, limited) = library.cleanup(); "Review only; no files changed. Partial scan: $limited\n" + items.take(50).joinToString("\n") { "${it.asset.path}: ${it.reason}" } }
                "move" -> { val item = library.entry(library.resolve(args["path"] ?: error("Missing path"))); library.move(AssetMove(item, args["destination"] ?: error("Missing destination"))); "Moved ${item.path} to ${args["destination"]}." }
                "trash" -> { val item = library.entry(library.resolve(args["path"] ?: error("Missing path"))); "Moved ${item.path} to Trash. Restore ID: ${library.trash(item)}" }
                "restore" -> { library.restore(args["id"] ?: error("Missing Trash ID")); "Restored file to its original location." }
                else -> error("Unsupported asset operation")
            }
            ToolResult.Success(result)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { ToolResult.Failure(e.message ?: "Asset operation failed") }
    }
}
