package com.example.meowmba

import com.example.meowmba.core.vault.VaultRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `slash titles land in a subfolder and still list`() {
        val vault = VaultRepository(tmp.newFolder("vault"))
        vault.append("Notes/the meeting", "hello")
        val note = vault.get("Notes/the meeting")!!
        assertEquals("Notes/the meeting", note.title)
        assertTrue(note.body.contains("hello"))
        assertTrue(vault.list().any { it.title == "Notes/the meeting" })
        vault.append("Notes/the meeting", "second")
        assertTrue(vault.get("Notes/the meeting")!!.body.contains("second"))
    }
}
