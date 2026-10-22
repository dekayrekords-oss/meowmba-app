package com.example.meowmba.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedBackupArchiveTest {
    private val password = "correct horse battery".toCharArray()
    private val metadata = BackupMetadata(BackupUpdatePolicy.PREVIEW, 7, "7.0", 1)
    private val work = Files.createTempDirectory("archive-test-").toFile()

    @After fun tearDown() { work.deleteRecursively() }

    @Test fun `round trip preserves empty unicode and binary files`() {
        val source = Files.createTempDirectory("archive-source-").toFile()
        File(source, "empty.txt").writeBytes(ByteArray(0))
        File(source, "unicode/hello-世界.txt").apply { parentFile.mkdirs(); writeText("café ☃ Привет") }
        File(source, "bytes.bin").writeBytes(byteArrayOf(0, 1, 2, -1, 127))
        val encoded = ByteArrayOutputStream()
        val originalPassword = password.copyOf()
        EncryptedBackupArchive.write(source, encoded, password, metadata)
        val unpacked = EncryptedBackupArchive.read(ByteArrayInputStream(encoded.toByteArray()), password, work)
        assertArrayEquals(originalPassword, password)
        assertEquals("café ☃ Привет", File(unpacked.directory, "unicode/hello-世界.txt").readText())
        assertEquals(0L, File(unpacked.directory, "empty.txt").length())
        assertArrayEquals(byteArrayOf(0, 1, 2, -1, 127), File(unpacked.directory, "bytes.bin").readBytes())
    }

    @Test fun `wrong password and authenticated mutations fail without work output`() {
        val encoded = archiveWithFiles("a.txt" to "payload".toByteArray())
        assertReadFails(encoded, "wrong password".toCharArray())
        val bitflip = encoded.copyOf().also { it[it.lastIndex - 25] = (it[it.lastIndex - 25].toInt() xor 1).toByte() }
        assertReadFails(bitflip, password)
    }

    @Test fun `truncation final frame removal and trailing bytes fail`() {
        val encoded = archiveWithFiles("a.txt" to "payload".toByteArray())
        assertReadFails(encoded.copyOf(encoded.size - 1), password)
        assertReadFails(encoded.copyOf(encoded.size - 20), password)
        assertReadFails(encoded + byteArrayOf(9), password)
    }

    @Test fun `invalid metadata is rejected`() {
        val source = Files.createTempDirectory("archive-source-").toFile()
        val out = ByteArrayOutputStream()
        var failed = false
        try { EncryptedBackupArchive.write(source, out, password, metadata.copy(sourcePackage = "other")) } catch (_: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }

    @Test fun `malformed manifests reject traversal duplicate missing and hash mismatch`() {
        val digest = sha256("ok".toByteArray())
        val valid = BackupEntry("a.txt", digest, 2)
        assertReadFails(manualZip(listOf("../escape.txt" to "bad".toByteArray()), null), password)
        assertReadFails(manualZip(listOf("a.txt" to "ok".toByteArray(), "b.txt" to "again".toByteArray()), listOf(valid, valid)), password)
        assertReadFails(manualZip(listOf("a.txt" to "ok".toByteArray()), null), password)
        assertReadFails(manualZip(listOf("a.txt" to "ok".toByteArray()), listOf(valid.copy(sha256 = "0".repeat(64)))), password)
    }

    @Test fun `permanent v2 fixture remains readable`() {
        val resource = requireNotNull(javaClass.getResourceAsStream("/backup/golden-v2.meowbackup"))
        val unpacked = resource.use { EncryptedBackupArchive.read(it, "fixture-password-only".toCharArray(), work) }
        assertEquals("portable test data", File(unpacked.directory, "note.txt").readText())
        assertEquals(160L, unpacked.manifest.metadata.versionCode)
    }

    @Test fun `reordered frames and oversized length are rejected`() {
        val payload = ByteArray(180_000).also { java.util.Random(42).nextBytes(it) }
        val encoded = archiveWithFiles("random.bin" to payload)
        val frameBytes = 4 + 65536 + 16
        val first = encoded.copyOfRange(33, 33 + frameBytes)
        val second = encoded.copyOfRange(33 + frameBytes, 33 + 2 * frameBytes)
        assertReadFails(encoded.copyOfRange(0, 33) + second + first + encoded.copyOfRange(33 + 2 * frameBytes, encoded.size), password)
        val oversized = encoded.copyOf()
        java.nio.ByteBuffer.wrap(oversized, 33, 4).putInt(Int.MAX_VALUE)
        assertReadFails(oversized, password)
    }

    private fun archiveWithFiles(vararg files: Pair<String, ByteArray>): ByteArray {
        val source = Files.createTempDirectory("archive-source-").toFile()
        files.forEach { (path, bytes) -> File(source, path).apply { parentFile?.mkdirs(); writeBytes(bytes) } }
        return ByteArrayOutputStream().also { EncryptedBackupArchive.write(source, it, password, metadata) }.toByteArray()
    }

    private fun manualZip(files: List<Pair<String, ByteArray>>, entries: List<BackupEntry>?): ByteArray {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { out ->
            files.forEach { (name, bytes) -> out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry() }
            if (entries != null) {
                val manifest = BackupManifest(metadata, entries)
                out.putNextEntry(ZipEntry("__manifest.json"))
                out.write(Json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                out.closeEntry()
            }
        }
        return ByteArrayOutputStream().also { encrypted ->
            EncryptedBackupArchive.encryptZip(ByteArrayInputStream(zip.toByteArray()), encrypted, password)
        }.toByteArray()
    }

    private fun assertReadFails(encoded: ByteArray, suppliedPassword: CharArray) {
        try {
            EncryptedBackupArchive.read(ByteArrayInputStream(encoded), suppliedPassword, work)
            throw AssertionError("corrupt archive was accepted")
        } catch (_: IllegalArgumentException) {
            assertTrue(work.listFiles().orEmpty().isEmpty())
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
