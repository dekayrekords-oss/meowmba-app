package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.agent.ModelProvider
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.database.ProjectDao
import com.example.meowmba.core.database.ProjectEntity
import com.example.meowmba.core.work.ProjectWorkStore
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectCreationViewModelTest {
    @get:Rule val temp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<CompanionViewModel>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }

    private class Dao : ProjectDao {
        val rows = MutableStateFlow(listOf(ProjectEntity(1, "Alpha", "OTHER", conversationId = 100),
            ProjectEntity(2, "Beta", "OTHER", conversationId = 200)))
        var inserts = 0
        var beforeInsert: suspend () -> Unit = {}
        var beforeRead: suspend (Long) -> Unit = {}
        override fun getAll() = rows
        override suspend fun getById(id: Long): ProjectEntity? { beforeRead(id); return rows.value.find { it.id == id } }
        override suspend fun names() = rows.value.map { it.name }
        override suspend fun touch(id: Long, at: Long) = Unit
        override suspend fun insert(project: ProjectEntity): Long {
            beforeInsert()
            val id = (++inserts + 2).toLong()
            rows.value += project.copy(id = id)
            return id
        }
        override suspend fun update(project: ProjectEntity) { rows.value = rows.value.map { if (it.id == project.id) project else it } }
        override suspend fun deleteById(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
    }

    private class Planner : ModelProvider {
        override val name = "Deterministic plan fixture"
        var calls = 0
        var beforeReply: suspend () -> Unit = {}
        override suspend fun generate(prompt: String): ModelResponse {
            calls++; beforeReply()
            return ModelResponse.Complete("""{"steps":["Draft the brief","Review the saved result"]}""")
        }
        override fun generateStreaming(prompt: String) = flowOf(ModelResponse.Complete("I recorded the report."))
    }

    private fun vm(dao: Dao, planner: Planner, root: File = temp.newFolder(),
        tts: VoicePipelineTest.FakeSpeechSynthesizer = VoicePipelineTest.FakeSpeechSynthesizer()) =
        CompanionViewModel(elapsedRealtimeMs = { System.nanoTime() / 1_000_000L }, workspaceRoot = root, projectDao = dao, ioDispatcher = dispatcher, ttsManager = tts)
            .also { it.currentProvider = planner; models += it }

    @Test fun duplicateCreationKeepsOneRecordOnePlanAndOneConfirmation() = runTest {
        val dao = Dao(); val planner = Planner(); val root = temp.newFolder()
        val tts = VoicePipelineTest.FakeSpeechSynthesizer(); val vm = vm(dao, planner, root, tts)
        runCurrent()
        vm.setSpeechMode(com.example.meowmba.ui.companion.SpeechMode.ALWAYS) // this test checks the spoken confirmation
        vm.createProject("Launch", "Plan the launch"); runCurrent()
        vm.createProject("Launch", "Plan the launch"); runCurrent()
        assertEquals(1, dao.inserts)
        assertEquals(1, planner.calls)
        assertEquals(1, tts.spokenTexts.count { it.startsWith("Your project Launch is ready") })
        assertEquals(listOf("Draft the brief", "Review the saved result"), ProjectWorkStore(root).load(3).steps.map { it.title })
        assertEquals(3L, vm.uiState.value.activeProjectId)
    }

    @Test fun repeatedCreationWhilePlanWaitsDoesNotCancelOrRestartTheOriginalPlan() = runTest {
        val releasePlan = CompletableDeferred<Unit>()
        val planner = Planner().apply { beforeReply = { releasePlan.await() } }
        val dao = Dao(); val root = temp.newFolder(); val vm = vm(dao, planner, root)
        runCurrent(); vm.createProject("Launch", "Plan the launch"); runCurrent()
        assertTrue(vm.uiState.value.projectWorkBusy)
        vm.createProject("Launch", "Plan the launch"); runCurrent()
        assertEquals(1, dao.inserts); assertEquals(1, planner.calls)
        assertTrue("Original planning should still own busy state", vm.uiState.value.projectWorkBusy)
        releasePlan.complete(Unit); runCurrent()
        assertEquals(2, ProjectWorkStore(root).load(3).steps.size)
        assertFalse(vm.uiState.value.projectWorkBusy)
    }

    @Test fun cancellationDuringInsertAndRetryRepairsTheSameRecordWithoutAnAutomaticPlan() = runTest {
        val started = CompletableDeferred<Unit>(); val releaseInsert = CompletableDeferred<Unit>()
        val dao = Dao().apply { beforeInsert = { started.complete(Unit); releaseInsert.await() } }
        val planner = Planner(); val root = temp.newFolder(); val vm = vm(dao, planner, root)
        runCurrent(); vm.createProject("Launch", "Plan the launch"); runCurrent()
        assertTrue(started.isCompleted)
        vm.cancelProjectPlanning()
        // Even a retry before the committed insert returns cannot queue a new insert.
        vm.createProject("Launch", "Plan the launch"); runCurrent()
        releaseInsert.complete(Unit); runCurrent()
        assertNull(vm.uiState.value.activeProjectId)
        vm.createProject("Launch", "Plan the launch"); runCurrent()
        assertEquals(1, dao.inserts); assertEquals(0, planner.calls)
        assertEquals(3L, vm.uiState.value.activeProjectId)
        assertEquals("Plan the launch", ProjectWorkStore(root).load(3).objective)
        assertFalse(vm.uiState.value.projectWorkBusy)
    }

    @Test fun switchingProjectsDuringInsertCannotLetTheOldCreationStealTheScreen() = runTest {
        val releaseInsert = CompletableDeferred<Unit>(); val releaseNavigation = CompletableDeferred<Unit>()
        val dao = Dao().apply {
            beforeInsert = { releaseInsert.await() }
            beforeRead = { if (it == 2L) releaseNavigation.await() }
        }
        val planner = Planner(); val vm = vm(dao, planner)
        runCurrent(); vm.openProject(1); runCurrent()
        vm.createProject("Launch", "Plan the launch"); runCurrent()
        vm.openProject(2); runCurrent()
        releaseInsert.complete(Unit); runCurrent()
        assertEquals("Navigation intention must invalidate creation before Room returns", 1L, vm.uiState.value.activeProjectId)
        assertEquals(0, planner.calls)
        releaseNavigation.complete(Unit); runCurrent()
        assertEquals(2L, vm.uiState.value.activeProjectId)
        assertEquals("Beta", vm.uiState.value.activeProjectName)
        assertEquals(1, dao.inserts)
    }

    @Test fun bugReportsAndSpokenConfirmationsDoNotAllocateProjectsThroughRealTextRouting() = runTest {
        val dao = Dao(); val vm = vm(dao, Planner())
        runCurrent()
        listOf(
            "it keeps starting projects until i close the app",
            "Started Website. Its plan activity and files are together in Projects. I'm drafting a plan for you to review.",
            "do not start a project",
        ).forEach { vm.onUserTextSubmitted(it); runCurrent() }
        assertEquals(0, dao.inserts)
        assertEquals(2, dao.rows.value.size)
        assertNull(vm.uiState.value.activeProjectId)
    }

    @Test fun leavingProjectDuringCreationPreventsLateSelectionOrDrafting() = runTest {
        val releaseInsert = CompletableDeferred<Unit>()
        val dao = Dao().apply { beforeInsert = { releaseInsert.await() } }
        val planner = Planner(); val vm = vm(dao, planner)
        runCurrent(); vm.createProject("Launch", "Plan the launch"); runCurrent()
        vm.leaveProject(); releaseInsert.complete(Unit); runCurrent()
        assertNull(vm.uiState.value.activeProjectId)
        assertEquals(0L, vm.uiState.value.activeConversationId)
        assertEquals(0, planner.calls)
        assertFalse(vm.uiState.value.projectWorkBusy)
    }
}
