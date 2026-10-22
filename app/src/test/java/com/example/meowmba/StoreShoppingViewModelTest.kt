package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.agent.tools.*
import com.example.meowmba.core.vault.VaultRepository
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoreShoppingViewModelTest {
    private suspend fun TestScope.withShopping(block: suspend TestScope.(CompanionViewModel, VaultRepository, () -> Int) -> Unit) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val root = kotlin.io.path.createTempDirectory("shopping-viewmodel").toFile()
        val vault = VaultRepository(root.resolve("vault"))
        val engine = VoicePipelineTest.FakeSpeechEngine().apply { deliversFinalResults = true }
        val vm = CompanionViewModel(workspaceRoot = root.resolve("workspace"), noteVault = vault,
            captureEngine = engine, ttsManager = VoicePipelineTest.FakeSpeechSynthesizer(), ioDispatcher = dispatcher,
            elapsedRealtimeMs = { System.nanoTime() / 1_000_000L })
        var calls = 0
        try {
            val registry = CompanionViewModel::class.java.getDeclaredField("agentToolRegistry").apply { isAccessible = true }.get(vm) as ToolRegistry
            registry.register(object : Tool {
                override val definition = ToolDefinition(StoreShoppingTool.ID, "Shopping fixture", "Fixture", riskLevel = RiskLevel.SAFE)
                override suspend fun execute(args: Map<String, String>): ToolResult {
                    calls++
                    return ToolResult.Success("A nearby store is only a guess.\n\nFixture conditions: members only.\nhttps://www.target.com/deals")
                }
            })
            block(vm, vault) { calls }
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain(); root.deleteRecursively() }
    }

    @Test fun `store question reaches the tool before the generic question model and preserves the full reply`() = runTest {
        withShopping { vm, _, calls ->
            vm.onUserTextSubmitted("what store am I in?")
            runCurrent()
            assertEquals(1, calls())
            val reply = vm.uiState.value.liveAssistantReply.orEmpty()
            assertTrue(reply, reply.contains("members only"))
            assertTrue(reply, reply.contains("https://www.target.com/deals"))
            assertFalse(reply.contains("I'll ask if"))
        }
    }

    @Test fun `store arrivals and recorded notes never run shopping tools`() = runTest {
        withShopping { vm, vault, calls ->
            vm.onUserTextSubmitted("I'm at Target")
            runCurrent()
            assertEquals(0, calls())
            vm.onUserTextSubmitted("start taking notes on shopping")
            runCurrent()
            assertTrue(vm.uiState.value.isNoting)
            vm.setMuted(false)
            vm.onUserTextSubmitted("what store am I in?")
            runCurrent()
            assertEquals(0, calls())
            assertTrue(vault.get("Notes/shopping")!!.body.contains("what store am I in"))
            vm.stopNoting()
        }
    }
}
