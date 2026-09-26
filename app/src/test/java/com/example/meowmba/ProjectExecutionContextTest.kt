package com.example.meowmba

import com.example.meowmba.core.agent.runtime.*
import com.example.meowmba.core.agent.tools.*
import com.example.meowmba.core.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectExecutionContextTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun generatedAssetsStayInTheSubmittedProjectAcrossDispatcherAndUiChanges() = runTest {
        var active = "projects/project-1"
        val ws = Workspace(temp.root) { active }
        val asset = withContext(ProjectExecutionContext.element(1, 10)) {
            active = "projects/project-2"
            withContext(Dispatchers.Default) { ws.newAsset("photos", "cat.png") }
        }
        assertTrue(asset.path.contains("projects/project-1/photos/"))
        assertNull(ProjectExecutionContext.projectId)
        assertTrue(ws.newAsset("photos", "other.png").path.contains("project-2/photos/"))
    }

    @Test fun nonProjectTasksDoNotBorrowAnOpenProjectsFolder() = runTest {
        val ws = Workspace(temp.root) { "projects/project-9" }
        val asset = withContext(ProjectExecutionContext.element(null)) { ws.newAsset("documents", "note.txt") }
        assertEquals(File(temp.root, "documents").canonicalFile, asset.parentFile.canonicalFile)
    }

    @Test fun textWriteUsesCapturedProjectAndReturnsTheRealArtifactPath() = runTest {
        val ws = Workspace(temp.root)
        val result = withContext(ProjectExecutionContext.element(3)) {
            FileWriteTool(ws).execute(mapOf("path" to "documents/brief.md", "content" to "A real brief"))
        } as ToolResult.Success
        val file = File(temp.root, "projects/project-3/documents/brief.md")
        assertEquals("A real brief", file.readText())
        assertTrue(result.output.contains("MEDIA_FILE:${file.absolutePath}"))
        assertFalse(File(temp.root, "documents/brief.md").exists())
    }

    @Test fun scopedWritesCannotEscapeOrOverwriteAnotherProject() = runTest {
        val ws = Workspace(temp.root)
        withContext(ProjectExecutionContext.element(3)) {
            assertFalse(ws.write("../elsewhere.txt", "bad"))
            assertFalse(ws.write("projects/project-4/note.txt", "bad"))
            assertTrue(ws.write("projects/project-3/note.txt", "good"))
        }
    }

    @Test fun approvalKeepsOriginalProjectAndNeverWritesUntilApproved() = runTest {
        val ws = Workspace(temp.root)
        val registry = ToolRegistry().apply { register(FileWriteTool(ws, kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))) }
        val runner = AgentOrchestrator(backgroundScope, registry, object : AuditSink {
            override suspend fun log(action: String, agentId: String, taskId: String?, detail: String) = Unit
        })
        val submit = runner.submit("file", "file.write", mapOf("path" to "brief.md", "content" to "Saved"), "Write brief", 7, 700) as SubmitResult.Started
        runCurrent()
        assertEquals(TaskStatus.WAITING_APPROVAL, runner.tasks.value.first().status)
        assertFalse(File(temp.root, "projects/project-7/brief.md").exists())
        withContext(ProjectExecutionContext.element(8, 800)) { runner.resolveApproval(true) }
        runCurrent()
        val task = runner.tasks.value.first { it.id == submit.taskId }
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertEquals(7L, task.projectId)
        assertEquals(700L, task.conversationId)
        assertTrue(File(temp.root, "projects/project-7/brief.md").isFile)
        assertFalse(File(temp.root, "projects/project-8/brief.md").exists())
    }
}
