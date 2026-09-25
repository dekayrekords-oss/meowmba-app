package com.example.meowmba

import com.example.meowmba.core.agent.runtime.AgentBrainRouter
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.tools.ToolRegistry
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class RobotRoutingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = TestCompanionModels()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.close(); Dispatchers.resetMain() }

    @Test fun `every model router rejects cross robot and unnamed Cozmo proposals`() = runTest {
        for (backend in listOf("hermes", "needle", "llm")) {
            for ((request, tool) in listOf(
                "control Cozmo using a custom move" to "robot.move",
                "control LOOI using a custom move" to "cozmo.control",
                "control the connected robot" to "cozmo.control",
                "control Cozmo using a custom move" to "avatar.animate",
            )) {
                assertNull("Test must exercise the model fallback: $request", AgentRouter.route(request))
                val vm = CompanionViewModel(
                    elapsedRealtimeMs = { System.nanoTime() / 1_000_000L },
                    workspaceRoot = Files.createTempDirectory("robot-routing").toFile(),
                    ttsManager = VoicePipelineTest.FakeSpeechSynthesizer(), ioDispatcher = dispatcher,
                ).also(models::add)
                var called = false
                val brain = object : AgentBrainRouter {
                    override suspend fun route(text: String, registry: ToolRegistry): AgentRouter.AgentIntent {
                        called = true
                        return AgentRouter.AgentIntent(com.example.meowmba.core.agent.runtime.AgentRegistry.ownerOf(tool)!!.id,
                            tool, mapOf("action" to "forward"), "Wrong robot proposal")
                    }
                }
                if (backend == "hermes") vm.hermesBrain = brain
                if (backend == "needle") vm.needleBrain = brain
                val provider = LlmToolCallRouterTest.FakeModelProvider(
                    """{"tool":"$tool","args":{"action":"forward"}}"""
                )
                vm.currentProvider = provider
                vm.onUserTextSubmitted(request)
                runCurrent()
                if (backend != "llm") assertTrue("$backend must propose: $request", called)
                assertTrue("$backend must not submit $tool for $request", vm.uiState.value.activeTasks.isEmpty())
            }
        }
    }
}
