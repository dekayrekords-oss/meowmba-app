package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.agent.*
import com.example.meowmba.core.agent.tools.*
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAnimationLifetimeTest {
    private class Fake(private val response: suspend () -> ModelResponse) : ModelProvider {
        override val name="test provider"
        var calls=0
        override suspend fun generate(prompt:String): ModelResponse { calls++;return response() }
        override fun generateStreaming(prompt:String)= flow<ModelResponse> { emit(generate(prompt)) }
    }
    private suspend fun TestScope.useVm(block: suspend TestScope.(CompanionViewModel,CustomAnimationStore)->Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm=CompanionViewModel(ioDispatcher=UnconfinedTestDispatcher(testScheduler))
        val dir=kotlin.io.path.createTempDirectory("local-director").toFile()
        try { block(vm,CustomAnimationStore(dir)) } finally { vm.viewModelScope.cancel();dir.deleteRecursively();Dispatchers.resetMain() }
    }
    private fun local(vm:CompanionViewModel, provider: ModelProvider) {
        CompanionViewModel::class.java.getDeclaredField("localBrain").apply { isAccessible=true }.set(vm,provider)
    }
    @Test fun newCreationUsesLocalOutputAndSavedReplayNeedsNoModel()=runTest { useVm { vm,store ->
        val provider=Fake { ModelResponse.Complete(FishingFixture.json) };local(vm,provider)
        vm.setAvatarStyle(AvatarStyle.NEO)
        assertTrue(vm.inventLocalAvatarScene("fishing",false,store) is ToolResult.Success)
        assertEquals("Fishing",vm.uiState.value.activeAnimation?.name)
        assertEquals(4,vm.uiState.value.activeAnimation?.props?.size)
        assertEquals(AvatarStyle.NEO,vm.uiState.value.avatarStyle)
        vm.stopAvatarPerformance()
        assertTrue(vm.inventLocalAvatarScene("fishing",false,store) is ToolResult.Success)
        assertEquals(1,provider.calls)
        assertFalse(vm.uiState.value.robotBodyEnabled)
    } }
    @Test fun aSavedCloudProviderIsNeverCalledForLocalCreation()=runTest { useVm { vm,store ->
        val cloud=Fake { error("must never call") };vm.currentProvider=cloud
        assertTrue(vm.inventLocalAvatarScene("fishing",false,store) is ToolResult.Failure)
        assertEquals(0,cloud.calls);assertNull(vm.uiState.value.activeAnimation);assertTrue(store.list().isEmpty())
    } }
    @Test fun stopOrNewPerformancePreventsLateModelOutputFromPlayingOrSaving()=runTest { useVm { vm,store ->
        val answer=CompletableDeferred<ModelResponse>();local(vm,Fake { answer.await() })
        val work=async { vm.inventLocalAvatarScene("fishing",false,store) }
        runCurrent()
        vm.playAnimationNamed("Butterfly Chase")
        answer.complete(ModelResponse.Complete(FishingFixture.json))
        try { work.await();fail("superseded work completed") } catch(_:CancellationException) {}
        assertEquals("Butterfly Chase",vm.uiState.value.activeAnimation?.name)
        assertTrue(store.list().isEmpty())
    } }
    @Test fun freshVariationNeverOverwritesTheFirstSavedCreation()=runTest { useVm { vm,store ->
        local(vm,Fake { ModelResponse.Complete(FishingFixture.json) })
        vm.inventLocalAvatarScene("fishing",false,store)
        val original=store.load("Fishing")
        vm.inventLocalAvatarScene("fishing",true,store)
        assertEquals("Fishing 2",vm.uiState.value.activeAnimation?.name)
        assertEquals(original,store.load("Fishing"));assertNotNull(store.load("Fishing 2"))
    } }
}
