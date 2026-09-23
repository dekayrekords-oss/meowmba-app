package com.example.meowmba.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable data class BackupMetadata(val sourcePackage: String, val versionCode: Long, val versionName: String, val createdAt: Long)
@Serializable data class BackupEntry(val path: String, val sha256: String, val length: Long)
@Serializable data class BackupManifest(val metadata: BackupMetadata, val entries: List<BackupEntry>)
data class UnpackedBackup(val directory: File, val manifest: BackupManifest)

/** Framed AES-GCM keeps Android providers from buffering a whole backup in memory.
 * Each frame authenticates its index, size and header. An authenticated empty final frame,
 * followed by EOF, prevents truncation, concatenation and frame substitution. Callers own passwords.
 */
object EncryptedBackupArchive {
    // Immutable v2 format parameter. Changing the KDF requires a new version and a v2 reader.
    private const val ITERATIONS = 600_000
    private const val CHUNK = 64 * 1024
    private const val MAX_BYTES = 512L * 1024 * 1024
    private const val MAX_ZIP_BYTES = MAX_BYTES + 32 * 1024 * 1024
    private const val MAX_FILES = 20_000
    private const val MAX_MANIFEST = 8 * 1024 * 1024
    private const val MANIFEST = "__manifest.json"
    private val magic = "MEOWBKP2".toByteArray(Charsets.US_ASCII)
    private val json = Json { ignoreUnknownKeys = false }

    fun write(sourceDir: File, output: OutputStream, password: CharArray, metadata: BackupMetadata): BackupManifest {
        validateMetadata(metadata)
        require(password.size >= 12 && sourceDir.isDirectory && !Files.isSymbolicLink(sourceDir.toPath()))
        val files = mutableListOf<File>()
        sourceDir.walkTopDown().onEnter { require(!Files.isSymbolicLink(it.toPath())); true }.forEach {
            require(!Files.isSymbolicLink(it.toPath()))
            if (it.isFile) { files += it; require(files.size <= MAX_FILES) }
        }
        val entries = mutableListOf<BackupEntry>()
        val framed = encryptingStream(output, password)
        var total = 0L
        try {
            ZipOutputStream(framed).use { zip ->
                for (file in files.sortedBy { it.path }) {
                    val path = file.relativeTo(sourceDir).invariantSeparatorsPath
                    validatePath(path); require(path != MANIFEST)
                    require(file.canonicalFile.toPath().startsWith(sourceDir.canonicalFile.toPath()))
                    val digest = MessageDigest.getInstance("SHA-256"); var length = 0L
                    zip.putNextEntry(ZipEntry(path))
                    file.inputStream().use { input ->
                        val buffer = ByteArray(CHUNK)
                        while (true) {
                            val n = input.read(buffer); if (n < 0) break
                            length += n; total += n; require(total <= MAX_BYTES)
                            digest.update(buffer, 0, n); zip.write(buffer, 0, n)
                        }
                    }
                    zip.closeEntry(); entries += BackupEntry(path, digest.digest().hex(), length)
                }
                val manifest = json.encodeToString(BackupManifest.serializer(), BackupManifest(metadata, entries)).toByteArray()
                require(manifest.size <= MAX_MANIFEST)
                zip.putNextEntry(ZipEntry(MANIFEST)); zip.write(manifest); zip.closeEntry()
            }
        } finally { framed.erase() }
        return BackupManifest(metadata, entries)
    }

    fun read(input: InputStream, password: CharArray, privateWorkingDir: File): UnpackedBackup {
        require(password.size >= 12)
        require(!Files.isSymbolicLink(privateWorkingDir.toPath()))
        require(privateWorkingDir.isDirectory || privateWorkingDir.mkdirs())
        val temp = File.createTempFile("authenticated-", ".zip", privateWorkingDir)
        try {
            temp.outputStream().buffered().use { decrypt(input, it, password) }
            return unpack(temp, privateWorkingDir)
        } catch (e: Exception) { throw IllegalArgumentException("Unable to verify encrypted backup", e) }
        finally { temp.delete() }
    }

    /** Internal fixture seam also used by ZIP writer; it never closes the caller's stream. */
    internal fun encryptZip(input: InputStream, output: OutputStream, password: CharArray) {
        val stream = encryptingStream(output, password)
        try { stream.use { input.copyTo(it, CHUNK) } } finally { stream.erase() }
    }

    private fun encryptingStream(output: OutputStream, password: CharArray): FramedOutput {
        require(password.size >= 12)
        val random = SecureRandom(); val salt = ByteArray(16).also(random::nextBytes)
        val prefix = ByteArray(8).also(random::nextBytes)
        val header = magic + byteArrayOf(2) + salt + prefix
        output.write(header)
        return FramedOutput(output, header, derive(password, salt), prefix)
    }

    private class FramedOutput(output: OutputStream, private val header: ByteArray, private val key: ByteArray, private val prefix: ByteArray) : OutputStream() {
        private val out = DataOutputStream(output)
        private val buffer = ByteArray(CHUNK)
        private var count = 0; private var index = 0; private var total = 0L; private var closed = false
        override fun write(b: Int) { check(!closed); buffer[count++] = b.toByte(); if (count == CHUNK) frame() }
        override fun write(b: ByteArray, off: Int, len: Int) {
            require(off >= 0 && len >= 0 && off <= b.size - len); check(!closed)
            var pos = off; var left = len
            while (left > 0) { val n = minOf(left, CHUNK - count); b.copyInto(buffer, count, pos, pos + n); count += n; pos += n; left -= n; if (count == CHUNK) frame() }
        }
        private fun frame() {
            total += count; require(total <= MAX_ZIP_BYTES)
            val cipher = cipher(Cipher.ENCRYPT_MODE, key, header, prefix, index++, count)
            out.writeInt(count); out.write(cipher.doFinal(buffer, 0, count)); buffer.fill(0); count = 0
        }
        override fun close() { if (!closed) { try { if (count > 0) frame(); frame(); out.flush() } finally { closed = true; erase() } } }
        fun erase() { key.fill(0); buffer.fill(0) }
    }

    private fun decrypt(input: InputStream, output: OutputStream, password: CharArray) {
        val source = DataInputStream(input); val header = ByteArray(33); source.readFully(header)
        require(header.copyOfRange(0, 8).contentEquals(magic) && header[8] == 2.toByte())
        val key = derive(password, header.copyOfRange(9, 25)); val prefix = header.copyOfRange(25, 33)
        try {
            var index = 0; var total = 0L
            while (true) {
                val length = source.readInt(); require(length in 0..CHUNK)
                total += length; require(total <= MAX_ZIP_BYTES)
                val encrypted = ByteArray(length + 16); source.readFully(encrypted)
                val plain = cipher(Cipher.DECRYPT_MODE, key, header, prefix, index++, length).doFinal(encrypted)
                try {
                    require(plain.size == length)
                    if (length == 0) { require(source.read() == -1); break }
                    output.write(plain)
                } finally { plain.fill(0) }
            }
        } finally { key.fill(0) }
    }

    private fun cipher(mode: Int, key: ByteArray, header: ByteArray, prefix: ByteArray, index: Int, length: Int): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            require(index >= 0)
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, prefix + ByteBuffer.allocate(4).putInt(index).array()))
            updateAAD(header); updateAAD(ByteBuffer.allocate(8).putInt(index).putInt(length).array())
        }

    private fun unpack(zipFile: File, work: File): UnpackedBackup {
        val out = Files.createTempDirectory(work.toPath(), "unpacked-").toFile()
        try {
            var manifest: BackupManifest? = null; val found = linkedMapOf<String, BackupEntry>(); var total = 0L
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break; val name = entry.name
                    validatePath(name); require(!entry.isDirectory && name !in found)
                    if (name == MANIFEST) {
                        require(manifest == null)
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) { val n = zip.read(buffer); if (n < 0) break; require(bytes.size() + n <= MAX_MANIFEST); bytes.write(buffer, 0, n) }
                        manifest = json.decodeFromString(BackupManifest.serializer(), bytes.toString("UTF-8"))
                    } else {
                        require(found.size < MAX_FILES)
                        val target = File(out, name); require(target.canonicalFile.toPath().startsWith(out.canonicalFile.toPath()))
                        require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                        val digest = MessageDigest.getInstance("SHA-256"); var length = 0L
                        target.outputStream().use { sink ->
                            val b = ByteArray(CHUNK)
                            while (true) { val n = zip.read(b); if (n < 0) break; length += n; total += n; require(total <= MAX_BYTES); digest.update(b, 0, n); sink.write(b, 0, n) }
                        }
                        found[name] = BackupEntry(name, digest.digest().hex(), length)
                    }
                    zip.closeEntry()
                }
            }
            val m = requireNotNull(manifest); validateMetadata(m.metadata)
            require(m.entries.size <= MAX_FILES && m.entries.map { it.path }.toSet().size == m.entries.size)
            m.entries.forEach { validatePath(it.path); require(it.path != MANIFEST && it.length >= 0 && it.sha256.matches(Regex("[a-f0-9]{64}"))) }
            require(found == m.entries.associateBy { it.path }) { "Manifest integrity mismatch" }
            return UnpackedBackup(out, m)
        } catch (e: Exception) { out.deleteRecursively(); throw e }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    private fun validateMetadata(m: BackupMetadata) { require(m.sourcePackage in setOf("com.example.meowmba", "com.meow.meowmba") && m.versionCode > 0 && m.createdAt > 0 && m.versionName.length <= 128) }
    private fun validatePath(p: String) { require(p.isNotEmpty() && p.length <= 1024 && !p.startsWith('/') && '\\' !in p && '\u0000' !in p && p.split('/').none { it.isEmpty() || it == "." || it == ".." }) }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
