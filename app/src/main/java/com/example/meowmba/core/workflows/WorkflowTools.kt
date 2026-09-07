package com.example.meowmba.core.workflows

import com.example.meowmba.core.agent.tools.*
import java.io.File
import java.security.MessageDigest

/** Business files are the same assets visible through Meow's Files page. */
object WorkflowTools {
    fun local(root: File): ToolRegistry {
        val workspace = Workspace(root)
        return ToolRegistry().apply {
            register(action("files.list", "List business files", mapOf("path" to "string")) { args ->
                val dir = if (args["path"].isNullOrBlank()) root else workspace.resolve(args.getValue("path"))
                require(dir?.isDirectory == true) { "Folder not found" }
                val entries = dir!!.walkTopDown().onEnter { it.canonicalPath.startsWith(root.canonicalPath + File.separator) || it.canonicalFile == root.canonicalFile }
                    .maxDepth(6).filter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }.take(201).toList()
                entries.take(200).joinToString("\n") { it.relativeTo(root).path }.take(11_000) +
                    "\nListing limited to 200 entries, depth 6 and 11,000 characters. Use a subfolder to narrow it."
            })
            register(action("files.read", "Read a business text file page (offset is characters, default 0)", mapOf("path" to "string", "offset" to "integer")) { args ->
                val file = workspace.resolve(args["path"].orEmpty())
                require(file?.isFile == true) { "File not found: ${args["path"]}. Use the exact path from the job or files.list." }
                require(file!!.length() <= 100_000) { "File is too large; split it into smaller inputs" }
                val text = file.readText()
                val offset = args["offset"]?.toIntOrNull() ?: 0
                require(offset in 0..text.length) { "Offset outside file" }
                val end = minOf(offset + 10_000, text.length)
                "File: ${args["path"]}; characters $offset..$end of ${text.length}; " +
                    (if (end < text.length) "more: call files.read with offset=$end" else "end of file") + "\n" + text.substring(offset, end)
            })
            register(action("files.write", "Write a business report or draft and verify it", mapOf("path" to "string", "content" to "string"), true) { args ->
                val file = workspace.resolve(args["path"].orEmpty()) ?: error("Path leaves the workspace")
                val content = args["content"] ?: error("Missing content")
                require(content.length <= 100_000) { "Content too large" }
                file.parentFile?.mkdirs()
                val bytes = content.toByteArray()
                val pending = java.nio.file.Files.createTempFile(file.parentFile.toPath(), ".agent-", ".pending")
                try {
                    java.io.FileOutputStream(pending.toFile()).use { out -> out.write(bytes); out.fd.sync() }
                    java.nio.file.Files.move(pending, file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                } finally { java.nio.file.Files.deleteIfExists(pending) }
                check(file.readBytes().contentEquals(bytes)) { "Written contents did not match" }
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                "Verified file: ${file.relativeTo(root).path}\nBytes: ${bytes.size}\nSHA-256: $digest"
            })
            register(action("files.csv_summary", "Calculate exact decimal totals from a CSV. Read the CSV to learn its columns and values. amount_column is required. Optional filter_column/filter_value selects equal rows, case-insensitive by default; set case_sensitive=true for case-sensitive values. Column names accept unambiguous case aliases only; output states the actual column and matching mode.",
                mapOf("path" to "string", "amount_column" to "string", "filter_column" to "string", "filter_value" to "string", "case_sensitive" to "boolean")) { args ->
                val file = workspace.resolve(args["path"].orEmpty())
                require(file?.isFile == true) { "CSV not found: ${args["path"]}. Use the exact path in the original job or files.list." }
                require(file!!.length() <= 100_000) { "CSV is over 100KB; split the input" }
                val mode = args["case_sensitive"].orEmpty()
                require(mode in setOf("", "true", "false")) { "case_sensitive must be true or false" }
                CsvSummary.summarize(file.readText(), args["amount_column"].orEmpty(), args["filter_column"], args["filter_value"], mode == "true")
            })
        }
    }
    private fun action(id: String, label: String, args: Map<String, String>, writes: Boolean = false, run: (Map<String, String>) -> String) = object : Tool {
        override val definition = ToolDefinition(id, label, label, args, riskLevel = if (writes) RiskLevel.MODERATE else RiskLevel.SAFE,
            confirmationPolicy = if (writes) ConfirmationPolicy.ALWAYS else ConfirmationPolicy.NONE)
        override suspend fun execute(args: Map<String, String>): ToolResult = try { ToolResult.Success(run(args)) }
        catch (e: Exception) { ToolResult.Failure(e.message ?: "File operation failed") }
    }
}
