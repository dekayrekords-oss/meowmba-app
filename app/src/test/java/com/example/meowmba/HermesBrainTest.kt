package com.example.meowmba

import com.example.meowmba.core.agent.runtime.HermesProtocol
import com.example.meowmba.core.agent.tools.FileDeleteTool
import com.example.meowmba.core.agent.tools.FileWriteTool
import com.example.meowmba.core.agent.tools.ToolRegistry
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class HermesProtocolTest {

    private lateinit var registry: ToolRegistry

    @Before
    fun setup() {
        val workspace = Workspace(Files.createTempDirectory("hermes_ws").toFile())
        registry = ToolRegistry().apply {
            register(FileWriteTool(workspace))
            register(FileDeleteTool(workspace))
        }
    }

    @Test
    fun `tool definitions convert to openai function calling format`() {
        val tools = HermesProtocol.toOpenAiTools(registry.definitions())
        assertEquals(2, tools.size)
        val first = tools[0].jsonObject
        assertEquals("function", first["type"]?.jsonPrimitive?.content)
        val fn = first["function"]!!.jsonObject
        assertTrue(fn["name"]!!.jsonPrimitive.content.startsWith("file."))
        val params = fn["parameters"]!!.jsonObject
        assertEquals("object", params["type"]?.jsonPrimitive?.content)
        assertTrue(params["properties"]!!.jsonObject.isNotEmpty())
        assertTrue(params["required"]!!.jsonArray.isNotEmpty())
    }

    @Test
    fun `request body carries model, messages, tools and tool_choice`() {
        val body = HermesProtocol.buildRequestBody(
            "hermes-agent", "make a file", HermesProtocol.toOpenAiTools(registry.definitions())
        )
        assertEquals("hermes-agent", body["model"]?.jsonPrimitive?.content)
        assertEquals("auto", body["tool_choice"]?.jsonPrimitive?.content)
        assertEquals(false, body["stream"]?.jsonPrimitive?.content?.toBoolean())
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("user", messages[1].jsonObject["role"]?.jsonPrimitive?.content)
        assertTrue(body["tools"]!!.jsonArray.isNotEmpty())
    }

    @Test
    fun `structured tool_calls response is parsed`() {
        val body = """
        {"choices": [{"message": {
            "role": "assistant",
            "content": null,
            "tool_calls": [{
                "id": "call_1",
                "type": "function",
                "function": {"name": "file.write", "arguments": "{\"path\": \"todo.txt\", \"content\": \"buy milk\"}"}
            }]
        }}]}
        """.trimIndent()
        val call = HermesProtocol.parseResponse(body, registry)
        assertEquals("file.write", call?.toolId)
        assertEquals("todo.txt", call?.args?.get("path"))
        assertEquals("buy milk", call?.args?.get("content"))
    }

    @Test
    fun `content fallback catches raw json spoken as text`() {
        // The exact failure mode the Memba KB warns about: model speaks the
        // tool call as content instead of structured tool_calls.
        val body = """
        {"choices": [{"message": {
            "role": "assistant",
            "content": "{\"tool\": \"file.delete\", \"args\": {\"path\": \"old.txt\"}}"
        }}]}
        """.trimIndent()
        val call = HermesProtocol.parseResponse(body, registry)
        assertEquals("file.delete", call?.toolId)
        assertEquals("old.txt", call?.args?.get("path"))
    }

    @Test
    fun `plain conversational content yields null`() {
        val body = """
        {"choices": [{"message": {"role": "assistant", "content": "I'd be happy to chat about files!"}}]}
        """.trimIndent()
        assertNull(HermesProtocol.parseResponse(body, registry))
    }

    @Test
    fun `hallucinated tools and garbage are rejected`() {
        val evil = """
        {"choices": [{"message": {"tool_calls": [{"function": {"name": "shell.exec", "arguments": "{\"cmd\": \"rm -rf /\"}"}}]}}]}
        """.trimIndent()
        assertNull(HermesProtocol.parseResponse(evil, registry))
        assertNull(HermesProtocol.parseResponse("not json at all", registry))
        assertNull(HermesProtocol.parseResponse("{}", registry))
    }

    @Test
    fun `endpoint candidates cover both openai sdk and proxy path shapes`() {
        val proxyStyle = com.example.meowmba.core.agent.runtime.HermesAgentBrain(
            com.example.meowmba.core.agent.runtime.HermesConfig("http://meow:8642")
        )
        assertEquals(
            listOf("http://meow:8642/v1/chat/completions", "http://meow:8642/chat/completions"),
            proxyStyle.endpointCandidates()
        )

        val sdkStyle = com.example.meowmba.core.agent.runtime.HermesAgentBrain(
            com.example.meowmba.core.agent.runtime.HermesConfig("http://meow:8642/v1/")
        )
        assertEquals(
            listOf("http://meow:8642/v1/chat/completions"),
            sdkStyle.endpointCandidates()
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HermesRoutingViewModelTest {

    private val models = TestCompanionModels()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeTts: VoicePipelineTest.FakeSpeechSynthesizer

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeTts = VoicePipelineTest.FakeSpeechSynthesizer()
    }

    @After
    fun tearDown() {
        models.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `declining hermes falls back to the on-device brain honestly`() = runTest {
        val workspaceRoot = Files.createTempDirectory("hermes_vm_ws").toFile()
        val vm = CompanionViewModel(elapsedRealtimeMs = { System.nanoTime() / 1_000_000L },
            ttsManager = fakeTts,
            workspaceRoot = workspaceRoot,
            ioDispatcher = testDispatcher
        ).also(models::add)
        // Hermes configured, but the endpoint declines everything (stands in
        // for unreachable / no-tool responses without real sockets)
        vm.setHermesAgentUrl("http://hermes.local:8642")
        vm.hermesBrain = object : com.example.meowmba.core.agent.runtime.AgentBrainRouter {
            override suspend fun route(
                text: String,
                registry: com.example.meowmba.core.agent.tools.ToolRegistry
            ): com.example.meowmba.core.agent.runtime.AgentRouter.AgentIntent? = null
        }
        val provider = LlmToolCallRouterTest.FakeModelProvider(
            """{"tool": "file.write", "args": {"path": "todo.txt", "content": ""}}"""
        )
        vm.currentProvider = provider

        vm.onUserTextSubmitted("hey can you make a file called todo.txt for me")
        runCurrent()

        // Hermes declined quietly; the on-device brain routed it instead
        assertNotNull(vm.uiState.value.pendingApproval)
        assertEquals("todo.txt", vm.uiState.value.activeTasks.first().args["path"])
    }

    @Test
    fun `agent brain toggle off means keyword routing only, even with hermes set`() = runTest {
        val workspaceRoot = Files.createTempDirectory("hermes_vm_ws2").toFile()
        val vm = CompanionViewModel(elapsedRealtimeMs = { System.nanoTime() / 1_000_000L },
            ttsManager = fakeTts,
            workspaceRoot = workspaceRoot,
            ioDispatcher = testDispatcher
        ).also(models::add)
        vm.setHermesAgentUrl("http://127.0.0.1:1")
        vm.setAgentBrainEnabled(false)

        vm.onUserTextSubmitted("hey can you make a file called todo.txt for me")
        runCurrent()

        assertNull(vm.uiState.value.pendingApproval)
        assertTrue(vm.uiState.value.activeTasks.isEmpty())
    }

    @Test
    fun `keyword commands still bypass the brain entirely`() = runTest {
        val workspaceRoot = Files.createTempDirectory("hermes_vm_ws3").toFile()
        val vm = CompanionViewModel(elapsedRealtimeMs = { System.nanoTime() / 1_000_000L },
            ttsManager = fakeTts,
            workspaceRoot = workspaceRoot,
            ioDispatcher = testDispatcher
        ).also(models::add)
        vm.setHermesAgentUrl("http://127.0.0.1:1")

        vm.onUserTextSubmitted("create file called direct.txt")
        runCurrent()

        // Keyword path: task exists at the gate with no brain involvement
        assertNotNull(vm.uiState.value.pendingApproval)
        assertEquals("direct.txt", vm.uiState.value.activeTasks.first().args["path"])
    }
}
