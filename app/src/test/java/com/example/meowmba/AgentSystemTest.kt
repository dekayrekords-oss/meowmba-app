package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentOrchestrator
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.runtime.AuditSink
import com.example.meowmba.core.agent.runtime.SubmitResult
import com.example.meowmba.core.agent.runtime.TaskStatus
import com.example.meowmba.core.agent.tools.FileDeleteTool
import com.example.meowmba.core.agent.tools.FileReadTool
import com.example.meowmba.core.agent.tools.FileWriteTool
import com.example.meowmba.core.agent.tools.ToolRegistry
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.ui.companion.CompanionViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class WorkspaceTest {

    private lateinit var root: java.io.File
    private lateinit var workspace: Workspace

    @Before
    fun setup() {
        root = Files.createTempDirectory("memba_ws_test").toFile()
        workspace = Workspace(root)
    }

    @Test
    fun `legitimate paths resolve inside the sandbox`() {
        assertNotNull(workspace.resolve("notes.txt"))
        assertNotNull(workspace.resolve("projects/demo/README.md"))
    }

    @Test
    fun `traversal and escape attempts are rejected`() {
        assertNull(workspace.resolve("../secrets.txt"))
        assertNull(workspace.resolve("../../etc/passwd"))
        assertNull(workspace.resolve("projects/../../escape.txt"))
        assertNull(workspace.resolve("   "))
    }

    @Test
    fun `absolute paths are tamed into the sandbox, never outside`() {
        val resolved = workspace.resolve("/etc/passwd")
        assertTrue(resolved!!.canonicalPath.startsWith(root.canonicalPath))
    }

    @Test
    fun `write, read, list and delete roundtrip`() {
        assertTrue(workspace.write("a/b.txt", "hello"))
        assertEquals("hello", workspace.read("a/b.txt"))
        assertTrue(workspace.list().contains("a/b.txt"))
        assertTrue(workspace.delete("a/b.txt"))
        assertNull(workspace.read("a/b.txt"))
    }

    @Test
    fun `non-empty directories cannot be deleted`() {
        workspace.write("proj/file.txt", "x")
        assertFalse(workspace.delete("proj"))
        assertTrue(workspace.delete("proj/file.txt"))
        assertTrue(workspace.delete("proj"))
    }
}

class AgentRouterTest {

    @Test
    fun `file intents`() {
        val create = AgentRouter.route("create file called notes.txt")!!
        assertEquals("file", create.agentId)
        assertEquals("file.write", create.toolId)
        assertEquals("notes.txt", create.args["path"])

        val delete = AgentRouter.route("delete file old.txt")!!
        assertEquals("file.delete", delete.toolId)

        val read = AgentRouter.route("read the file notes.txt")!!
        assertEquals("file.read", read.toolId)
        assertEquals("notes.txt", read.args["path"])
    }

    @Test
    fun `project, memory and task intents`() {
        val project = AgentRouter.route("create a project called Website")!!
        assertEquals("project", project.agentId)
        assertEquals("project.create", project.toolId)
        assertEquals("Website", project.args["name"])

        val search = AgentRouter.route("search memory for green tea")!!
        assertEquals("memory.search", search.toolId)
        assertEquals("green tea", search.args["query"])

        val tasks = AgentRouter.route("what tasks are running")!!
        assertEquals("task.list", tasks.toolId)
    }

    @Test
    fun `ordinary conversation is not routed`() {
        assertNull(AgentRouter.route("what's the weather like"))
        assertNull(AgentRouter.route("tell me a joke"))
    }

    @Test
    fun `avatar performance intents reach the face, never the video tool`() {
        // The reported failure: "it makes the animation but does not show me —
        // it makes a file instead." Every one of these must land on avatar.animate.
        listOf("do a trick", "show me a trick", "do an animation", "show me an animation",
            "animate yourself", "wiggle your ears").forEach { said ->
            val intent = AgentRouter.route(said)!!
            assertEquals(said, "avatar.animate", intent.toolId)
            assertEquals(said, "main", intent.agentId)
        }

        val dance = AgentRouter.route("dance")!!
        assertEquals("avatar.animate", dance.toolId)
        assertEquals("happy bop", dance.args["name"])

        val replay = AgentRouter.route("play the ear wiggle animation")!!
        assertEquals("avatar.animate", replay.toolId)
        assertEquals("ear wiggle", replay.args["name"])
    }

    @Test
    fun `a video of something is still a video`() {
        // Only the bare "animate " prefix was removed — video-of-X stays media.
        val intent = AgentRouter.route("make an animation of a cat chasing a laser")!!
        assertEquals("media.generate_video", intent.toolId)
        assertEquals("media", intent.agentId)
    }

    @Test
    fun `play without an animation suffix is not an avatar command`() {
        assertNull(AgentRouter.route("play the piano"))
        assertNull(AgentRouter.route("play something"))
    }

    @Test
    fun `describe-animation routes land on avatar describe_animation`() {
        listOf("be happy", "look happy", "smile", "cheer up").forEach { said ->
            val intent = AgentRouter.route(said)!!
            assertEquals(said, "avatar.describe_animation", intent.toolId)
            assertEquals(said, "main", intent.agentId)
        }
        val sad = AgentRouter.route("look sad")!!
        assertEquals("avatar.describe_animation", sad.toolId)
        assertEquals("look sad", sad.args["description"])

        val angry = AgentRouter.route("get angry")!!
        assertEquals("avatar.describe_animation", angry.toolId)

        val scared = AgentRouter.route("look scared")!!
        assertEquals("avatar.describe_animation", scared.toolId)

        val sleepy = AgentRouter.route("look sleepy")!!
        assertEquals("avatar.describe_animation", sleepy.toolId)

        val purr = AgentRouter.route("purr")!!
        assertEquals("avatar.describe_animation", purr.toolId)

        val jump = AgentRouter.route("jump")!!
        assertEquals("avatar.describe_animation", jump.toolId)

        val laugh = AgentRouter.route("laugh")!!
        assertEquals("avatar.describe_animation", laugh.toolId)

        // "spin" moved from the face to the BODY on user request ("it should
        // spin in a circle if I tell it to"). A face cannot spin; the LOOI base
        // can, and that is what a person asking for it means. The expressive
        // motions the face CAN do — nod, shake head, wiggle — stay with the
        // avatar, which drives the servos anyway via FaceMotorMap.
        val spin = AgentRouter.route("spin")!!
        assertEquals("robot.move", spin.toolId)
        assertEquals("spin", spin.args["action"])
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OrchestratorTest {

    private data class AuditRecord(val action: String, val detail: String)

    private lateinit var workspace: Workspace
    private lateinit var auditRecords: MutableList<AuditRecord>
    private lateinit var auditSink: AuditSink

    @Before
    fun setup() {
        workspace = Workspace(Files.createTempDirectory("memba_orch_test").toFile())
        auditRecords = mutableListOf()
        auditSink = object : AuditSink {
            override suspend fun log(action: String, agentId: String, taskId: String?, detail: String) {
                auditRecords.add(AuditRecord(action, detail))
            }
        }
    }

    private fun registry(scope: TestScope): ToolRegistry = ToolRegistry().apply {
        register(FileReadTool(workspace))
        register(FileWriteTool(workspace, UnconfinedTestDispatcher(scope.testScheduler)))
        register(FileDeleteTool(workspace))
    }

    // Unconfined scope sharing the test scheduler: launched tasks run eagerly,
    // while advanceUntilIdle() still controls timeouts and delays.
    private fun orchestrator(scope: TestScope) = AgentOrchestrator(
        TestScope(UnconfinedTestDispatcher(scope.testScheduler)),
        registry(scope),
        auditSink
    )

    @Test
    fun `approval flow - gated tool waits, approval completes the task`() = runTest {
        val orch = orchestrator(this)
        val result = orch.submit("file", "file.write", mapOf("path" to "n.txt", "content" to "hi"), "create file n.txt")
        assertTrue(result is SubmitResult.Started)

        // Task is parked at the approval gate
        val waiting = orch.tasks.value.first()
        assertEquals(TaskStatus.WAITING_APPROVAL, waiting.status)
        assertNotNull(orch.pendingApproval.value)
        assertFalse(workspace.resolve("n.txt")!!.exists()) // nothing written yet

        orch.resolveApproval(true)
        advanceUntilIdle()

        assertEquals(TaskStatus.COMPLETED, orch.tasks.value.first().status)
        assertEquals("hi", workspace.read("n.txt"))
        assertTrue(auditRecords.any { it.action == "task.approved" })
        assertTrue(auditRecords.any { it.action == "task.complete" })
    }

    @Test
    fun `denial fails the task and nothing is written`() = runTest {
        val orch = orchestrator(this)
        orch.submit("file", "file.write", mapOf("path" to "n.txt", "content" to "hi"), "create file n.txt")
        orch.resolveApproval(false)
        advanceUntilIdle()

        assertEquals(TaskStatus.FAILED, orch.tasks.value.first().status)
        assertEquals("Denied by user", orch.tasks.value.first().result)
        assertFalse(workspace.resolve("n.txt")!!.exists())
    }

    @Test
    fun `approval timeout auto-denies`() = runTest {
        val orch = orchestrator(this)
        orch.submit("file", "file.delete", mapOf("path" to "n.txt"), "delete file n.txt")
        assertNotNull(orch.pendingApproval.value)

        advanceUntilIdle() // virtual time passes the 60s approval timeout

        assertNull(orch.pendingApproval.value)
        assertEquals(TaskStatus.FAILED, orch.tasks.value.first().status)
        assertTrue(auditRecords.any { it.action == "task.approval_timeout" })
    }

    @Test
    fun `concurrency limit queues extra tasks and drains on completion`() = runTest {
        val orch = orchestrator(this)
        // file agent allows 2 concurrent tasks; all three hit the approval gate
        orch.submit("file", "file.write", mapOf("path" to "1.txt", "content" to "a"), "t1")
        orch.submit("file", "file.write", mapOf("path" to "2.txt", "content" to "b"), "t2")
        orch.submit("file", "file.write", mapOf("path" to "3.txt", "content" to "c"), "t3")

        val statuses = orch.tasks.value.associate { it.title to it.status }
        assertEquals(TaskStatus.QUEUED, statuses["t3"])

        // runCurrent (not advanceUntilIdle) keeps virtual time frozen so the
        // approval timeouts don't fire while we approve one gate at a time.
        orch.resolveApproval(true)  // t1
        runCurrent()                // t1 completes; t3 takes the freed slot
        orch.resolveApproval(true)  // t2
        runCurrent()

        // t3 got its slot and its own approval request surfaced
        assertEquals(TaskStatus.WAITING_APPROVAL, orch.tasks.value.first { it.title == "t3" }.status)
        assertNotNull(orch.pendingApproval.value)
        orch.resolveApproval(true)
        runCurrent()

        assertTrue(orch.tasks.value.all { it.status == TaskStatus.COMPLETED })
    }

    @Test
    fun `cancellation stops a waiting task`() = runTest {
        val orch = orchestrator(this)
        val result = orch.submit("file", "file.delete", mapOf("path" to "x"), "delete x") as SubmitResult.Started
        orch.cancel(result.taskId)
        advanceUntilIdle()

        assertEquals(TaskStatus.CANCELLED, orch.tasks.value.first().status)
        assertNull(orch.pendingApproval.value)
    }

    @Test
    fun `permission and availability rules reject honestly`() = runTest {
        val orch = orchestrator(this)
        assertTrue(orch.submit("memory", "file.write", emptyMap(), "x") is SubmitResult.Rejected) // wrong agent
        assertTrue(orch.submit("email", "file.write", emptyMap(), "x") is SubmitResult.Rejected) // unavailable agent
        assertTrue(orch.submit("file", "shell.exec", emptyMap(), "x") is SubmitResult.Rejected)  // no such tool
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgentViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeTts: VoicePipelineTest.FakeSpeechSynthesizer
    private lateinit var androidMocks: ScopedCompanionAndroidMocks
    private val viewModels = mutableListOf<CompanionViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Completed tools now feed the live expression/reflex pipeline before
        // speech. Its Android clock must be supplied in this plain-JVM fixture.
        androidMocks = ScopedCompanionAndroidMocks { testDispatcher.scheduler.currentTime }
        fakeTts = VoicePipelineTest.FakeSpeechSynthesizer()
    }

    @After
    fun tearDown() = runTest(testDispatcher) {
        try {
            viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        } finally {
            viewModels.clear()
            androidMocks.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `voice command to create a file runs the full approval loop end to end`() = runTest {
        val workspaceRoot = Files.createTempDirectory("memba_vm_ws").toFile()
        val vm = CompanionViewModel(ttsManager = fakeTts, workspaceRoot = workspaceRoot,
            ioDispatcher = testDispatcher).also { viewModels += it }

        vm.setSpeechMode(com.example.meowmba.ui.companion.SpeechMode.ALWAYS) // this test checks the spoken confirmation
        vm.onUserTextSubmitted("create file called hello.txt")
        runCurrent() // settle synchronous work without tripping the approval timeout

        // Approval card surfaced in state
        val approval = vm.uiState.value.pendingApproval
        assertNotNull(approval)
        assertTrue(vm.uiState.value.activeTasks.isNotEmpty())

        vm.resolveApproval(true)
        runCurrent()

        // File written, task completed, audit recorded, completion spoken
        assertTrue(java.io.File(workspaceRoot, "hello.txt").exists())
        assertEquals(TaskStatus.COMPLETED, vm.uiState.value.activeTasks.first().status)
        assertTrue(vm.uiState.value.auditEntries.isNotEmpty())
        assertTrue(fakeTts.spokenTexts.any { it.contains("Wrote hello.txt") })
    }

    @Test
    fun `task status question uses real task evidence without a chat model`() = runTest {
        val vm = CompanionViewModel(ttsManager = fakeTts, workspaceRoot = Files.createTempDirectory("memba_vm_ws2").toFile(),
            ioDispatcher = testDispatcher).also { viewModels += it }
        // This must not be swallowed by generic "what ..." chat routing.
        vm.setSpeechMode(com.example.meowmba.ui.companion.SpeechMode.ALWAYS) // this test checks the spoken confirmation
        vm.onUserTextSubmitted("what tasks are running")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.activeTasks.any { it.toolId == "task.list" && it.status == TaskStatus.COMPLETED })
        assertEquals(listOf("No tasks right now."), fakeTts.spokenTexts)
    }
}
