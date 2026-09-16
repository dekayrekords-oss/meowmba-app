package com.example.meowmba.ui.companion

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.Normalizer
import java.util.Locale

/** Private bounded library. Neither model names nor user descriptions become file paths. */
class GeneratedAvatarStore(private val dir: File) {
    private val idPattern = Regex("[0-9a-f]{64}")
    private data class Saved(val form: GeneratedAvatarForm, val requests: List<String>)

    @Synchronized fun save(form: GeneratedAvatarForm, request: String) {
        val validated = GeneratedAvatarForms.parse(GeneratedAvatarForms.toJson(form))
        require(form.id == validated.id) { "Avatar drawing ID does not match its contents" }
        val normalized = normalizeRequest(request)
        require(normalized.isNotEmpty()) { "Describe the avatar to save" }
        require(request.length <= GeneratedAvatarForms.MAX_REQUEST && normalized.length <= GeneratedAvatarForms.MAX_REQUEST) {
            "Avatar description is too long"
        }
        val target = file(form.id) ?: throw IllegalArgumentException("Invalid avatar ID")
        val previous = read(target)
        // Do not evict a form behind the user's back: it may still be selected.
        if (previous == null && list().size >= GeneratedAvatarForms.MAX_SAVED)
            throw IllegalStateException("Your avatar library is full. Remove a saved form before creating another.")
        val requests = (listOf(normalized) + previous?.requests.orEmpty()).distinct().take(8)
        val document = JSONObject().put("version", 1).put("requests", JSONArray(requests))
            .put("form", JSONObject(GeneratedAvatarForms.toJson(validated))).toString()
        check(dir.isDirectory || dir.mkdirs()) { "Could not create the avatar library" }
        val pending = File.createTempFile("avatar-", ".pending", dir)
        try {
            FileOutputStream(pending).use { stream ->
                stream.write(document.toByteArray(Charsets.UTF_8)); stream.fd.sync()
            }
            try { Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        } finally { pending.delete() }
    }

    @Synchronized fun list(): List<GeneratedAvatarForm> = savedFiles().mapNotNull { read(it)?.form }.take(GeneratedAvatarForms.MAX_SAVED)

    @Synchronized fun load(id: String): GeneratedAvatarForm? = file(id)?.let(::read)?.form

    @Synchronized fun find(request: String): GeneratedAvatarForm? {
        if (request.length > GeneratedAvatarForms.MAX_REQUEST) return null
        val normalized = normalizeRequest(request)
        if (normalized.isEmpty()) return null
        return savedFiles().asSequence().mapNotNull(::read).firstOrNull {
            normalized in it.requests || normalized == normalizeRequest(it.form.name) || normalized == normalizeRequest(it.form.description)
        }?.form
    }

    @Synchronized fun delete(id: String): Boolean = file(id)?.delete() ?: false

    private fun file(id: String): File? = if (idPattern.matches(id)) File(dir, "$id.json") else null

    private fun savedFiles(): List<File> = dir.listFiles { file -> file.isFile &&
        file.name.endsWith(".json") && idPattern.matches(file.name.removeSuffix(".json")) }
        .orEmpty().sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })
        .take(GeneratedAvatarForms.MAX_SAVED * 4)

    private fun read(file: File): Saved? = runCatching {
        if (!file.isFile || file.length() !in 1..65_536) return null
        val root = JSONObject(file.readText(Charsets.UTF_8))
        if (root.optInt("version") != 1) return null
        val form = GeneratedAvatarForms.parse(root.getJSONObject("form").toString())
        if (file.name != "${form.id}.json") return null
        val rawRequests = root.getJSONArray("requests")
        if (rawRequests.length() !in 1..8) return null
        val requests = (0 until rawRequests.length()).map {
            val value = rawRequests.opt(it) as? String ?: return null
            if (value.isEmpty() || value.length > GeneratedAvatarForms.MAX_REQUEST) return null
            normalizeRequest(value)
        }
        Saved(form, requests)
    }.getOrNull()

    companion object {
        fun normalizeRequest(request: String): String = Normalizer.normalize(request, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?')
    }
}
