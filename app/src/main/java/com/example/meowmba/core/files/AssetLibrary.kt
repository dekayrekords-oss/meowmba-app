package com.example.meowmba.core.files

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

data class AssetEntry(val path: String, val name: String, val folder: Boolean, val bytes: Long, val modified: Long, val category: String)
data class AssetScan(val entries: List<AssetEntry>, val truncated: Boolean)
data class AssetMove(val source: AssetEntry, val destination: String)
data class CleanupCandidate(val asset: AssetEntry, val reason: String)
data class TrashEntry(val id: String, val original: String, val bytes: Long, val date: Long)

/** The browser and agents share the existing workspace; never scan keys or model storage. */
class AssetLibrary(val root: File) {
    companion object {
        const val TRASH = ".asset-trash"
        val folders = listOf("projects", "photos", "videos", "audio", "documents", "imports")
        fun category(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
            "png", "jpg", "jpeg", "webp", "gif", "heic", "avif", "svg" -> "Photos"
            "mp4", "mkv", "webm", "mov", "m4v", "avi" -> "Videos"
            "wav", "mp3", "ogg", "m4a", "flac", "aac" -> "Audio"
            "zip", "tar", "gz", "bz2", "7z" -> "Archives"
            else -> "Documents"
        }
        fun safeName(value: String): String = value.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_")
            .trim().trim('.').take(160).ifBlank { "asset" }
    }

    init { check(root.mkdirs() || root.isDirectory) { "Asset library is unavailable" } }

    fun resolve(path: String, allowRoot: Boolean = false): File {
        require(!path.startsWith('/') && !path.contains('\\') && !path.contains(':') && path.none { it.isISOControl() } && path.split('/').none { it == ".." || it.startsWith('.') && it.isNotEmpty() }) { "Unsafe asset path" }
        var segment = root
        path.split('/').filter { it.isNotEmpty() }.forEach { part ->
            segment = File(segment, part)
            require(!java.nio.file.Files.isSymbolicLink(segment.toPath())) { "Linked paths are not supported" }
        }
        val f = File(root, path).canonicalFile
        require((allowRoot && f == root.canonicalFile) || f.path.startsWith(root.canonicalPath + File.separator)) { "Path is outside the asset library" }
        require(allowRoot || path.isNotBlank()) { "Choose a file" }
        return f
    }

    fun ensureFolders() { folders.forEach { check(resolve(it).mkdirs() || resolve(it).isDirectory) } }
    fun entry(file: File): AssetEntry {
        val path = file.relativeTo(root).invariantSeparatorsPath
        resolve(path)
        return AssetEntry(path, file.name, file.isDirectory, if (file.isFile) file.length() else 0, file.lastModified(),
            if (file.isDirectory) "Folders" else category(file.name))
    }

    /** Bounded, symlink-free enumeration. A truncated scan is always reported to the UI. */
    fun scan(limit: Int = 5000): AssetScan {
        val entries = mutableListOf<AssetEntry>()
        val pending = java.util.ArrayDeque<Pair<File, Int>>()
        pending.add(root to 0)
        var truncated = false
        while (pending.isNotEmpty() && entries.size < limit) {
            val (dir, depth) = pending.removeFirst()
            val children = dir.listFiles() ?: continue
            for (file in children.sortedBy { it.name.lowercase() }) {
                if (file.name.startsWith('.') || java.nio.file.Files.isSymbolicLink(file.toPath())) continue
                if (entries.size >= limit) { truncated = true; break }
                val item = runCatching { entry(file) }.getOrNull() ?: continue
                entries += item
                if (file.isDirectory) {
                    if (depth < 10) pending.add(file to depth + 1) else truncated = true
                }
            }
        }
        return AssetScan(entries, truncated || pending.isNotEmpty())
    }

    fun search(query: String): AssetScan {
        val scan = scan()
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        return scan.copy(entries = scan.entries.filter { item -> terms.all { it in "${item.path} ${item.category}".lowercase() } })
    }

    @Synchronized fun createFolder(path: String): File = resolve(path).also {
        require(!it.exists()) { "That folder already exists" }
        check(it.mkdirs()) { "Could not create folder" }
    }

    /** A new destination every time: imports and generated outputs must not overwrite. */
    @Synchronized fun newOutput(folder: String, name: String): File {
        val dir = resolve(folder)
        check(dir.mkdirs() || dir.isDirectory)
        val safe = safeName(name)
        val destination = resolve("$folder/${UUID.randomUUID().toString().take(8)}_$safe")
        check(destination.createNewFile()) { "Could not reserve output" }
        return destination
    }

    fun import(name: String, folder: String, input: InputStream): AssetEntry {
        val file = newOutput(folder, name)
        try {
            file.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var total = 0L
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= 11_000_000_000L && root.usableSpace > 64_000_000L) { "Import exceeds 11 GB or free space is too low" }
                    output.write(buffer, 0, count)
                }
            }
            return entry(file)
        } catch (error: Exception) { file.delete(); throw error }
    }

    private fun unchanged(item: AssetEntry): File = resolve(item.path).also {
        require(it.isFile && !item.folder && it.length() == item.bytes && it.lastModified() == item.modified) { "${item.name} changed. Refresh and review it again." }
    }

    /** Each move is independently checked; no overwrites, root moves or invented AI paths. */
    @Synchronized fun move(plan: AssetMove) {
        val source = unchanged(plan.source)
        val target = resolve(plan.destination)
        require(source != target && !target.exists()) { "Destination already exists or is unchanged" }
        check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
        check(source.renameTo(target)) { "Could not move ${plan.source.name}" }
    }

    fun cleanup(): Pair<List<CleanupCandidate>, Boolean> {
        val scan = scan()
        val files = scan.entries.filter { !it.folder }
        val candidates = linkedMapOf<String, CleanupCandidate>()
        files.filter { it.bytes == 0L }.forEach { candidates[it.path] = CleanupCandidate(it, "Empty file — review before removing") }
        var remaining = 1_000_000_000L
        var limited = scan.truncated
        for (group in files.filter { it.bytes > 0 }.groupBy { it.bytes }.values.filter { it.size > 1 }) {
            val hashes = mutableMapOf<String, AssetEntry>()
            for (item in group.sortedBy { it.path }) {
                if (item.bytes > remaining) { limited = true; continue }
                remaining -= item.bytes
                val file = runCatching { unchanged(item) }.getOrNull() ?: continue
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                val keeper = hashes.putIfAbsent(hash, item)
                if (keeper != null) candidates[item.path] = CleanupCandidate(item, "Identical bytes at scan time to ${keeper.path}")
            }
        }
        files.filter { it.bytes >= 100_000_000L && it.path !in candidates }.forEach {
            candidates[it.path] = CleanupCandidate(it, "Large file — not necessarily unwanted")
        }
        return candidates.values.toList() to limited
    }

    private fun trashDir(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}"))) { "Invalid trash item" }
        val base = File(root, TRASH)
        require(!java.nio.file.Files.isSymbolicLink(base.toPath()))
        return File(base, id).also { require(it.canonicalPath.startsWith(root.canonicalPath + "/$TRASH/")) }
    }
    @Synchronized fun trash(item: AssetEntry): String {
        val source = unchanged(item)
        val id = UUID.randomUUID().toString()
        val dir = trashDir(id)
        check(dir.mkdirs())
        val metadata = Properties().apply { setProperty("path", item.path); setProperty("date", System.currentTimeMillis().toString()) }
        try {
            File(dir, "entry.properties").outputStream().use { metadata.store(it, "Restore location") }
            check(source.renameTo(File(dir, "payload"))) { "Could not move file to Trash" }
        } catch (e: Exception) { File(dir, "entry.properties").delete(); dir.delete(); throw e }
        return id
    }
    fun trashItems(): List<TrashEntry> = File(root, TRASH).listFiles().orEmpty().mapNotNull { folder ->
        runCatching {
            val dir = trashDir(folder.name)
            val payload = File(dir, "payload")
            require(payload.isFile && !java.nio.file.Files.isSymbolicLink(payload.toPath()))
            val metadata = Properties().apply { File(dir, "entry.properties").inputStream().use { load(it) } }
            val path = metadata.getProperty("path")
            resolve(path)
            TrashEntry(folder.name, path, payload.length(), metadata.getProperty("date").toLong())
        }.getOrNull()
    }.sortedByDescending { it.date }
    @Synchronized fun restore(id: String) {
        val item = trashItems().firstOrNull { it.id == id } ?: error("Trash item is missing")
        val target = resolve(item.original)
        require(!target.exists()) { "A file already exists at ${item.original}; move it first" }
        check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
        check(File(trashDir(id), "payload").renameTo(target))
        File(trashDir(id), "entry.properties").delete(); trashDir(id).delete()
    }
    /** Only explicit per-item permanent-delete confirmation may call this. */
    @Synchronized fun purge(id: String) {
        require(trashItems().any { it.id == id }) { "Trash item is missing" }
        val dir = trashDir(id)
        check(File(dir, "payload").delete()) { "Could not delete file" }
        File(dir, "entry.properties").delete(); dir.delete()
    }
}
