package com.example.meowmba

import com.example.meowmba.ui.companion.GeneratedAvatarMotion
import com.example.meowmba.ui.companion.GeneratedAvatarPart
import com.example.meowmba.ui.companion.GeneratedAvatarShape
import com.example.meowmba.ui.companion.GeneratedPropCategory
import com.example.meowmba.ui.companion.GeneratedPropForm
import com.example.meowmba.ui.companion.GeneratedPropForms
import com.example.meowmba.ui.companion.GeneratedPropStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeneratedPropStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun sample(name: String = "Paper umbrella"): GeneratedPropForm {
        val draft = GeneratedPropForm(
            id = "",
            name = name,
            description = "A tiny peach paper umbrella",
            category = GeneratedPropCategory.HELD,
            useHint = "Hold above head",
            parts = listOf(
                GeneratedAvatarPart(
                    GeneratedAvatarShape.ELLIPSE, 0f, -.05f, .42f, .22f, 0xFFF4C7A5,
                    motion = GeneratedAvatarMotion.STILL,
                ),
                GeneratedAvatarPart(
                    GeneratedAvatarShape.ROUND_RECT, 0f, .12f, .04f, .28f, 0xFF8B6A4A,
                    motion = GeneratedAvatarMotion.STILL,
                ),
            ),
            anchorX = 0f,
            anchorY = .22f,
        )
        return GeneratedPropForms.parse(GeneratedPropForms.toJson(draft))
    }

    @Test fun savedPropCanBeFoundByRequest() {
        val dir = temporary.newFolder()
        val form = sample()
        GeneratedPropStore(dir).save(form, "  Tiny   UMBRELLA! ")
        val restored = GeneratedPropStore(dir)
        assertEquals(form, restored.load(form.id))
        assertEquals(form, restored.find("tiny umbrella"))
        assertEquals(listOf(form), restored.list())
        assertTrue(dir.listFiles()!!.all { it.name.matches(Regex("[0-9a-f]{64}\\.json")) })
    }

    @Test fun fullLibraryDoesNotEvictSilently() {
        val dir = temporary.newFolder()
        val store = GeneratedPropStore(dir)
        val base = sample()
        val saved = (0 until GeneratedPropForms.MAX_SAVED).map { i ->
            val form = GeneratedPropForms.parse(GeneratedPropForms.toJson(base.copy(name = "Umbrella $i")))
            store.save(form, "umbrella $i"); form
        }
        assertThrows(IllegalStateException::class.java) { store.save(base, "umbrella") }
        assertEquals(GeneratedPropForms.MAX_SAVED, store.list().size)
        assertTrue(store.delete(saved.last().id))
        store.save(base, "umbrella")
        assertEquals(base, store.load(base.id))
        assertNull(store.load(saved.last().id))
    }
}
