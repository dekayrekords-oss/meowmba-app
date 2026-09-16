package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.agent.ModelProvider
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.audio.SpeechEngine
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.companion.InteractionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Real ViewModel turn paths, with controllable recognizer and playback callbacks. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceRecoveryRegressionTest {
    private class Engine : SpeechEngine {
        override val continuousCapture = true
        override val deliversFinalResults = true
        override val isListening = MutableStateFlow(false)
        override val partialResults = MutableStateFlow("")
        val results = mutableListOf<(String) -> Unit>()
        val errors = mutableListOf<(Int) -> Unit>()
        var submissions = 0
        var stops = 0
        override fun startListening(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            results += onResult; errors += onError; isListening.value = true
        }
        override fun swapCallbacks(onResult: (String) -> Unit, onError: (Int) -> Unit) {
            results += onResult; errors += onError
        }
        override fun finishInput(): Boolean { submissions++; isListening.value = false; return true }
        override fun stopListening() { stops++; isListening.value = false; partialResults.value = "" }
        override fun destroy() = Unit
    }
    private class Provider(private val events: Flow<ModelResponse>) : ModelProvider {
        override val name = "Controlled reply provider"
        override fun generateStreaming(prompt: String) = events
        override suspend fun generate(prompt: String) = ModelResponse.Complete("{\"tool\":null}")
    }
    private suspend fun TestScope.withVm(
        block: suspend TestScope.(CompanionViewModel, Engine, VoicePipelineTest.FakeSpeechSynthesizer) -> Unit,
    ) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val engine = Engine()
        val tts = VoicePipelineTest.FakeSpeechSynthesizer()
        val vm = CompanionViewModel(captureEngine = engine, ttsManager = tts, ioDispatcher = dispatcher)
        try { block(vm, engine, tts) } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun repeatedForegroundRequestsShareOnePendingMicStartAndMuteCancelsIt() = runTest { withVm { vm, engine, _ ->
        val released = CompletableDeferred<Unit>()
        var waits = 0
        val wake = object : com.example.meowmba.core.audio.WakeWordEngine {
            override val isRunning = MutableStateFlow(false)
            override var onWake: ((String?) -> Unit)? = null
            override fun start() = Unit
            override fun stop() = Unit
            override suspend fun stopAndAwait(timeoutMs: Long) { waits++; released.await() }
        }
        CompanionViewModel::class.java.getDeclaredField("wakeWordEngine").apply {
            isAccessible = true; set(vm, wake)
        }
        val before = engine.results.size
        repeat(8) { vm.reclaimWakeMicForForeground() }
        runCurrent()
        assertEquals(1, waits)
        assertEquals(before, engine.results.size)
        vm.setMicEnabled(false)
        released.complete(Unit)
        runCurrent()
        assertEquals(before, engine.results.size)
        assertFalse(engine.isListening.value)
    } }
    @Test fun completeOnlyProviderAnswerIsVisibleAndSpoken() = runTest { withVm { vm, _, tts ->
        vm.currentProvider = Provider(flowOf(ModelResponse.Complete("The sky scatters blue light.")))
        vm.onUserTextSubmitted("why is the sky blue")
        runCurrent()
        assertEquals("The sky scatters blue light.", vm.uiState.value.liveAssistantReply)
        assertEquals(listOf("The sky scatters blue light."), tts.spokenTexts)
        assertNull(vm.uiState.value.errorMessage)
    } }
    @Test fun similarExplicitTypedQuestionsAreSeparateTurns() = runTest { withVm { vm, _, tts ->
        vm.currentProvider = Provider(flowOf(ModelResponse.Complete("Here is the answer.")))
        vm.onUserTextSubmitted("why is the moon bright"); runCurrent()
        vm.onUserTextSubmitted("why is venus bright"); runCurrent()
        assertEquals(2, tts.spokenTexts.size)
    } }
    @Test fun finalCorrectionReplacesDraftAndDuplicateTerminalCannotSpeakAgain() = runTest { withVm { vm, _, tts ->
        vm.currentProvider = Provider(flowOf(
            ModelResponse.Partial("The planet is entirely made of a very unusual substance. "),
            ModelResponse.Complete("The planet is rocky."),
            ModelResponse.Complete("A duplicate must not replace this.")))
        vm.onUserTextSubmitted("why is the planet solid")
        runCurrent()
        assertEquals("The planet is rocky.", vm.uiState.value.liveAssistantReply)
        assertEquals("The planet is rocky.", tts.spokenTexts.last())
        assertFalse(tts.spokenTexts.any { it.contains("duplicate") })
    } }
    @Test fun incompleteStreamKeepsActualPartialWithFailureNotice() = runTest { withVm { vm, _, _ ->
        vm.currentProvider = Provider(flowOf(ModelResponse.Partial("This is the part that arrived")))
        vm.onUserTextSubmitted("why do stars shine")
        runCurrent()
        assertTrue(vm.uiState.value.liveAssistantReply.orEmpty().contains("This is the part that arrived"))
        assertTrue(vm.uiState.value.liveAssistantReply.orEmpty().contains("ended before completion"))
        assertEquals(InteractionState.OFFLINE, vm.uiState.value.interactionState)
    } }
    @Test fun mutedFinalReplyReleasesThinkingImmediately() = runTest { withVm { vm, _, tts ->
        vm.setMuted(true)
        vm.currentProvider = Provider(flowOf(ModelResponse.Complete("The Moon reflects sunlight.")))
        vm.onUserTextSubmitted("why is the moon bright")
        runCurrent()
        assertEquals(InteractionState.IDLE, vm.uiState.value.interactionState)
        assertEquals("The Moon reflects sunlight.", vm.uiState.value.liveAssistantReply)
        assertTrue(tts.spokenTexts.isEmpty())
    } }
    @Test fun mutedDirectCommandReleasesListeningImmediately() = runTest { withVm { vm, engine, _ ->
        vm.setMuted(true)
        vm.onMicClick()
        engine.results.last().invoke("what time is it")
        runCurrent()
        assertEquals(InteractionState.IDLE, vm.uiState.value.interactionState)
        assertTrue(vm.uiState.value.liveAssistantReply.orEmpty().any { it.isDigit() })
    } }
    @Test fun interruptionWithoutStopCallbackDoesNotDiscardNextPlaybackCompletion() = runTest { withVm { vm, engine, tts ->
        vm.onUserTextSubmitted("what time is it")
        tts.onSpeechStarted?.invoke()
        vm.interrupt()
        engine.results.last().invoke("what day is it")
        tts.onSpeechStarted?.invoke()
        assertEquals(InteractionState.SPEAKING, vm.uiState.value.interactionState)
        tts.onSpeechFinished?.invoke()
        assertNotEquals(InteractionState.SPEAKING, vm.uiState.value.interactionState)
    } }
    @Test fun latePlaybackCompletionCannotReplaceCurrentMicrophoneCapture() = runTest { withVm { vm, engine, tts ->
        vm.setConversationMode(true)
        vm.onMicClick()
        val count = engine.results.size
        tts.onSpeechFinished?.invoke()
        runCurrent()
        assertEquals(count, engine.results.size)
        assertEquals(InteractionState.LISTENING, vm.uiState.value.interactionState)
    } }
    @Test fun oldAlwaysOnErrorCannotClearReplacementManualCapture() = runTest { withVm { vm, engine, _ ->
        vm.reclaimWakeMicForForeground()
        runCurrent()
        val old = engine.errors.last()
        vm.onMicClick()
        assertEquals(InteractionState.LISTENING, vm.uiState.value.interactionState)
        val stops = engine.stops
        old(7)
        assertEquals(InteractionState.LISTENING, vm.uiState.value.interactionState)
        assertEquals(stops, engine.stops)
    } }
    @Test fun callMicrophoneCanSubmitAndCancelWedgedAlwaysOnFinalization() = runTest { withVm { vm, engine, tts ->
        vm.toggleCallMode()
        tts.onSpeechStarted?.invoke(); tts.onSpeechFinished?.invoke()
        advanceTimeBy(2_001); runCurrent() // Wait for the bounded recorder handoff, not a stale partial.
        assertTrue(engine.isListening.value)
        engine.partialResults.value = "please tell me the weather"
        runCurrent()
        assertEquals(InteractionState.LISTENING, vm.uiState.value.interactionState)
        vm.onChatMicClick()
        assertEquals(1, engine.submissions)
        vm.onChatMicClick()
        assertEquals(1, engine.submissions)
        assertNotEquals(InteractionState.LISTENING, vm.uiState.value.interactionState)
    } }
    @Test fun directReplyStillAppearsAfterBackgroundMicHandoff() = runTest { withVm { vm, _, tts ->
        vm.handOffListeningToBackground()
        vm.onUserTextSubmitted("what time is it")
        runCurrent()
        assertTrue(vm.uiState.value.liveAssistantReply.orEmpty().any { it.isDigit() })
        assertTrue(tts.spokenTexts.isEmpty())
    } }
    @Test fun hungDirectAnswerTimesOutAndLateFinalCannotResurrectIt() = runTest { withVm { vm, _, tts ->
        val answer = CompletableDeferred<Unit>()
        vm.currentProvider = Provider(flow { answer.await(); emit(ModelResponse.Complete("Too late")) })
        vm.onUserTextSubmitted("why is water wet")
        runCurrent()
        assertEquals(InteractionState.THINKING, vm.uiState.value.interactionState)
        advanceTimeBy(45_001); runCurrent()
        assertEquals(InteractionState.IDLE, vm.uiState.value.interactionState)
        assertTrue(vm.uiState.value.liveAssistantReply.orEmpty().contains("too long"))
        answer.complete(Unit); runCurrent()
        assertFalse(tts.spokenTexts.contains("Too late"))
        assertFalse(vm.uiState.value.liveAssistantReply.orEmpty().contains("Too late"))
    } }
}
