package com.example.meowmba

import com.example.meowmba.core.files.*
import com.example.meowmba.core.agent.tools.*
import com.example.meowmba.core.agent.runtime.*
import com.example.meowmba.ui.companion.CompanionState
import com.example.meowmba.ui.companion.faceModeActive
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

class AssetLibraryTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var library: AssetLibrary
    @Before fun setup() { library = AssetLibrary(temp.newFolder("workspace")) }
    private fun file(path: String, content: String = "hello") = library.resolve(path).apply { parentFile!!.mkdirs(); writeText(content) }

    @Test fun rejectsTraversalHiddenPathsUrlsAndRoot() {
        for (path in listOf("../key", "/etc/passwd", "", ".asset-trash/payload", "photos/../keys", "https://x/a", "a\\b", "a/./b"))
            assertThrows(IllegalArgumentException::class.java) { library.resolve(path) }
    }
    @Test fun neverFollowsSymlinks() {
        val outside = temp.newFolder("outside")
        java.nio.file.Files.createSymbolicLink(File(library.root, "escape").toPath(), outside.toPath())
        assertThrows(IllegalArgumentException::class.java) { library.resolve("escape/a.txt") }
        assertTrue(library.scan().entries.isEmpty())
    }
    @Test fun importsNeverOverwriteAndKeepOriginalStreamBytes() {
        val first = library.import("report.txt", "documents", "one".byteInputStream())
        val second = library.import("report.txt", "documents", "two".byteInputStream())
        assertNotEquals(first.path, second.path)
        assertEquals("one", library.resolve(first.path).readText())
        assertEquals("two", library.resolve(second.path).readText())
    }
    @Test fun scanCategorizesAndReportsLimit() {
        file("cat.png"); file("movie.mp4"); file("note.txt")
        assertEquals(setOf("Photos", "Videos", "Documents"), library.scan().entries.map { it.category }.toSet())
        assertTrue(library.scan(2).truncated)
        assertEquals(2, library.scan(2).entries.size)
    }
    @Test fun localSearchReturnsRealPaths() {
        file("photos/garden-cat.jpg"); file("documents/taxes.pdf")
        assertEquals("photos/garden-cat.jpg", library.search("garden photos").entries.single().path)
    }
    @Test fun trashRestoreRoundTripDoesNotChangeBytes() {
        val original = file("documents/test.txt")
        val id = library.trash(library.entry(original))
        assertFalse(original.exists()); assertTrue(library.scan().entries.none { !it.folder })
        assertEquals("documents/test.txt", library.trashItems().single().original)
        library.restore(id)
        assertEquals("hello", original.readText()); assertTrue(library.trashItems().isEmpty())
    }
    @Test fun restoreNeverOverwrites() {
        val original = file("test.txt")
        val id = library.trash(library.entry(original)); original.writeText("new")
        assertThrows(IllegalArgumentException::class.java) { library.restore(id) }
        assertEquals("new", original.readText()); assertEquals(1, library.trashItems().size)
    }
    @Test fun purgeOnlyDeletesTheExactTrashItem() {
        val keep = file("keep.txt", "safe")
        val id = library.trash(library.entry(file("delete.txt")))
        assertThrows(IllegalArgumentException::class.java) { library.purge("../keep.txt") }
        library.purge(id)
        assertTrue(library.trashItems().isEmpty()); assertEquals("safe", keep.readText())
    }
    @Test fun moveRejectsChangedSourceAndOverwrite() {
        val source = file("a.txt"); file("b.txt", "keep")
        val entry = library.entry(source)
        assertThrows(IllegalArgumentException::class.java) { library.move(AssetMove(entry, "b.txt")) }
        source.writeText("changed")
        assertThrows(IllegalArgumentException::class.java) { library.move(AssetMove(entry, "c.txt")) }
        assertEquals("keep", library.resolve("b.txt").readText())
    }
    @Test fun moveCreatesDestinationFolders() {
        val source = file("a.txt")
        library.move(AssetMove(library.entry(source), "documents/topic/a.txt"))
        assertFalse(source.exists()); assertEquals("hello", library.resolve("documents/topic/a.txt").readText())
    }
    @Test fun cleanupComparesBytesAndNeverChangesFiles() {
        file("a.txt", "same"); file("b.txt", "same"); file("c.txt", "diff"); file("empty.txt", "")
        val (candidates, _) = library.cleanup()
        assertEquals(setOf("b.txt", "empty.txt"), candidates.map { it.asset.path }.toSet())
        assertEquals(4, library.scan().entries.size)
    }
    @Test fun aiSearchRejectsInventedIds() {
        val entries = listOf(library.entry(file("test.txt")))
        assertThrows(IllegalArgumentException::class.java) { AssetAiPlanner.matches("{\"matches\":[9]}", entries) }
        assertEquals(entries, AssetAiPlanner.matches("{\"matches\":[0]}", entries))
    }
    @Test fun aiOrganizationCannotEscapeOverwriteOrChangeType() {
        val entries = listOf(library.entry(file("test.txt")))
        file("existing.txt")
        for (destination in listOf("../secret.txt", "existing.txt", "documents/test.apk", ".asset-trash/test.txt"))
            assertThrows(IllegalArgumentException::class.java) { AssetAiPlanner.moves("{\"moves\":[{\"id\":0,\"destination\":\"$destination\"}]}", entries, library) }
        assertEquals("documents/test.txt", AssetAiPlanner.moves("{\"moves\":[{\"id\":0,\"destination\":\"documents/test.txt\"}]}", entries, library).single().destination)
    }
    @Test fun aiNeverMovesProjectDependencies() {
        val entries = listOf(library.entry(file("projects/project-7/main.txt")))
        assertThrows(IllegalArgumentException::class.java) { AssetAiPlanner.moves("{\"moves\":[{\"id\":0,\"destination\":\"documents/main.txt\"}]}", entries, library) }
    }
    @Test fun aiPromptContainsMetadataNotContents() {
        val entry = library.entry(file("letter.txt", "private content"))
        val prompt = AssetAiPlanner.prompt("find letter", listOf(entry), false)
        assertTrue(prompt.contains("letter.txt")); assertFalse(prompt.contains("private content"))
    }
    @Test fun agentMutationsRequireApprovalAndToolsAreRegisteredToFileAgent() {
        for (action in listOf("move", "trash", "restore")) assertEquals(ConfirmationPolicy.ALWAYS, AssetTool(library, action).definition.confirmationPolicy)
        for (id in listOf("file.browser", "file.search", "file.cleanup", "file.move", "file.trash", "file.restore"))
            assertTrue(AgentRegistry.agents.first { it.id == "file" }.allowedTools.contains(id))
    }
    @Test fun commandRoutesAreDeterministic() {
        assertEquals("file.browser", AgentRouter.route("open files")?.toolId)
        assertEquals("file.search", AgentRouter.route("find files about garden")?.toolId)
        assertEquals("file.cleanup", AgentRouter.route("clean up my library")?.toolId)
        assertTrue(CommandHeuristics.mightBeCommand("clean up my library"))
        assertFalse(faceModeActive(CompanionState(showAssetBrowser = true)))
    }
    @Test fun mediaSavedInActiveProjectWithoutOverwriting() {
        val workspace = Workspace(library.root) { "projects/project-5" }
        val a = workspace.newAsset("photos", "cat.png")
        val b = workspace.newAsset("photos", "cat.png")
        assertTrue(a.path.contains("projects/project-5/photos")); assertNotEquals(a.path, b.path)
    }
    @Test fun searchToolReadsActualLibrary() = runBlocking {
        file("photos/cat.jpg")
        val result = AssetTool(library, "search").execute(mapOf("query" to "cat"))
        assertTrue(result is ToolResult.Success)
        assertTrue(result.toString().contains("photos/cat.jpg"))
    }
    @Test fun agentTextReaderIsBoundedAndRejectsMedia() = runBlocking {
        file("large.txt", "a".repeat(100_000)); file("video.mp4")
        val tool = FileReadTool(Workspace(library.root))
        assertTrue(tool.execute(mapOf("path" to "video.mp4")) is ToolResult.Failure)
        val result = tool.execute(mapOf("path" to "large.txt"))
        assertTrue(result is ToolResult.Success)
        assertTrue(result.toString().length < 4100)
        assertTrue(result.toString().contains("truncated"))
    }
}
