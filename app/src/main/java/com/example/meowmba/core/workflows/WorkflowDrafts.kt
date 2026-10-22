package com.example.meowmba.core.workflows

import com.example.meowmba.core.agent.tools.*
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.*

/** Durable, deduplicated review outbox. It deliberately has NO send function.
 * Event keys must include the service/account and its stable event identifier. */
object WorkflowDrafts {
    fun register(registry: ToolRegistry, root: File) {
        registry.register(tool("events.check", "Check whether a service event already has a saved reply draft", mapOf("source" to "service/account", "event_id" to "stable message or signup ID")) { args ->
            val file = destination(root, args)
            if (file.exists()) "Already drafted. Do not reply again. Review: drafts/${file.name}" else "No saved draft for this event. This does not prove whether any external reply was sent."
        })
        registry.register(tool("drafts.prepare", "Save a reply to the local review outbox without sending it. One draft per source/event; repeated calls never overwrite it.",
            mapOf("source" to "service/account", "event_id" to "stable event ID", "recipient" to "recipient", "subject" to "subject", "body" to "reply draft")) { args -> prepare(root, args) })
        registry.register(tool("drafts.list", "List up to 100 local review-outbox draft paths; use files.read to review contents", emptyMap()) {
            val folder = folder(root)
            folder.listFiles()?.filter { it.isFile && it.extension == "json" }?.sortedByDescending { it.lastModified() }?.take(100)
                ?.joinToString("\n") { "drafts/${it.name}" }?.ifBlank { "No drafts saved." } ?: "No drafts saved."
        })
    }
    private fun folder(root: File): File {
        root.mkdirs()
        val folder = File(root, "drafts")
        require(!Files.isSymbolicLink(folder.toPath()) && folder.canonicalFile.parentFile == root.canonicalFile) { "Draft folder must stay in the business workspace" }
        folder.mkdirs(); return folder
    }
    private fun destination(root: File, args: Map<String, String>): File {
        val source = args["source"]?.trim().orEmpty(); val event = args["event_id"]?.trim().orEmpty()
        require(source.isNotEmpty() && event.isNotEmpty() && source.length <= 500 && event.length <= 500) { "Use a stable source/account and event ID" }
        val identity = "$source\u0000$event"
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(folder(root), "$hash.json").also { require(!Files.isSymbolicLink(it.toPath())) { "Draft cannot be a symbolic link" } }
    }
    @Synchronized private fun prepare(root: File, args: Map<String, String>): String {
        val file = destination(root, args)
        if (file.exists()) return "Existing draft kept; nothing sent or overwritten. Review: drafts/${file.name}"
        require(!args["recipient"].isNullOrBlank() && !args["body"].isNullOrBlank()) { "Recipient and body are required" }
        require(args.values.sumOf { it.length } <= 30_000) { "Draft too large" }
        check(folder(root).listFiles()?.size?.let { it < 2000 } != false) { "Outbox full. Review and archive drafts in Files." }
        val contents = buildJsonObject {
            put("status", "DRAFT_REQUIRES_REVIEW"); put("sent", false); put("createdAt", System.currentTimeMillis())
            for (key in listOf("source", "event_id", "recipient", "subject", "body")) put(key, args[key].orEmpty())
        }.toString().toByteArray()
        val pending = Files.createTempFile(file.parentFile.toPath(), ".draft-", ".pending")
        try {
            java.io.FileOutputStream(pending.toFile()).use { it.write(contents); it.fd.sync() }
            Files.move(pending, file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { Files.deleteIfExists(pending) }
        check(file.readBytes().contentEquals(contents)) { "Draft verification failed" }
        return "Draft saved and verified, NOT sent. Review: drafts/${file.name}"
    }
    private fun tool(id: String, description: String, schema: Map<String, String>, action: (Map<String, String>) -> String) = object : Tool {
        override val definition = ToolDefinition(id, id, description, schema, riskLevel = RiskLevel.SAFE, confirmationPolicy = ConfirmationPolicy.NONE)
        override suspend fun execute(args: Map<String, String>): ToolResult = try { ToolResult.Success(action(args)) }
        catch (e: Exception) { ToolResult.Failure(e.message ?: "Outbox operation failed") }
    }
}
