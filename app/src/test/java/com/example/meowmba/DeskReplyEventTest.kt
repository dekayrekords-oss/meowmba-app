package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.agent.ModelProvider
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeskReplyEventTest {
    private class Provider(private val reply: String) : ModelProvider {
        override val name = "Desk reply fixture"
        override fun generateStreaming(prompt: String): Flow<ModelResponse> = flowOf(ModelResponse.Complete(reply))
        override suspend fun generate(prompt: String): ModelResponse = ModelResponse.Complete(reply)
    }

    @Test fun mutedCurrentReplyPublishesFullDeskEvent() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val previousIdle = System.getProperty("meowmba.idleAnimations")
        System.setProperty("meowmba.idleAnimations", "false")
        val android = ScopedCompanionAndroidMocks { testScheduler.currentTime }
        var model: CompanionViewModel? = null
        try {
            val vm = CompanionViewModel(ioDispatcher = dispatcher).also { model = it }
            vm.setMuted(true)
            vm.currentProvider = Provider("The Moon reflects sunlight.")
            vm.onUserTextSubmitted("why is the moon bright")
            runCurrent()

            val state = vm.uiState.value
            assertEquals(1L, state.deskReplySequence)
            assertEquals("The Moon reflects sunlight.", state.deskReplyText)
            assertEquals(state.activeConversationId, state.deskReplyConversationId)
            assertFalse(state.deskReplyText.isNullOrBlank())
        } finally {
            model?.viewModelScope?.cancel()
            android.close()
            if (previousIdle == null) System.clearProperty("meowmba.idleAnimations")
            else System.setProperty("meowmba.idleAnimations", previousIdle)
            Dispatchers.resetMain()
        }
    }
}
