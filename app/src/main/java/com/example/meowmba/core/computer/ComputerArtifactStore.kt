package com.example.meowmba.core.computer

import com.example.meowmba.core.files.AssetLibrary
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest

/** Only explicitly retrieved Computer files enter the shared library. No remote
 * prose can mark a file saved; a bounded local copy is read back before returning. */
class ComputerArtifactStore(workspace: File) {
    private val library = AssetLibrary(workspace)

    data class Saved(val file: File, val libraryPath: String, val bytes: Long, val sha256: String, val sourcePath: String)

    fun save(session: String, relativePath: String, bytes: ByteArray): Saved = synchronized(LOCK) {
        OpenCodeComputer.id(session)
        OpenCodeComputer.artifactPath(relativePath)
        require(bytes.isNotEmpty() && bytes.size <= 2_000_000) { "The result is empty or exceeds the supported transfer size." }
        val hash = digest(bytes)
        val pathHash = digest(relativePath.toByteArray(Charsets.UTF_8)).take(12)
        val folder = "projects/opencode/$session"
        val dir = library.resolve(folder)
        check(dir.mkdirs() || dir.isDirectory) { "Computer results folder is unavailable." }
        val name = "$pathHash-${hash.take(12)}-${boundedName(relativePath.substringAfterLast('/'))}"
        val libraryPath = "$folder/$name"
        val target = library.resolve(libraryPath)
        if (!target.exists()) {
            val pending = File.createTempFile(".computer-result-", ".part", dir)
            try {
                FileOutputStream(pending).use { it.write(bytes); it.fd.sync() }
                // Android app storage may forbid hard links. Publish within this
                // directory using the no-replacement move contract; do not request
                // ATOMIC_MOVE, whose existing-target semantics are platform-specific.
                // A concurrent existing copy is verified below, never overwritten.
                try { Files.move(pending.toPath(), target.toPath()) }
                catch (_: java.nio.file.FileAlreadyExistsException) { /* verify existing bytes below */ }
            } finally { pending.delete() }
        }
        check(target.isFile && target.length() == bytes.size.toLong() &&
            target.inputStream().use { input ->
                val actual = ByteArray(bytes.size + 1)
                var count = 0
                while (count < actual.size) {
                    val n = input.read(actual, count, actual.size - count)
                    if (n < 0) break
                    count += n
                }
                count == bytes.size && digest(actual.copyOf(count)) == hash
            }) {
            "The saved result did not pass verification. Existing files were preserved."
        }
        Saved(target, libraryPath, target.length(), hash, relativePath)
    }

    companion object {
        private val LOCK = Any()
        /** Keep the hash prefix + UTF-8 basename below Android's 255-byte leaf limit. */
        private fun boundedName(value: String): String {
            val safe = AssetLibrary.safeName(value)
            val extension = safe.substringAfterLast('.', "").takeIf { it.matches(Regex("[A-Za-z0-9]{1,12}")) }
            val suffix = extension?.let { ".$it" }.orEmpty()
            val stem = if (suffix.isEmpty()) safe else safe.dropLast(suffix.length)
            var remaining = 220 - suffix.toByteArray(Charsets.UTF_8).size
            val prefix = buildString {
                for (point in stem.codePoints().toArray()) {
                    val text = String(Character.toChars(point))
                    val size = text.toByteArray(Charsets.UTF_8).size
                    if (size > remaining) break
                    append(text); remaining -= size
                }
            }.ifBlank { "result" }
            return prefix + suffix
        }
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
