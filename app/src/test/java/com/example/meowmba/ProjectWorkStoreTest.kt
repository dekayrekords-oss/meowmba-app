package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentTask
import com.example.meowmba.core.agent.runtime.TaskStatus
import com.example.meowmba.core.files.AssetLibrary
import com.example.meowmba.core.work.ProjectTaskStatus
import com.example.meowmba.core.work.ProjectWorkStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ProjectWorkStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var root: File
    private lateinit var metadata: File
    private lateinit var store: ProjectWorkStore
    private var tick = 100L

    @Before fun setup() {
        root = temp.newFolder("workspace")
        metadata = temp.newFolder("project_state")
        store = instance()
    }

    private fun instance(runtime: String = "runtime-one") =
        ProjectWorkStore(root, metadata, now = { ++tick }, runtimeId = runtime)

    private fun task(id: String = "task", status: TaskStatus = TaskStatus.QUEUED,
                     result: String? = null, projectId: Long? = 7L, toolId: String = "file.write") =
        AgentTask(id = id, agentId = "file", toolId = toolId, title = "Write project notes",
            args = mapOf("private-argument" to "never saved"), status = status, createdAt = 10L,
            completedAt = if (status in setOf(TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED)) ++tick else null,
            result = result, projectId = projectId)

    private fun file(path: String, content: String = "actual bytes"): File = File(root, path).apply {
        parentFile!!.mkdirs()
        writeText(content)
    }

    @Test fun lateModelDraftCannotOverwriteAnEditedBrief() {
        val original = store.ensure(7, "Launch", "OTHER", "Make a guide")
        store.savePlan(7, "Make a poster", listOf("Design the poster"), "Landscape")
        assertTrue(runCatching { store.saveDraftIfUnchanged(original, listOf("Write a guide")) }.isFailure)
        assertEquals("Make a poster", store.load(7).objective)
        assertEquals("Landscape", store.load(7).notes)
    }

    @Test fun createsOneStableHomeAndKeepsMetadataOutsideFiles() {
        val saved = store.ensure(7, "  Garden book  ", "WRITING", "A short garden book")
        assertEquals("Garden book", saved.name)
        assertEquals("projects/project-7", saved.outputFolder)
        assertTrue(File(root, saved.outputFolder).isDirectory)
        assertTrue(File(metadata, "project-7.json").isFile)
        assertTrue(AssetLibrary(root).scan().entries.none { it.name == "project-7.json" })
        assertEquals(saved, instance().load(7))
    }

    @Test fun planChecksSurviveRestartButNeverBecomeObservedToolCompletion() {
        store.ensure(7, "Garden book", "WRITING")
        val proposal = store.savePlan(7, "Write the book", listOf("Outline", "Write chapters"), "Use a friendly tone")
        val checked = store.setStepDone(7, proposal.steps.first().id, true)
        assertEquals(1, checked.remainingStepCount)
        assertEquals(0, checked.completedTaskCount)
        assertEquals(checked, instance("after-restart").load(7))
        val reordered = store.savePlan(7, "Write the book", listOf("Write chapters", "Outline"), "Use a friendly tone")
        assertEquals(proposal.steps.first().id, reordered.steps.last().id)
        assertTrue(reordered.steps.last().completedByUser)
        store.recordTask(7, task(status = TaskStatus.COMPLETED, result = "Wrote an outline"))
        val observed = store.load(7)
        assertEquals(1, observed.completedTaskCount)
        assertFalse(observed.steps.first().completedByUser)
    }

    @Test fun aNewObjectiveCannotInheritCompletedChecksByGenericTitle() {
        val first = store.savePlan(7, "Write a book", listOf("Research"))
        store.setStepDone(7, first.steps.single().id, true)
        val changed = store.savePlan(7, "Build an app", listOf("Research"))
        assertFalse(changed.steps.single().completedByUser)
        assertNotEquals(first.steps.single().id, changed.steps.single().id)
    }

    @Test fun restartInterruptsAllActiveStatesWithoutReplayingOrCompletingThem() {
        listOf(TaskStatus.QUEUED, TaskStatus.RUNNING, TaskStatus.WAITING_APPROVAL, TaskStatus.COMPLETED,
            TaskStatus.FAILED, TaskStatus.CANCELLED).forEach {
            store.recordTask(7, task(id = it.name, status = it, result = if (it == TaskStatus.FAILED) "Network failed" else null))
        }
        val recovered = instance("after-restart").load(7)
        assertTrue(recovered.currentTasks.isEmpty())
        assertEquals(3, recovered.tasks.count { it.status == ProjectTaskStatus.INTERRUPTED })
        assertEquals(1, recovered.completedTaskCount)
        assertEquals("Network failed", recovered.tasks.single { it.id == "FAILED" }.resultSummary)
        assertTrue(recovered.tasks.filter { it.status == ProjectTaskStatus.INTERRUPTED }
            .all { it.resultSummary.contains("external action may already have happened") })
        assertEquals(recovered, instance("another-restart").load(7))
    }

    @Test fun recreatingStoreInSameRuntimeDoesNotInterruptLiveWork() {
        val running = store.recordTask(7, task(status = TaskStatus.RUNNING))
        assertEquals(running, instance().load(7))
        val one = ProjectWorkStore(root, metadata)
        one.recordTask(8, task(id = "other", projectId = 8, status = TaskStatus.WAITING_APPROVAL))
        assertEquals(ProjectTaskStatus.WAITING_APPROVAL, ProjectWorkStore(root, metadata).load(8).tasks.single().status)
    }

    @Test fun delayedSnapshotsCannotDowngradeObservedTerminalOrRequeueStartedWork() {
        val running = store.recordTask(7, task(status = TaskStatus.RUNNING))
        assertEquals(running, store.recordTask(7, task(status = TaskStatus.QUEUED)))
        val completed = store.recordTask(7, task(status = TaskStatus.COMPLETED, result = "Saved"))
        assertEquals(completed, store.recordTask(7, task(status = TaskStatus.RUNNING)))
        assertEquals(completed, store.recordTask(7, task(status = TaskStatus.WAITING_APPROVAL)))
        assertEquals(completed, store.recordTask(7, task(status = TaskStatus.QUEUED)))
    }

    @Test fun taskBindingCannotBeReassignedToNewlyOpenedProject() {
        store.ensure(7, "One", "WRITING")
        store.ensure(8, "Two", "CODING")
        assertThrows(IllegalArgumentException::class.java) { store.recordTask(8, task()) }
        store.recordTask(7, task(status = TaskStatus.COMPLETED, result = "Saved"))
        assertEquals(1, store.load(7).tasks.size)
        assertTrue(store.load(8).tasks.isEmpty())
        // Existing callers without a binding can explicitly choose the saved project.
        assertEquals(1, store.recordTask(8, task(projectId = null)).tasks.size)
    }

    @Test fun scanningIsProjectScopedAndNeverReadsFileContents() {
        file("projects/project-7/photos/cover.png", "private image payload")
        file("projects/project-7/draft.md", "private document payload")
        file("projects/project-8/other.txt")
        file("documents/global.txt")
        file("projects/project-7/.hidden.txt")
        val outside = temp.newFile("outside.txt").apply { writeText("outside payload") }
        Files.createSymbolicLink(File(root, "projects/project-7/linked.txt").toPath(), outside.toPath())
        val snapshot = store.snapshot(7)
        assertEquals(setOf("projects/project-7/photos/cover.png", "projects/project-7/draft.md"), snapshot.artifacts.map { it.path }.toSet())
        assertTrue(snapshot.artifacts.all { it.available && it.inProjectFolder })
        assertEquals(setOf("Photos", "Documents"), snapshot.artifacts.map { it.category }.toSet())
        val checkpoint = File(metadata, "project-7.json").readText()
        assertFalse(checkpoint.contains("private image payload"))
        assertFalse(checkpoint.contains("private document payload"))
    }

    @Test fun trustedOutputElsewhereInFilesIsReferencedWithoutMovingOrCopying() {
        val original = file("documents/legacy-draft.txt")
        store.ensure(7, "Book", "WRITING")
        store.recordTask(7, task(status = TaskStatus.COMPLETED, result = "Saved draft\nMEDIA_FILE:${original.absolutePath}"))
        val artifact = store.snapshot(7).artifacts.single()
        assertEquals("documents/legacy-draft.txt", artifact.path)
        assertTrue(artifact.available)
        assertFalse(artifact.inProjectFolder)
        assertEquals("actual bytes", original.readText())
        assertTrue(File(root, "projects/project-7").listFiles().orEmpty().isEmpty())
        assertEquals(artifact, instance("restart").snapshot(7).artifacts.single())
    }

    @Test fun markersFromReadersUnfinishedTasksAndOutsideWorkspaceAreNotArtifacts() {
        val original = file("documents/private.txt")
        val marker = "MEDIA_FILE:${original.absolutePath}"
        assertTrue(store.recordTask(7, task(id = "read", status = TaskStatus.COMPLETED, toolId = "file.read", result = marker)).artifacts.isEmpty())
        assertTrue(store.recordTask(7, task(id = "mcp", status = TaskStatus.COMPLETED, toolId = "mcp.untrusted", result = marker)).artifacts.isEmpty())
        assertTrue(store.recordTask(7, task(id = "running", status = TaskStatus.RUNNING, result = marker)).artifacts.isEmpty())
        val outside = temp.newFile("private-outside.txt").apply { writeText("not a workspace asset") }
        val rejected = store.recordTask(7, task(id = "outside", status = TaskStatus.COMPLETED, result = "MEDIA_FILE:${outside.absolutePath}"))
        assertTrue(rejected.artifacts.isEmpty())
        assertTrue(rejected.warnings.any { it.contains("outside") })
        assertFalse(File(metadata, "project-7.json").readText().contains(outside.absolutePath))
    }

    @Test fun missingAndEmptyArtifactsAreNotShownAsAvailableAndCanReappear() {
        val output = file("projects/project-7/draft.txt")
        store.snapshot(7)
        assertTrue(output.delete())
        assertFalse(store.snapshot(7).artifacts.single().available)
        output.writeText("")
        assertFalse(store.snapshot(7).artifacts.single().available)
        output.writeText("returned")
        assertTrue(store.snapshot(7).artifacts.single().available)
    }

    @Test fun boundedScanReportsPartialResultsWithoutInventedEntries() {
        repeat(ProjectWorkStore.MAX_ARTIFACTS + 1) { file("projects/project-7/file-$it.txt") }
        val snapshot = store.snapshot(7)
        assertEquals(ProjectWorkStore.MAX_ARTIFACTS, snapshot.artifacts.size)
        assertTrue(snapshot.scanTruncated)
        assertTrue(snapshot.artifacts.all { File(root, it.path).isFile })
    }

    @Test fun boundedHistoryRetainsActiveTasksAndReportsOmissions() {
        store.recordTask(7, task(id = "still-running", status = TaskStatus.RUNNING))
        repeat(ProjectWorkStore.MAX_TASKS) { store.recordTask(7, task(id = "finished-$it", status = TaskStatus.COMPLETED)) }
        val saved = store.load(7)
        assertEquals(ProjectWorkStore.MAX_TASKS, saved.tasks.size)
        assertEquals("still-running", saved.currentTasks.single().id)
        assertTrue(saved.tasks.any { it.id == "finished-${ProjectWorkStore.MAX_TASKS - 1}" })
        assertTrue(saved.warnings.any { it.contains("observed task records") })
    }

    @Test fun taskArgumentsAndArtifactMarkersAreNotPersistedInResultSummaries() {
        val output = file("projects/project-7/result.txt")
        val saved = store.recordTask(7, task(status = TaskStatus.COMPLETED,
            result = "Saved the result\nMEDIA_FILE:${output.absolutePath}"))
        assertEquals("Saved the result", saved.tasks.single().resultSummary)
        assertFalse(File(metadata, "project-7.json").readText().contains("private-argument"))
        assertFalse(File(metadata, "project-7.json").readText().contains("never saved"))
        val bounded = store.recordTask(7, task(id = "large", status = TaskStatus.COMPLETED, result = "x".repeat(50_000)))
        assertEquals(ProjectWorkStore.MAX_RESULT_SUMMARY, bounded.tasks.first().resultSummary.length)
    }

    @Test fun invalidInputsCannotOverwriteExistingCheckpoint() {
        store.savePlan(7, "Safe objective", listOf("Safe step"), "Safe notes")
        val checkpoint = File(metadata, "project-7.json")
        val before = checkpoint.readText()
        assertThrows(IllegalArgumentException::class.java) { store.savePlan(7, "x".repeat(ProjectWorkStore.MAX_OBJECTIVE + 1), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { store.savePlan(7, "Safe", listOf(" ")) }
        assertThrows(IllegalArgumentException::class.java) { store.savePlan(7, "Safe", List(ProjectWorkStore.MAX_STEPS + 1) { "Step" }) }
        assertThrows(IllegalArgumentException::class.java) { store.setStepDone(7, "missing", true) }
        assertThrows(IllegalArgumentException::class.java) { store.recordTask(7, task(id = "")) }
        assertThrows(IllegalArgumentException::class.java) { store.load(0) }
        assertThrows(IllegalArgumentException::class.java) { ProjectWorkStore.outputFolder(-1) }
        assertEquals(before, checkpoint.readText())
    }

    @Test fun metadataInsideWorkspaceOrLinkedCheckpointIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ProjectWorkStore(root, File(root, "progress")) }
        assertThrows(IllegalArgumentException::class.java) { ProjectWorkStore(root, root) }
        val destination = temp.newFile("do-not-overwrite.txt").apply { writeText("keep") }
        Files.createSymbolicLink(File(metadata, "project-7.json").toPath(), destination.toPath())
        assertThrows(IllegalArgumentException::class.java) { store.load(7) }
        assertThrows(IllegalArgumentException::class.java) { store.ensure(7, "Book", "WRITING") }
        assertEquals("keep", destination.readText())
    }

    @Test fun corruptAndOversizeProgressIsReportedWithoutReplacingIt() {
        val checkpoint = File(metadata, "project-7.json")
        checkpoint.writeText("{invalid saved data")
        val error = assertThrows(IllegalStateException::class.java) { store.load(7) }
        assertTrue(error.message.orEmpty().contains("existing data was kept"))
        assertThrows(IllegalStateException::class.java) { store.ensure(7, "Book", "WRITING") }
        assertEquals("{invalid saved data", checkpoint.readText())
        checkpoint.writeText("x".repeat(ProjectWorkStore.MAX_CHECKPOINT_BYTES + 1))
        assertThrows(IllegalStateException::class.java) { store.load(7) }
        assertEquals(ProjectWorkStore.MAX_CHECKPOINT_BYTES + 1L, checkpoint.length())
    }

    @Test fun unchangedObservationsDoNotWriteANewCheckpointAndNoPendingFilesRemain() {
        val input = task(status = TaskStatus.RUNNING)
        val first = store.recordTask(7, input)
        assertEquals(first.updatedAt, store.recordTask(7, input).updatedAt)
        val planned = store.savePlan(7, "Objective", listOf("Step"))
        assertEquals(planned.updatedAt, store.savePlan(7, "Objective", listOf("Step")).updatedAt)
        assertEquals(listOf("project-7.json"), metadata.listFiles().orEmpty().map { it.name })
    }
}
