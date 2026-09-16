package com.example.meowmba

import com.example.meowmba.core.database.ProjectDao
import com.example.meowmba.core.database.ProjectEntity
import com.example.meowmba.core.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectCreationServiceTest {
    @get:Rule val temp = TemporaryFolder()

    private class Dao : ProjectDao {
        val rows = MutableStateFlow<List<ProjectEntity>>(emptyList())
        var inserts = 0
        var hideReads = false
        var beforeInsert: suspend () -> Unit = {}
        var afterInsert: () -> Unit = {}
        override fun getAll() = rows
        override suspend fun getById(id: Long) = if (hideReads) null else rows.value.find { it.id == id }
        override suspend fun names() = rows.value.map { it.name }
        override suspend fun touch(id: Long, at: Long) = Unit
        override suspend fun insert(project: ProjectEntity): Long {
            beforeInsert()
            val saved = project.copy(id = (++inserts).toLong())
            rows.value += saved
            afterInsert()
            return saved.id
        }
        override suspend fun update(project: ProjectEntity) { rows.value = rows.value.map { if (it.id == project.id) project else it } }
        override suspend fun deleteById(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
    }

    private val request = ProjectStart("Launch", ProjectKind.OTHER)
    private fun workspace() = File(temp.newFolder(), "workspace").also { it.mkdirs() }

    @Test fun duplicateFinalTranscriptsReuseTheVerifiedRecordAndNeverDraftAgain() = runTest {
        val dao = Dao(); val work = ProjectWorkStore(workspace())
        val service = ProjectCreationService(dao, work, StandardTestDispatcher(testScheduler))
        val first = service.create(request, "Create a launch plan") as ProjectCreationService.Result.Ready
        val next = service.create(request.copy(name = "  launch "), "create  a launch plan") as ProjectCreationService.Result.Ready
        assertTrue(first.created); assertFalse(next.created)
        assertEquals(first.project.id, next.project.id)
        assertEquals(1, dao.inserts)
        assertEquals("Create a launch plan", work.load(first.project.id).objective)
    }

    @Test fun concurrentRequestsAreNotQueuedBehindTheCurrentInsert() = runTest {
        val dao = Dao(); val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        dao.beforeInsert = { started.complete(Unit); release.await() }
        val service = ProjectCreationService(dao, ProjectWorkStore(workspace()), StandardTestDispatcher(testScheduler))
        val first = async { service.create(request, "Launch") }; started.await()
        assertEquals(ProjectCreationService.Result.Busy, service.create(request, "Launch"))
        assertEquals(ProjectCreationService.Result.Busy, service.create(request.copy(name = "Different"), "Other"))
        release.complete(Unit); first.await()
        assertEquals(1, dao.inserts)
    }

    @Test fun cancellingAfterDbCommitRetainsTheIdForASingleWorkspaceRepair() = runTest {
        val dao = Dao(); val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        dao.beforeInsert = { started.complete(Unit); release.await() }
        val work = ProjectWorkStore(workspace())
        val service = ProjectCreationService(dao, work, StandardTestDispatcher(testScheduler))
        val first = launch { service.create(request, "Launch") }; started.await()
        first.cancel(); release.complete(Unit); first.join()
        assertEquals(1, dao.inserts)
        val next = service.create(request, "Launch") as ProjectCreationService.Result.Ready
        assertFalse(next.created); assertEquals(1, dao.inserts)
        assertEquals("Launch", work.load(next.project.id).objective)
    }

    @Test fun partialDiskFailureIsRetriedOnTheSameRecordEvenAfterTheRepeatWindow() = runTest {
        var time = 1000L; val root = workspace(); val dao = Dao()
        val projectFolder = File(root, "projects").also { it.writeText("unavailable directory") }
        val service = ProjectCreationService(dao, ProjectWorkStore(root), StandardTestDispatcher(testScheduler), now = { time })
        assertTrue(runCatching { service.create(request, "Launch") }.isFailure)
        assertEquals(1, dao.inserts)
        projectFolder.delete(); projectFolder.mkdirs(); time += 120_000
        val next = service.create(request, "Launch") as ProjectCreationService.Result.Ready
        assertFalse(next.created); assertEquals(1, dao.inserts)
    }

    @Test fun distinctRequestsHaveDistinctConversationsEvenInOneClockTick() = runTest {
        val dao = Dao(); val service = ProjectCreationService(dao, ProjectWorkStore(workspace()), StandardTestDispatcher(testScheduler), now = { 1000L })
        val first = service.create(request, "Launch") as ProjectCreationService.Result.Ready
        val next = service.create(request, "Different objective") as ProjectCreationService.Result.Ready
        assertEquals("Launch 2", next.project.name)
        assertNotEquals(first.project.conversationId, next.project.conversationId)
        assertEquals(2, dao.inserts)
    }

    @Test fun savedRecordIsRequiredBeforeReturningSuccess() = runTest {
        val dao = Dao().apply { hideReads = true }
        val service = ProjectCreationService(dao, ProjectWorkStore(workspace()), StandardTestDispatcher(testScheduler))
        val outcome = runCatching { service.create(request, "Launch") }
        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull()!!.message!!.contains("verified"))
    }

    @Test fun expiredSuccessfulRequestCanIntentionallyCreateANewWorkspace() = runTest {
        var time = 1000L; val dao = Dao()
        val service = ProjectCreationService(dao, ProjectWorkStore(workspace()), StandardTestDispatcher(testScheduler), now = { time })
        service.create(request, "Launch"); time += 60_001
        val next = service.create(request, "Launch") as ProjectCreationService.Result.Ready
        assertTrue(next.created); assertEquals("Launch 2", next.project.name)
    }

    @Test fun staleScopeAndInvalidRequestsCannotAllocateAnything() = runTest {
        val dao = Dao(); val service = ProjectCreationService(dao, ProjectWorkStore(workspace()), StandardTestDispatcher(testScheduler))
        assertEquals(ProjectCreationService.Result.Superseded, service.create(request, "Launch", isCurrent = { false }))
        assertTrue(runCatching { service.create(request.copy(name = " "), "Launch") }.isFailure)
        assertTrue(runCatching { service.create(request, "x".repeat(2001)) }.isFailure)
        assertEquals(0, dao.inserts)
    }

    @Test fun restartAfterCommittedInsertButBeforeReceiptWriteRecoversTheSameProject() = runTest {
        val root = workspace(); val dao = Dao()
        dao.afterInsert = { error("Simulated process loss after Room commit") }
        val first = ProjectCreationService(dao, ProjectWorkStore(root), StandardTestDispatcher(testScheduler))
        assertTrue(runCatching { first.create(request, "Launch") }.isFailure)
        assertEquals(1, dao.inserts)
        dao.afterInsert = {}
        val restarted = ProjectCreationService(dao, ProjectWorkStore(root), StandardTestDispatcher(testScheduler))
        val result = restarted.create(request, "Launch") as ProjectCreationService.Result.Ready
        assertFalse(result.created); assertEquals(1L, result.project.id); assertEquals(1, dao.inserts)
    }

    @Test fun restartAfterWorkspaceFailureNeverAllocatesAnotherRow() = runTest {
        val root = workspace(); val dao = Dao(); val blocked = File(root, "projects").also { it.writeText("blocked") }
        val first = ProjectCreationService(dao, ProjectWorkStore(root), StandardTestDispatcher(testScheduler), now = { 1000L })
        assertTrue(runCatching { first.create(request, "Launch") }.isFailure)
        blocked.delete()
        val restarted = ProjectCreationService(dao, ProjectWorkStore(root), StandardTestDispatcher(testScheduler), now = { 999999L })
        assertFalse((restarted.create(request, "Launch") as ProjectCreationService.Result.Ready).created)
        assertEquals(1, dao.inserts)
    }

    @Test fun completedRepeatAcrossRestartPreservesConversation() = runTest {
        val root = workspace(); val dao = Dao()
        val first = ProjectCreationService(dao, ProjectWorkStore(root), StandardTestDispatcher(testScheduler), now = { 1000L })
            .create(request, "Launch") as ProjectCreationService.Result.Ready
        val next = ProjectCreationService(dao, ProjectWorkStore(root), StandardTestDispatcher(testScheduler), now = { 1001L })
            .create(request, "Launch") as ProjectCreationService.Result.Ready
        assertFalse(next.created); assertEquals(first.project.conversationId, next.project.conversationId)
    }

    @Test fun corruptJournalCannotSilentlyBecomeANewCreation() = runTest {
        val root = workspace(); val dao = Dao()
        File(root.parentFile, "project_state").mkdirs()
        File(root.parentFile, "project_state/allocations.json").writeText("broken receipt")
        val result = runCatching {
            ProjectCreationService(dao, ProjectWorkStore(root), StandardTestDispatcher(testScheduler)).create(request, "Launch")
        }
        assertTrue(result.isFailure); assertEquals(0, dao.inserts)
    }
}
