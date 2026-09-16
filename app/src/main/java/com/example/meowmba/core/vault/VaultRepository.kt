package com.example.meowmba.core.vault

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Obsidian-compatible vault: plain Markdown files stored one-per-note in a
 * sandboxed directory. File names are the title, sanitized. Front-matter
 * (`---`) carries tags and a creation timestamp.
 *
 * The vault is the only place vault tools may touch — same sandbox model as
 * [Workspace]. No Room: notes are user-visible text files, exactly like
 * Obsidian itself stores them, so they can be synced or exported freely.
 */
class VaultRepository(val dir: File) {

    init { dir.mkdirs() }

    // ── Public API ──────────────────────────────────────────────────────────

    /** All notes, sorted newest-first. Includes one level of subfolders (Notes/). */
    fun list(): List<VaultNote> =
        dir.walkTopDown()
            .maxDepth(2)
            .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            .mapNotNull { parse(it) }
            .sortedByDescending { it.createdAt }
            .toList()

    /** A single note by title (case-insensitive). Null = not found. */
    @Synchronized
    fun get(title: String): VaultNote? {
        val file = file(title)
        return if (file.isFile) parse(file) else null
    }

    /**
     * Create or overwrite a note. Tags are optional; content is the body text
     * (everything after the front-matter). Returns the saved note.
     */
    @Synchronized
    fun save(title: String, body: String, tags: List<String> = emptyList()): VaultNote {
        val existing = get(title)
        val createdAt = existing?.createdAt ?: System.currentTimeMillis()
        val note = VaultNote(
            title = title.trim(),
            body = body,
            tags = tags,
            createdAt = createdAt,
            modifiedAt = System.currentTimeMillis(),
        )
        val target = file(title)
        val temporary = File.createTempFile("note-", ".tmp", target.parentFile)
        try {
            java.io.FileOutputStream(temporary).use { output ->
                output.write(serialize(note).toByteArray(Charsets.UTF_8)); output.fd.sync()
            }
            try {
                java.nio.file.Files.move(temporary.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
        return note
    }

    /** Append text to an existing note (or create it). Returns the updated note. */
    @Synchronized
    fun append(title: String, text: String): VaultNote {
        val existing = get(title)
        val newBody = if (existing != null) existing.body + "\n\n" + text.trim() else text.trim()
        return save(title, newBody, existing?.tags ?: emptyList())
    }

    /** O(utterance-size) journal writes; never read/rewrite a growing day's transcript. */
    @Synchronized
    fun appendTranscript(title: String, text: String, maxBytes: Long = MAX_TRANSCRIPT_BYTES) {
        require(text.length <= 8_192) { "That note segment is too long to save." }
        val target = file(title)
        if (!target.exists()) save(title, "", listOf("local-transcript"))
        val bytes = ("\n\n" + text.trim()).toByteArray(Charsets.UTF_8)
        check(target.length() + bytes.size <= maxBytes) { "This notes file reached its size limit. Start a new topic to continue." }
        java.io.FileOutputStream(target, true).use { output -> output.write(bytes); output.fd.sync() }
    }

    @Synchronized
    fun transcript(title: String): VaultNote? {
        val target = file(title)
        check(target.length() <= MAX_TRANSCRIPT_BYTES) { "This notes file is too large for a local summary." }
        return if (target.isFile) parse(target) else null
    }

    /** Delete a note by title. Returns true if it existed. */
    @Synchronized
    fun delete(title: String): Boolean = file(title).delete()

    /**
     * Full-text search across all notes. Returns notes whose title or body
     * contains [query] (case-insensitive). Sorted by relevance (title match
     * first, then body match).
     */
    fun search(query: String): List<VaultNote> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        val all = list()
        val titleMatches = all.filter { q in it.title.lowercase() }
        val bodyMatches = all.filter { q !in it.title.lowercase() && q in it.body.lowercase() }
        return titleMatches + bodyMatches
    }

    /** [[wikilinks]] referenced in a note that do not yet have their own file. */
    fun missingLinks(note: VaultNote): List<String> {
        val refs = WIKILINK.findAll(note.body).map { it.groupValues[1].trim() }.toList()
        return refs.filter { get(it) == null }
    }

    // ── Serialization ───────────────────────────────────────────────────────

    private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    @Synchronized
    private fun serialize(note: VaultNote): String = buildString {
        append("---\n")
        append("title: ${note.title}\n")
        append("created: ${DATE_FMT.format(Date(note.createdAt))}\n")
        append("modified: ${DATE_FMT.format(Date(note.modifiedAt))}\n")
        if (note.tags.isNotEmpty()) append("tags: [${note.tags.joinToString(", ")}]\n")
        append("---\n\n")
        append(note.body)
    }

    @Synchronized
    private fun parse(file: File): VaultNote? = runCatching {
        val text = file.readText()
        if (!text.startsWith("---")) {
            // No front-matter: treat entire file as body, derive title from filename
            return@runCatching VaultNote(
                title = file.nameWithoutExtension.replace('_', ' '),
                body = text,
                tags = emptyList(),
                createdAt = file.lastModified(),
                modifiedAt = file.lastModified(),
            )
        }
        val end = text.indexOf("\n---", 4)
        if (end < 0) return@runCatching null
        val front = text.substring(4, end)
        val body = text.substring(end + 5).trimStart('\n')

        var title = file.nameWithoutExtension.replace('_', ' ')
        var createdAt = file.lastModified()
        var modifiedAt = file.lastModified()
        val tags = mutableListOf<String>()

        for (line in front.lines()) {
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val key = line.substring(0, colon).trim()
            val value = line.substring(colon + 1).trim()
            when (key) {
                "title" -> title = value
                "created" -> runCatching { createdAt = DATE_FMT.parse(value)!!.time }
                "modified" -> runCatching { modifiedAt = DATE_FMT.parse(value)!!.time }
                "tags" -> tags.addAll(
                    value.removeSurrounding("[", "]").split(",").map { it.trim() }.filter { it.isNotBlank() }
                )
            }
        }
        VaultNote(title = title, body = body, tags = tags, createdAt = createdAt, modifiedAt = modifiedAt)
    }.getOrNull()

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun file(title: String): File {
        val parts = title.split('/')
            .map { sanitize(it) }
            .filter { it.isNotBlank() }
        val relative = parts.joinToString(File.separator) + ".md"
        val f = File(dir, relative)
        f.parentFile?.mkdirs()
        return f
    }

    private fun sanitize(title: String): String =
        title.trim().replace(Regex("[^A-Za-z0-9 _\\-]"), "_").replace(' ', '_').take(120)

    companion object {
        const val MAX_TRANSCRIPT_BYTES = 1_048_576L
        private val WIKILINK = Regex("\\[\\[([^\\]]+)\\]\\]")
    }
}

data class VaultNote(
    val title: String,
    val body: String,
    val tags: List<String>,
    val createdAt: Long,
    val modifiedAt: Long,
) {
    /** Plain text preview for list items. */
    val preview: String get() = body.lines().firstOrNull { it.isNotBlank() }?.take(120) ?: ""
}
