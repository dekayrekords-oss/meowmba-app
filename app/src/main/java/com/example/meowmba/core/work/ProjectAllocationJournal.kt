package com.example.meowmba.core.work

import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Written BEFORE Room insertion. An interrupted insert can be reconciled by
 * its reserved conversation identity without allocating another project. */
internal class ProjectAllocationJournal(private val file: File) {
    data class Receipt(val key: String, val name: String, val kind: String, val at: Long,
        val conversationId: Long, val projectId: Long = 0, val verified: Boolean = false)

    fun read(): List<Receipt> {
        check(!Files.isSymbolicLink(file.toPath())) { "Linked project allocation storage is not supported" }
        if (!file.exists()) return emptyList()
        check(file.isFile && file.length() <= 64_000) { "Project allocation history is unavailable. Review Projects before creating more." }
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        check(root["version"]?.jsonPrimitive?.int == 1) { "Unknown project allocation journal version" }
        val records = root.getValue("receipts").jsonArray.map { entry ->
            val o = entry.jsonObject
            Receipt(o.getValue("key").jsonPrimitive.content, o.getValue("name").jsonPrimitive.content,
                o.getValue("kind").jsonPrimitive.content, o.getValue("at").jsonPrimitive.long,
                o.getValue("conversationId").jsonPrimitive.long, o.getValue("projectId").jsonPrimitive.long,
                o.getValue("verified").jsonPrimitive.boolean).also {
                check(it.key.matches(Regex("[a-f0-9]{64}")) && it.name.isNotBlank() && it.name.length <= 100 &&
                    it.conversationId > 0 && it.projectId >= 0 && it.kind in ProjectKind.entries.map { k -> k.name }) {
                    "Invalid project allocation history. Review Projects before creating more."
                }
            }
        }
        check(records.size <= 16 && records.map { it.key }.distinct().size == records.size &&
            records.map { it.conversationId }.distinct().size == records.size) { "Ambiguous project allocation history" }
        return records
    }

    fun save(records: List<Receipt>) {
        check(records.size <= 16)
        val parent = checkNotNull(file.parentFile) { "Project allocation directory is missing" }
        check(!Files.isSymbolicLink(file.toPath()) && !Files.isSymbolicLink(parent.toPath()))
        check(parent.mkdirs() || parent.isDirectory) { "Project allocation storage unavailable" }
        val bytes = buildJsonObject {
            put("version", 1)
            putJsonArray("receipts") { records.forEach { r -> add(buildJsonObject {
                put("key", r.key); put("name", r.name); put("kind", r.kind); put("at", r.at)
                put("conversationId", r.conversationId); put("projectId", r.projectId); put("verified", r.verified)
            }) } }
        }.toString().toByteArray()
        check(bytes.size <= 64_000)
        val temp = Files.createTempFile(parent.toPath(), ".allocation-", ".pending")
        try {
            FileOutputStream(temp.toFile()).use { it.write(bytes); it.fd.sync() }
            // No non-atomic overwrite fallback: losing a receipt could repeat an insert.
            Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
}
