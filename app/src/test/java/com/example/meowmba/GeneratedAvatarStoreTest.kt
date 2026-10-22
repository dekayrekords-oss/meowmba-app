package com.example.meowmba

import com.example.meowmba.ui.companion.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeneratedAvatarStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun savedFormCanBeReplayedAfterProcessRestartByRequestNameOrId() {
        val dir = temporary.newFolder(); val form = GeneratedAvatarExamples.coffeeCup()
        GeneratedAvatarStore(dir).save(form, "  My   COFFEE friend! ")
        val restored = GeneratedAvatarStore(dir)
        assertEquals(form, restored.load(form.id))
        assertEquals(form, restored.find("my coffee friend"))
        assertEquals(form, restored.find(form.name))
        assertEquals(form, restored.find(form.description))
        assertEquals(listOf(form), restored.list())
        assertTrue(dir.listFiles()!!.all { it.name.matches(Regex("[0-9a-f]{64}\\.json")) })
    }

    @Test fun repeatedSavesKeepRequestAliasesWithoutDuplicatingTheForm() {
        val dir = temporary.newFolder(); val store = GeneratedAvatarStore(dir); val form = GeneratedAvatarExamples.dragon()
        store.save(form, "a dragon"); store.save(form, "my winged companion")
        assertEquals(form, store.find("a dragon")); assertEquals(form, store.find("my winged companion"))
        assertEquals(1, store.list().size)
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test fun corruptFilesAndMismatchedContentCannotReplaceAValidSelection() {
        val dir = temporary.newFolder(); val store = GeneratedAvatarStore(dir); val form = GeneratedAvatarExamples.spaceship()
        store.save(form, "space ship")
        File(dir, "0".repeat(64) + ".json").writeText("broken")
        File(dir, "1".repeat(64) + ".json").writeText(File(dir, form.id + ".json").readText())
        File(dir, "leftover.pending").writeText("interrupted save")
        assertEquals(listOf(form), store.list()); assertEquals(form, store.load(form.id))
        assertNull(store.load("0".repeat(64))); assertNull(store.load("1".repeat(64)))
        assertThrows(IllegalArgumentException::class.java) { store.save(form.copy(name = "Tampered"), "new") }
        assertEquals(form, store.load(form.id))
    }

    @Test fun fullLibraryDoesNotSilentlyEvictTheCurrentAvatar() {
        val dir = temporary.newFolder(); val store = GeneratedAvatarStore(dir); val base = GeneratedAvatarExamples.coffeeCup()
        val saved = (0 until GeneratedAvatarForms.MAX_SAVED).map { i ->
            val form = GeneratedAvatarForms.parse(GeneratedAvatarForms.toJson(base.copy(name = "Coffee $i")))
            store.save(form, "coffee $i"); form
        }
        assertThrows(IllegalStateException::class.java) { store.save(base, "coffee") }
        saved.forEach { assertEquals(it, store.load(it.id)) }
        store.save(saved.first(), "first favorite")
        assertEquals(GeneratedAvatarForms.MAX_SAVED, store.list().size)
        assertTrue(store.delete(saved.last().id)); store.save(base, "coffee")
        assertEquals(base, store.load(base.id)); assertNull(store.load(saved.last().id))
    }

    @Test fun descriptionsAndNamesCannotBecomePaths() {
        val parent = temporary.newFolder(); val dir = File(parent, "forms"); val store = GeneratedAvatarStore(dir)
        val sentinel = File(parent, "sentinel").apply { writeText("preserve") }
        val base = GeneratedAvatarExamples.coffeeCup()
        val named = GeneratedAvatarForms.parse(GeneratedAvatarForms.toJson(base.copy(name = "../../sentinel")))
        store.save(named, "../../sentinel")
        assertEquals("preserve", sentinel.readText()); assertEquals(named, store.find("../../sentinel"))
        assertNull(store.load("../sentinel")); assertFalse(store.delete("../sentinel"))
        store.save(base, "x".repeat(GeneratedAvatarForms.MAX_REQUEST))
        assertThrows(IllegalArgumentException::class.java) { store.save(base, "x".repeat(GeneratedAvatarForms.MAX_REQUEST + 1)) }
    }

    @Test fun deletingOneFormPreservesTheOthers() {
        val store = GeneratedAvatarStore(temporary.newFolder())
        val a = GeneratedAvatarExamples.dragon(); val b = GeneratedAvatarExamples.spaceship()
        store.save(a, "dragon"); store.save(b, "spaceship")
        assertTrue(store.delete(a.id)); assertFalse(store.delete(a.id))
        assertNull(store.find("dragon")); assertEquals(b, store.load(b.id))
    }
}
