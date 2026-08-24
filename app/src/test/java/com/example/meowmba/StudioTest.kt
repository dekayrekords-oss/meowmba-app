package com.example.meowmba

import com.example.meowmba.core.agent.tools.CreateComicTool
import com.example.meowmba.core.agent.tools.GetCharacterTool
import com.example.meowmba.core.agent.tools.SaveCharacterTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.core.agent.tools.WriteScriptTool
import com.example.meowmba.core.database.CharacterDao
import com.example.meowmba.core.database.CharacterEntity
import com.example.meowmba.core.studio.ComicRenderer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** In-memory stand-in for Room so the studio tools stay plain-JVM testable. */
private class FakeCharacterDao : CharacterDao {
    val store = linkedMapOf<String, String>()
    private val flow = MutableStateFlow(emptyList<CharacterEntity>())

    override fun getAll(): Flow<List<CharacterEntity>> = flow
    override suspend fun getAllOnce(): List<CharacterEntity> =
        store.map { CharacterEntity(it.key, it.value) }
    override suspend fun getByName(name: String): CharacterEntity? =
        store[name]?.let { CharacterEntity(name, it) }
    override suspend fun upsert(character: CharacterEntity) {
        store[character.name] = character.sheet
        flow.value = getAllOnce()
    }
    override suspend fun deleteByName(name: String) { store.remove(name) }
    override suspend fun count(): Int = store.size
}

class StudioTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---- renderer ----

    @Test
    fun `slugify keeps alphanumerics and collapses spaces`() {
        assertEquals("meowmba_1", ComicRenderer.slugify("Meowmba #1!"))
        assertEquals("the_great_escape", ComicRenderer.slugify("  The   GREAT Escape "))
        assertEquals("untitled", ComicRenderer.slugify("???"))
        assertTrue(ComicRenderer.slugify("x".repeat(500)).length <= 48)
    }

    @Test
    fun `comic html renders panels dialogue and cast`() {
        val panels = """[
            {"caption":"night","dialogue":"I have an idea.","description":"cat at window"},
            {"caption":"boom"}
        ]"""
        val html = ComicRenderer.renderComicHtml("Meow #1", panels, mapOf("Meow" to "orange tabby #F59E2D"))
        assertTrue(html.contains("Meow #1"))
        assertTrue(html.contains("Panel") || html.contains(">1<"))
        assertTrue(html.contains("I have an idea."))
        assertTrue(html.contains("cat at window"))
        assertTrue(html.contains("CAST"))
        assertTrue(html.contains("#F59E2D"))
    }

    @Test
    fun `comic html escapes markup and survives garbage json`() {
        val evil = """[{"dialogue":"<script>alert('x')</script>"}]"""
        val html = ComicRenderer.renderComicHtml("T", evil)
        assertFalse(html.contains("<script>alert"))
        assertTrue(html.contains("&lt;script&gt;"))
        val junk = ComicRenderer.renderComicHtml("T", "not json at all")
        assertTrue(junk.contains("No usable panels"))
    }

    @Test
    fun `script html marks headings bullets and bold`() {
        val body = "# HOOK\nSay **this**\n- point one"
        val html = ComicRenderer.renderScriptHtml("Ad v1", "ad", body)
        assertTrue(html.contains("<h2>HOOK</h2>"))
        assertTrue(html.contains("<b>this</b>"))
        assertTrue(html.contains("point one"))
        assertTrue(html.contains(">ad<"))
    }

    // ---- tools ----

    @Test
    fun `save and get character roundtrip with honest miss`() = runTest {
        val dao = FakeCharacterDao()
        val save = SaveCharacterTool(dao)
        val get = GetCharacterTool(dao)

        val saved = save.execute(mapOf("name" to "Meow", "sheet" to "orange #F59E2D"))
        assertTrue(saved is ToolResult.Success)
        val got = get.execute(mapOf("name" to "meow")) as ToolResult.Failure
        // lookup is exact-name: case matters, and the failure lists what IS saved
        assertTrue(got.error.contains("No character named"))
        assertTrue(got.error.contains("Meow"))
        val hit = get.execute(mapOf("name" to "Meow")) as ToolResult.Success
        assertTrue(hit.output.contains("#F59E2D"))

        assertTrue(save.execute(mapOf("name" to "", "sheet" to "x")) is ToolResult.Failure)
        assertTrue(save.execute(mapOf("name" to "X", "sheet" to "")) is ToolResult.Failure)
    }

    @Test
    fun `create comic embeds known sheets and reports unknown names`() = runTest {
        val dao = FakeCharacterDao()
        dao.upsert(CharacterEntity("Meow", "locked sheet"))
        val tool = CreateComicTool(Workspace(tmp.newFolder("ws")), dao)

        val result = tool.execute(
            mapOf(
                "title" to "Test Comic",
                "panels" to """[{"dialogue":"hi"}]""",
                "characters" to "Meow, Nobody"
            )
        ) as ToolResult.Success

        assertTrue(result.output.contains("MEDIA_FILE:"))
        val path = result.output.lineSequence().first { it.startsWith("MEDIA_FILE:") }.removePrefix("MEDIA_FILE:")
        val file = File(path)
        assertTrue(file.exists())
        assertTrue(file.absolutePath.contains("comics"))
        assertTrue(file.readText().contains("locked sheet"))
        assertTrue(result.output.contains("Nobody"))
        assertFalse(result.output.contains("(none yet)"))
    }

    @Test
    fun `create comic rejects non-array panels`() = runTest {
        val tool = CreateComicTool(Workspace(tmp.newFolder("ws2")), FakeCharacterDao())
        val bad = tool.execute(mapOf("title" to "T", "panels" to "{\"dialogue\":\"hi\"}"))
        assertTrue(bad is ToolResult.Failure)
        assertNotNull((bad as ToolResult.Failure).error)
    }

    @Test
    fun `write script routes kinds to folders`() = runTest {
        val ws = Workspace(tmp.newFolder("ws3"))
        val tool = WriteScriptTool(ws)

        for ((kind, dir) in listOf("script" to "scripts", "ad" to "ads", "post" to "posts")) {
            val r = tool.execute(mapOf("title" to "Draft $kind", "body" to "# HOOK\nstuff", "kind" to kind)) as ToolResult.Success
            assertTrue(r.output.contains("MEDIA_FILE:"))
            assertTrue(File(r.output.substringAfter("MEDIA_FILE:")).parentFile!!.name == dir)
        }
        assertTrue(tool.execute(mapOf("title" to "Empty", "body" to "")) is ToolResult.Failure)
    }
}
