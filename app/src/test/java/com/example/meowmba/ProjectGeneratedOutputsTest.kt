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

class ProjectGeneratedOutputsTest {
    @get:Rule val temp = TemporaryFolder()

    private fun tools(workspace: Workspace): List<Pair<Tool, String>> = listOf(
        CreateComicTool(workspace) to "comics",
        WriteScriptTool(workspace) to "scripts",
        EmailDraftTool(workspace) to "documents",
    )

    private fun args(body: String) = mapOf("title" to "Shared title", "subject" to "Shared title",
        "to" to "review@example.com", "body" to body,
        "panels" to "[{\"caption\":\"$body\",\"description\":\"Original scene\"}]")

    private fun artifact(result: ToolResult): File {
        assertTrue(result.toString(), result is ToolResult.Success)
        val path = (result as ToolResult.Success).output.lineSequence()
            .first { it.startsWith("MEDIA_FILE:") }.removePrefix("MEDIA_FILE:")
        return File(path).also { assertTrue("An actual nonempty result is required", it.isFile && it.length() > 0L) }
    }

    @Test fun `same titles in two projects preserve distinct comic script and email content`() = runTest {
        tools(Workspace(temp.root)).forEach { (tool, folder) ->
            val first = withContext(ProjectExecutionContext.element(1, 10)) { artifact(tool.execute(args("First project"))) }
            val second = withContext(ProjectExecutionContext.element(2, 20)) { artifact(tool.execute(args("Second project"))) }
            assertEquals(File(temp.root, "projects/project-1/$folder").canonicalFile, first.parentFile.canonicalFile)
            assertEquals(File(temp.root, "projects/project-2/$folder").canonicalFile, second.parentFile.canonicalFile)
            assertNotEquals(first.canonicalFile, second.canonicalFile)
            assertTrue(first.readText().contains("First project"))
            assertFalse(first.readText().contains("Second project"))
            assertTrue(second.readText().contains("Second project"))
        }
    }

    @Test fun `repeating a title within one project creates a new output without overwriting`() = runTest {
        withContext(ProjectExecutionContext.element(3)) {
            tools(Workspace(temp.root)).forEach { (tool, _) ->
                val first = artifact(tool.execute(args("First draft")))
                val original = first.readBytes()
                val second = artifact(tool.execute(args("Revised draft")))
                assertNotEquals(first.canonicalFile, second.canonicalFile)
                assertArrayEquals(original, first.readBytes())
                assertTrue(second.readText().contains("Revised draft"))
            }
        }
    }

    @Test fun `captured project survives UI project switch and worker dispatcher`() = runTest {
        var selected = "projects/project-4"
        val ws = Workspace(temp.root) { selected }
        withContext(ProjectExecutionContext.element(4)) {
            selected = "projects/project-9"
            tools(ws).forEach { (tool, folder) ->
                val file = withContext(Dispatchers.Default) { artifact(tool.execute(args("Captured project"))) }
                assertEquals(File(temp.root, "projects/project-4/$folder").canonicalFile, file.parentFile.canonicalFile)
            }
        }
        assertFalse(File(temp.root, "projects/project-9").exists())
    }

    @Test fun `explicit nonproject task never borrows currently open project folder`() = runTest {
        val ws = Workspace(temp.root) { "projects/project-9" }
        withContext(ProjectExecutionContext.element(null, 0)) {
            tools(ws).forEach { (tool, folder) ->
                val file = artifact(tool.execute(args("Ordinary conversation")))
                assertEquals(File(temp.root, folder).canonicalFile, file.parentFile.canonicalFile)
            }
        }
        assertFalse(File(temp.root, "projects").exists())
    }

    @Test fun `reservation failure reports failure and preserves the obstructing file`() = runTest {
        val obstruction = File(temp.root, "projects").apply { writeText("Existing file must remain") }
        val ws = Workspace(temp.root)
        withContext(ProjectExecutionContext.element(5)) {
            tools(ws).forEach { (tool, _) ->
                assertTrue(tool.execute(args("Cannot save")) is ToolResult.Failure)
            }
        }
        assertEquals("Existing file must remain", obstruction.readText())
    }

    @Test fun `script kinds retain their categories and drafts report actual reserved names`() = runTest {
        val ws = Workspace(temp.root)
        withContext(ProjectExecutionContext.element(6)) {
            mapOf("script" to "scripts", "ad" to "ads", "post" to "posts").forEach { (kind, folder) ->
                val file = artifact(WriteScriptTool(ws).execute(args("Authored text") + ("kind" to kind)))
                assertEquals(File(temp.root, "projects/project-6/$folder").canonicalFile, file.parentFile.canonicalFile)
            }
            val result = EmailDraftTool(ws).execute(args("Review before sending")) as ToolResult.Success
            val file = artifact(result)
            assertTrue(result.output.contains("\"${file.name}\""))
            assertTrue(file.readText().contains("To: review@example.com"))
            assertTrue(file.readText().contains("Subject: Shared title"))
        }
    }
}
