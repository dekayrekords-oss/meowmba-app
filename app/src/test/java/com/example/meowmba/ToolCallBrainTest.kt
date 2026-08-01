package com.example.meowmba

import com.example.meowmba.core.agent.ModelProvider
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.runtime.CommandHeuristics
import com.example.meowmba.core.agent.runtime.LlmToolCallRouter
import com.example.meowmba.core.agent.runtime.ToolCallParser
import com.example.meowmba.core.agent.runtime.ToolCallPrompt
import com.example.meowmba.core.agent.tools.FileDeleteTool
import com.example.meowmba.core.agent.tools.FileReadTool
import com.example.meowmba.core.agent.tools.FileWriteTool
import com.example.meowmba.core.agent.tools.ProjectCreateTool
import com.example.meowmba.core.agent.tools.ToolRegistry
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class ToolCallPromptTest {

    private fun registry(): ToolRegistry {
        val workspace = Workspace(Files.createTempDirectory("tcp_ws").toFile())
        return ToolRegistry().apply {
            register(FileWriteTool(workspace))
            register(FileReadTool(workspace)
            )
        }
    }

    @Test
    fun `prompt contains tool signatures, output contract and user text`() {
        val prompt = ToolCallPrompt.build("make a file", registry().definitions())
        assertTrue(prompt.contains("file.write"))
        assertTrue(prompt.contains("file.read"))
        assertTrue(prompt.contains("""{"tool": "<id>", "args":"""))
        assertTrue(prompt.contains("""{"tool": null}"""))
        assertTrue(prompt.contains("User: make a file"))
    }

    @Test
    fun `hermes native prompt uses the trained chatml function calling template`() {
        val prompt = ToolCallPrompt.buildHermesNative("make a file", registry().definitions())
        assertTrue(prompt.contains("<|im_start|>system"))
        assertTrue(prompt.contains("<tools>"))
        assertTrue(prompt.contains("</tools>"))
        assertTrue(prompt.contains("<tool_call>"))
        assertTrue(prompt.contains("file.write"))
        assertTrue(prompt.contains("<|im_start|>user\nmake a file<|im_end|>"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `both prompt modes steer self-performance to the avatar, never media files`() {
        // Measured on device: "animation of you licking your paw" was routed to
        // media.generate_video and the user got a file while the cat never moved.
        val contract = ToolCallPrompt.build("lick your paw", registry().definitions())
        val hermes = ToolCallPrompt.buildHermesNative("lick your paw", registry().definitions())
        listOf(contract, hermes).forEach { prompt ->
            assertTrue(prompt.contains("avatar.animate"))
            assertTrue(prompt.contains("never media.*"))
        }
    }
}

class ToolCallParserTest {

    private lateinit var registry: ToolRegistry

    @Before
    fun setup() {
        val workspace = Workspace(Files.createTempDirectory("tcp_ws2").toFile())
        registry = ToolRegistry().apply {
            register(FileWriteTool(workspace))
            register(FileDeleteTool(workspace))
        }
    }

    @Test
    fun `parses clean compact json`() {
        val call = ToolCallParser.parse("""{"tool": "file.write", "args": {"path": "a.txt", "content": "hi"}}""", registry)
        assertEquals("file.write", call?.toolId)
        assertEquals("a.txt", call?.args?.get("path"))
        assertEquals("hi", call?.args?.get("content"))
    }

    @Test
    fun `parses json inside code fences and prose`() {
        val output = "Sure! Here is my decision:\n```json\n{\"tool\": \"file.delete\", \"args\": {\"path\": \"old.txt\"}}\n```\nHope that helps."
        val call = ToolCallParser.parse(output, registry)
        assertEquals("file.delete", call?.toolId)
        assertEquals("old.txt", call?.args?.get("path"))
    }

    @Test
    fun `parses hermes style tool_call tags with name and arguments`() {
        val output = "<tool_call>{\"name\": \"file.write\", \"arguments\": {\"path\": \"b.txt\", \"content\": \"x\"}}</tool_call>"
        val call = ToolCallParser.parse(output, registry)
        assertEquals("file.write", call?.toolId)
        assertEquals("b.txt", call?.args?.get("path"))
    }

    @Test
    fun `explicit null tool means conversation`() {
        assertNull(ToolCallParser.parse("""{"tool": null}""", registry))
    }

    @Test
    fun `hallucinated tools are rejected`() {
        assertNull(ToolCallParser.parse("""{"tool": "shell.exec", "args": {"cmd": "rm -rf /"}}""", registry))
    }

    @Test
    fun `garbage yields null, never a guess`() {
        assertNull(ToolCallParser.parse("I don't understand the question.", registry))
        assertNull(ToolCallParser.parse("{broken json", registry))
        assertNull(ToolCallParser.parse("", registry))
    }

    @Test
    fun `braces inside string arguments do not break extraction`() {
        val call = ToolCallParser.parse("""{"tool": "file.write", "args": {"path": "c.txt", "content": "a {nested} brace"}}""", registry)
        assertEquals("a {nested} brace", call?.args?.get("content"))
    }
}

class CommandHeuristicsTest {

    @Test
    fun `command-like text passes the gate`() {
        assertTrue(CommandHeuristics.mightBeCommand("can you make a file for me"))
        assertTrue(CommandHeuristics.mightBeCommand("please delete that thing from my workspace"))
        assertTrue(CommandHeuristics.mightBeCommand("search my notes"))
    }

    @Test
    fun `plain conversation is gated out`() {
        assertTrue(!CommandHeuristics.mightBeCommand("what's the weather like"))
        assertTrue(!CommandHeuristics.mightBeCommand("tell me about yourself"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LlmToolCallRouterTest {

    /** Scripted model provider: returns a fixed completion, records calls. */
    class FakeModelProvider(var completion: String) : ModelProvider {
        override val name = "fake"
        var generateCalls = 0
        var lastPrompt: String? = null

        override fun generateStreaming(prompt: String): Flow<ModelResponse> = flow {
            emit(ModelResponse.Complete(completion))
        }

        override suspend fun generate(prompt: String): ModelResponse {
            generateCalls++
            lastPrompt = prompt
            return ModelResponse.Complete(completion)
        }
    }

    private lateinit var registry: ToolRegistry

    @Before
    fun setup() {
        val workspace = Workspace(Files.createTempDirectory("llm_router_ws").toFile())
        registry = ToolRegistry().apply {
            register(FileReadTool(workspace))
            register(FileWriteTool(workspace))
            register(FileDeleteTool(workspace))
            register(ProjectCreateTool(workspace))
        }
    }

    @Test
    fun `keyword fast path never calls the model`() = runTest {
        val provider = FakeModelProvider("""{"tool": null}""")
        val router = LlmToolCallRouter(provider, registry)
        val intent = router.route("create file called notes.txt")
        assertEquals("file.write", intent?.toolId)
        assertEquals(0, provider.generateCalls)
    }

    @Test
    fun `natural language command is routed by the model`() = runTest {
        val provider = FakeModelProvider("""{"tool": "file.write", "args": {"path": "todo.txt", "content": ""}}""")
        val router = LlmToolCallRouter(provider, registry)
        val intent = router.route("hey can you make a file called todo.txt for me")
        assertEquals("file", intent?.agentId)
        assertEquals("file.write", intent?.toolId)
        assertEquals("todo.txt", intent?.args?.get("path"))
        assertTrue(provider.lastPrompt!!.contains("todo.txt"))
    }

    @Test
    fun `model saying no-tool means conversation`() = runTest {
        val provider = FakeModelProvider("""{"tool": null}""")
        val router = LlmToolCallRouter(provider, registry)
        assertNull(router.route("show me something about files"))
    }

    @Test
    fun `model garbage falls back to conversation safely`() = runTest {
        val provider = FakeModelProvider("I think files are great!")
        val router = LlmToolCallRouter(provider, registry)
        assertNull(router.route("show me the files please"))
    }

    @Test
    fun `chit chat never reaches the model`() = runTest {
        val provider = FakeModelProvider("""{"tool": "file.delete", "args": {"path": "everything"}}""")
        val router = LlmToolCallRouter(provider, registry)
        assertNull(router.route("how are you feeling today"))
        assertEquals(0, provider.generateCalls)
    }

    @Test
    fun `hermes native mode roundtrips a real tool_call response`() = runTest {
        // What Hermes-3 actually generates: ChatML tool_call tags
        val provider = FakeModelProvider(
            "<tool_call>\n{\"arguments\": {\"path\": \"todo.txt\", \"content\": \"\"}, \"name\": \"file.write\"}\n</tool_call>"
        )
        val router = LlmToolCallRouter(
            provider, registry,
            promptMode = com.example.meowmba.core.agent.runtime.ToolCallPromptMode.HERMES_NATIVE
        )
        val intent = router.route("make a file called todo.txt please")
        assertEquals("file", intent?.agentId)
        assertEquals("file.write", intent?.toolId)
        assertEquals("todo.txt", intent?.args?.get("path"))
        // And the model was asked in the native template
        assertTrue(provider.lastPrompt!!.contains("<tools>"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgentBrainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeTts: VoicePipelineTest.FakeSpeechSynthesizer

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeTts = VoicePipelineTest.FakeSpeechSynthesizer()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `natural language file command routes through the on-device brain to the approval gate`() = runTest {
        val workspaceRoot = Files.createTempDirectory("brain_vm_ws").toFile()
        val vm = CompanionViewModel(
            ttsManager = fakeTts,
            workspaceRoot = workspaceRoot,
            ioDispatcher = testDispatcher
        )
        val provider = LlmToolCallRouterTest.FakeModelProvider(
            """{"tool": "file.write", "args": {"path": "todo.txt", "content": ""}}"""
        )
        vm.currentProvider = provider

        vm.onUserTextSubmitted("hey can you make a file called todo.txt for me")
        runCurrent()

        // The model was asked to route, the intent reached the approval gate
        assertEquals(1, provider.generateCalls)
        assertNotNull(vm.uiState.value.pendingApproval)
        val task = vm.uiState.value.activeTasks.first()
        assertEquals("file.write", task.toolId)
        assertEquals("todo.txt", task.args["path"])
    }

    @Test
    fun `disabling the agent brain skips model routing entirely`() = runTest {
        val workspaceRoot = Files.createTempDirectory("brain_vm_ws2").toFile()
        val vm = CompanionViewModel(
            ttsManager = fakeTts,
            workspaceRoot = workspaceRoot,
            ioDispatcher = testDispatcher
        )
        val provider = LlmToolCallRouterTest.FakeModelProvider(
            """{"tool": "file.write", "args": {"path": "todo.txt", "content": ""}}"""
        )
        vm.currentProvider = provider
        vm.setAgentBrainEnabled(false)

        vm.onUserTextSubmitted("hey can you make a file called todo.txt for me")
        runCurrent()

        // No routing call, no task, no approval — it went down the chat path
        assertNull(vm.uiState.value.pendingApproval)
        assertTrue(vm.uiState.value.activeTasks.isEmpty())
    }

    @Test
    fun `prompt mode auto-detects hermes from the loaded local model path`() = runTest {
        val vm = CompanionViewModel(ttsManager = fakeTts)
        assertEquals(
            com.example.meowmba.core.agent.runtime.ToolCallPromptMode.CONTRACT,
            vm.toolCallPromptMode()
        )

        // Simulate a Hermes fine-tune configured as the local model path
        val storage = org.mockito.Mockito.mock(com.example.meowmba.core.security.SecureStorageManager::class.java)
        org.mockito.Mockito.`when`(storage.context).thenReturn(
            org.mockito.Mockito.mock(android.content.Context::class.java)
        )
        org.mockito.Mockito.`when`(storage.getEndpoint("local")).thenReturn("/data/local/tmp/llm/hermes3-3b.task")
        val vm2 = CompanionViewModel(ttsManager = fakeTts, secureStorage = storage)
        assertEquals(
            com.example.meowmba.core.agent.runtime.ToolCallPromptMode.HERMES_NATIVE,
            vm2.toolCallPromptMode()
        )
    }
}
