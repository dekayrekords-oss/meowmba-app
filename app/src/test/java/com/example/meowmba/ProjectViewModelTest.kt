package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.agent.*
import com.example.meowmba.core.database.*
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectViewModelTest {
    @get:Rule val temp = TemporaryFolder()
    private val dispatcher = UnconfinedTestDispatcher()
    private val vms = mutableListOf<CompanionViewModel>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { vms.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private class Dao : ProjectDao {
        val projects = MutableStateFlow(listOf(ProjectEntity(1, "Alpha", "OTHER", conversationId = 100), ProjectEntity(2, "Beta", "OTHER", conversationId = 200)))
        override fun getAll() = projects
        override suspend fun getById(id: Long) = projects.value.firstOrNull { it.id == id }
        override suspend fun names() = projects.value.map { it.name }
        override suspend fun touch(id: Long, at: Long) = Unit
        override suspend fun insert(project: ProjectEntity): Long { projects.value += project.copy(id = 3); return 3 }
        override suspend fun update(project: ProjectEntity) = Unit
        override suspend fun deleteById(id: Long) { projects.value = projects.value.filterNot { it.id == id } }
    }
    private fun vm(root: File = temp.newFolder()) = CompanionViewModel(workspaceRoot = root, projectDao = Dao(), ioDispatcher = dispatcher,
        ttsManager = VoicePipelineTest.FakeSpeechSynthesizer()).also { vms += it }

    @Test fun switchingProjectsRejectsAnOldModelsLateToolProposal() = runTest {
        val vm = vm()
        val answer = CompletableDeferred<Unit>()
        var routing = false
        vm.currentProvider = object : ModelProvider {
            override val name = "Test planner"
            override suspend fun generate(prompt: String): ModelResponse {
                routing = true
                withContext(NonCancellable) { answer.await() }
                return ModelResponse.Complete("""{"tool":"file.write","args":{"path":"wrong.md","content":"Must not run"}}""")
            }
            override fun generateStreaming(prompt: String) = flowOf(ModelResponse.Complete("No task"))
        }
        vm.openProject(1); runCurrent()
        vm.onUserTextSubmitted("make a helpful file for this project")
        runCurrent(); assertTrue(routing)
        vm.openProject(2); runCurrent()
        answer.complete(Unit); runCurrent()
        assertEquals(2L, vm.uiState.value.activeProjectId)
        assertTrue(vm.uiState.value.activeTasks.isEmpty())
        assertNull(vm.uiState.value.pendingApproval)
    }

    @Test fun stepButtonWorksWithMicrophoneKilledAndKeepsApprovalGate() = runTest {
        val vm = vm()
        vm.openProject(1); runCurrent()
        vm.setMicEnabled(false)
        vm.currentProvider = LlmToolCallRouterTest.FakeModelProvider("""{"tool":"file.write","args":{"path":"brief.md","content":"Hello"}}""")
        vm.continueProject("Draft a welcome brief")
        runCurrent()
        assertFalse(vm.uiState.value.isMicEnabled)
        assertEquals("file.write", vm.uiState.value.activeTasks.first().toolId)
        assertEquals(1L, vm.uiState.value.activeTasks.first().projectId)
        assertNotNull(vm.uiState.value.pendingApproval)
        assertTrue(vm.uiState.value.pendingApproval!!.detail.contains("Project #1"))
    }
    @Test fun stepReadsThenWritesUsingActualToolResultAndRequiresApproval() = runTest {
        val root = temp.newFolder()
        val vm = vm(root)
        vm.openProject(1); runCurrent()
        val source = File(root, "projects/project-1/source.md")
        source.parentFile.mkdirs(); source.writeText("Verified source sentence")
        val prompts = mutableListOf<String>()
        vm.currentProvider = object : ModelProvider {
            override val name = "Sequenced planner"
            override suspend fun generate(prompt: String): ModelResponse {
                prompts += prompt
                return ModelResponse.Complete(when (prompts.size) {
                    1 -> """{"tool":"file.read","args":{"path":"source.md"}}"""
                    2 -> """{"tool":"file.write","args":{"path":"result.md","content":"Verified result"}}"""
                    else -> "No more actions"
                })
            }
            override fun generateStreaming(prompt: String) = flowOf(ModelResponse.Complete("Unused"))
        }
        vm.continueProject("Read source.md and save a useful result")
        runCurrent()
        assertEquals(2, prompts.size)
        assertTrue(prompts.last().contains("Verified source sentence"))
        assertNotNull(vm.uiState.value.pendingApproval)
        assertFalse(File(source.parentFile, "result.md").exists())
        vm.resolveApproval(true); runCurrent()
        assertEquals("Verified result", File(source.parentFile, "result.md").readText())
        assertFalse(vm.uiState.value.projectExecuting)
        assertTrue(vm.uiState.value.projectWorkMessage!!.contains("Review"))
    }

    @Test fun cancelledRunCannotClearANewRunInTheSameProject() = runTest {
        val vm = vm()
        vm.openProject(1); runCurrent()
        val oldAnswer = CompletableDeferred<Unit>()
        var calls = 0
        vm.currentProvider = object : ModelProvider {
            override val name = "Delayed planner"
            override suspend fun generate(prompt: String): ModelResponse {
                if (++calls == 1) {
                    withContext(NonCancellable) { oldAnswer.await() }
                    return ModelResponse.Complete("No action")
                }
                return ModelResponse.Complete("""{"tool":"file.write","args":{"path":"new.md","content":"New run"}}""")
            }
            override fun generateStreaming(prompt: String) = flowOf(ModelResponse.Complete("Unused"))
        }
        vm.continueProject("Old step"); runCurrent()
        vm.cancelProjectPlanning()
        vm.continueProject("New step"); runCurrent()
        val approval = vm.uiState.value.pendingApproval
        assertNotNull(approval)
        oldAnswer.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.projectExecuting)
        assertEquals(approval, vm.uiState.value.pendingApproval)
        assertTrue(vm.uiState.value.activeTasks.any { !it.isTerminal })
        vm.cancelProjectPlanning(); runCurrent()
    }

    @Test fun stoppingProjectExecutionCancelsItsPendingAction() = runTest {
        val vm = vm()
        vm.openProject(1); runCurrent()
        vm.currentProvider = LlmToolCallRouterTest.FakeModelProvider("""{"tool":"file.write","args":{"path":"brief.md","content":"Hello"}}""")
        vm.continueProject("Draft a welcome brief"); runCurrent()
        assertNotNull(vm.uiState.value.pendingApproval)
        vm.cancelProjectPlanning(); runCurrent()
        assertNull(vm.uiState.value.pendingApproval)
        assertFalse(vm.uiState.value.projectWorkBusy)
        assertFalse(vm.uiState.value.activeTasks.any { !it.isTerminal })
    }

}
