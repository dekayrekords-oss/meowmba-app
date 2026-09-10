package com.example.meowmba

import com.example.meowmba.core.agent.LocalModelImport
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream

class LocalModelImportTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun completeImportIsSavedAndStagingIsRemoved() {
        val dir = temporary.newFolder()
        val file = LocalModelImport.copy(byteArrayOf(1, 2, 3).inputStream(), dir, "model.task", 3)
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
        assertEquals(listOf("model.task"), dir.list()!!.toList())
    }
    @Test fun existingModelIsNeverOverwritten() {
        val dir = temporary.newFolder()
        val old = File(dir, "model.task").apply { writeBytes(byteArrayOf(1, 2)) }
        assertTrue(runCatching { LocalModelImport.copy(byteArrayOf(3).inputStream(), dir, "model.task") }.isFailure)
        assertArrayEquals(byteArrayOf(1, 2), old.readBytes())
    }
    @Test fun emptyTruncatedOversizedAndTraversalFilesFailWithoutInstall() {
        for ((name, bytes, expected) in listOf(Triple("empty.task", byteArrayOf(), null),
            Triple("truncated.task", byteArrayOf(1), 4L), Triple("../outside.task", byteArrayOf(1), null),
            Triple("weights.gguf", byteArrayOf(1), null), Triple("huge.task", byteArrayOf(1), LocalModelImport.MAX_BYTES + 1))) {
            val dir = temporary.newFolder()
            assertTrue(name, runCatching { LocalModelImport.copy(bytes.inputStream(), dir, name, expected) }.isFailure)
            assertTrue(dir.listFiles()!!.isEmpty())
        }
    }
    @Test fun failingInputDoesNotLeavePartialModel() {
        val dir = temporary.newFolder()
        val broken = object : InputStream() { override fun read(): Int = throw java.io.IOException("picker disconnected") }
        assertTrue(runCatching { LocalModelImport.copy(broken, dir, "model.task") }.isFailure)
        assertTrue(dir.listFiles()!!.isEmpty())
    }
    @Test fun fileAppearingDuringImportIsNotOverwritten() {
        val dir = temporary.newFolder()
        val target = File(dir, "model.task")
        val input = object : java.io.ByteArrayInputStream(byteArrayOf(1, 2)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!target.exists()) target.writeBytes(byteArrayOf(9))
                return super.read(b, off, len)
            }
        }
        assertTrue(runCatching { LocalModelImport.copy(input, dir, "model.task") }.isFailure)
        assertArrayEquals(byteArrayOf(9), target.readBytes())
        assertEquals(listOf("model.task"), dir.list()!!.toList())
    }
}
