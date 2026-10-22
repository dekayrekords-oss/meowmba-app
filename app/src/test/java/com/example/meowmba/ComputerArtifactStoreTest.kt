package com.example.meowmba

import com.example.meowmba.core.computer.ComputerArtifactStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ComputerArtifactStoreTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `verified result is discoverable in Files and repeat save reuses identical copy`() {
        val root = temp.newFolder()
        val store = ComputerArtifactStore(root)
        val bytes = "actual result\n".toByteArray()
        val first = store.save("ses_test", "reports/result.md", bytes)
        val second = store.save("ses_test", "reports/result.md", bytes)
        assertArrayEquals(bytes, first.file.readBytes())
        assertEquals(first, second)
        assertEquals(bytes.size.toLong(), first.bytes)
        assertEquals(64, first.sha256.length)
        assertTrue(first.libraryPath.startsWith("projects/opencode/ses_test/"))
        assertEquals(1, first.file.parentFile!!.listFiles()!!.size)
    }

    @Test fun `changed results and identical basenames never overwrite earlier outputs`() {
        val store = ComputerArtifactStore(temp.newFolder())
        val old = store.save("ses_test", "a/result.txt", "old".toByteArray())
        val updated = store.save("ses_test", "a/result.txt", "new".toByteArray())
        val other = store.save("ses_test", "b/result.txt", "old".toByteArray())
        assertEquals(3, setOf(old.file, updated.file, other.file).size)
        assertEquals("old", old.file.readText())
        assertEquals("new", updated.file.readText())
    }

    @Test fun `unsafe empty oversized and linked destinations fail closed`() {
        val root = temp.newFolder()
        val store = ComputerArtifactStore(root)
        for (path in listOf("../keys", "/outside", "a/../../outside", "a\\b")) {
            assertTrue(runCatching { store.save("ses_test", path, byteArrayOf(1)) }.isFailure)
        }
        assertTrue(runCatching { store.save("../bad", "ok.txt", byteArrayOf(1)) }.isFailure)
        assertTrue(runCatching { store.save("ses_test", "ok.txt", byteArrayOf()) }.isFailure)
        assertTrue(runCatching { store.save("ses_test", "ok.txt", ByteArray(2_000_001)) }.isFailure)
        Files.createSymbolicLink(File(root, "projects").toPath(), temp.newFolder().toPath())
        assertTrue(runCatching { store.save("ses_test", "ok.txt", byteArrayOf(1)) }.isFailure)
    }

    @Test fun `locally modified result is not silently overwritten`() {
        val store = ComputerArtifactStore(temp.newFolder())
        val saved = store.save("ses_test", "result.txt", "original".toByteArray())
        saved.file.writeText("modified")
        assertTrue(runCatching { store.save("ses_test", "result.txt", "original".toByteArray()) }.isFailure)
        assertEquals("modified", saved.file.readText())
    }

    @Test fun `unicode basename stays within filesystem byte limit and retains extension`() {
        val saved = ComputerArtifactStore(temp.newFolder()).save("ses_test", "文".repeat(80) + ".txt", "ok".toByteArray())
        assertTrue(saved.file.name.toByteArray(Charsets.UTF_8).size <= 255)
        assertEquals("txt", saved.file.extension)
        assertEquals("ok", saved.file.readText())
    }
}
