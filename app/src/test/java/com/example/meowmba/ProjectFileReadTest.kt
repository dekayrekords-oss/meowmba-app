package com.example.meowmba

import com.example.meowmba.core.agent.tools.*
import com.example.meowmba.core.work.ProjectExecutionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ProjectFileReadTest {
    @get:Rule val temp = TemporaryFolder()
    private fun file(path: String, content: String) = File(temp.root, path).apply {
        parentFile.mkdirs(); writeText(content)
    }
    private suspend fun read(ws: Workspace, path: String): String =
        (FileReadTool(ws).execute(mapOf("path" to path)) as ToolResult.Success).output

    @Test fun relativeReadFindsExactlyTheFileWrittenInTheCapturedProject() = runTest {
        var selected = "projects/project-9"
        val ws = Workspace(temp.root) { selected }
        file("brief.md", "Unrelated old global brief")
        withContext(ProjectExecutionContext.element(1, 10)) {
            assertTrue(FileWriteTool(ws).execute(mapOf("path" to "brief.md", "content" to "Current project brief")) is ToolResult.Success)
            selected = "projects/project-2"
            assertEquals("Current project brief", withContext(Dispatchers.Default) { read(ws, "brief.md") })
            assertEquals("Current project brief", ws.read("brief.md"))
            assertEquals("Unrelated old global brief", read(ws, "/brief.md"))
        }
    }

    @Test fun sharedAndOtherProjectReferencesRemainReadableByTheirPaths() = runTest {
        val ws = Workspace(temp.root)
        file("imports/reference.md", "Imported research")
        file("projects/project-2/reference.md", "Explicit other-project reference")
        withContext(ProjectExecutionContext.element(1)) {
            assertEquals("Imported research", read(ws, "imports/reference.md"))
            assertEquals("Explicit other-project reference", read(ws, "projects/project-2/reference.md"))
            assertTrue(ws.write("documents/note.md", "Scoped nested text"))
            assertEquals("Scoped nested text", read(ws, "documents/note.md"))
            assertEquals("Scoped nested text", read(ws, "projects/project-1/documents/note.md"))
        }
    }

    @Test fun listShowsOnlyCapturedProjectAndReturnsUsableFullPaths() = runTest {
        val ws = Workspace(temp.root) { "projects/project-2" }
        file("global.md", "Global")
        file("projects/project-1/brief.md", "One")
        file("projects/project-2/brief.md", "Two")
        withContext(ProjectExecutionContext.element(1)) {
            val result = FileListTool(ws).execute(emptyMap()) as ToolResult.Success
            assertEquals("projects/project-1/brief.md", result.output)
            assertEquals("One", read(ws, result.output))
            assertFalse(result.output.contains("project-2"))
            assertFalse(result.output.contains("global.md"))
        }
        withContext(ProjectExecutionContext.element(null)) {
            val result = FileListTool(ws).execute(emptyMap()) as ToolResult.Success
            assertTrue(result.output.contains("global.md"))
            assertTrue(result.output.contains("projects/project-1/brief.md"))
            assertEquals("Global", read(ws, "global.md"))
        }
    }

    @Test fun projectListingsKeepHiddenAndLinkedPathsExcludedAndReportPartialResults() = runTest {
        val ws = Workspace(temp.root)
        file("projects/project-1/public.md", "Visible")
        file("projects/project-1/.asset-trash/payload", "Hidden")
        val target = file("projects/project-2/secret.md", "Other project")
        Files.createSymbolicLink(File(temp.root, "projects/project-1/linked.md").toPath(), target.toPath())
        withContext(ProjectExecutionContext.element(1)) {
            val paths = ws.scanFiles().entries.map { it.path }
            assertEquals(listOf("projects/project-1/public.md"), paths)
            repeat(55) { file("projects/project-1/item-$it.md", "item") }
            assertTrue((FileListTool(ws).execute(emptyMap()) as ToolResult.Success).output.contains("Partial listing"))
        }
    }

    @Test fun missingProjectListDoesNotScanTheWholeLibraryAndEscapesStillFail() = runTest {
        val ws = Workspace(temp.root)
        file("global.md", "Unrelated")
        withContext(ProjectExecutionContext.element(3)) {
            assertTrue(ws.scanFiles().entries.isEmpty())
            assertNull(ws.resolveRead("../../outside.md"))
            assertTrue(FileReadTool(ws).execute(mapOf("path" to "missing.md")) is ToolResult.Failure)
        }
        assertFalse(File(temp.root, "projects/project-3").exists())
    }
}
