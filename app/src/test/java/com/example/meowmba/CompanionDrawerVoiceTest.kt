package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.thermal.ThermalGovernor
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.companion.InteractionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Manual drawer actions must stop real capture before reusing its caption channel. */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionDrawerVoiceTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var androidMocks: ScopedCompanionAndroidMocks
    private lateinit var engine: VoicePipelineTest.FakeSpeechEngine
    private lateinit var vm: CompanionViewModel
    private var oldIdle: String? = null

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        androidMocks = ScopedCompanionAndroidMocks { dispatcher.scheduler.currentTime }
        ThermalGovernor.resetForTest()
        oldIdle = System.getProperty("meowmba.idleAnimations")
        System.setProperty("meowmba.idleAnimations", "false")
        engine = VoicePipelineTest.FakeSpeechEngine().apply { deliversFinalResults = true }
        vm = CompanionViewModel(speechService = engine, captureEngine = engine,
            ttsManager = VoicePipelineTest.FakeSpeechSynthesizer(), ioDispatcher = dispatcher)
        vm.toggleAppDrawer()
    }

    @After fun cleanup() {
        vm.viewModelScope.cancel()
        ThermalGovernor.resetForTest()
        androidMocks.close()
        Dispatchers.resetMain()
        if (oldIdle == null) System.clearProperty("meowmba.idleAnimations")
        else System.setProperty("meowmba.idleAnimations", oldIdle!!)
    }

    @Test fun manualGoogleFailureStopsCaptureAndRejectsLateTranscript() {
        vm.startDrawerVoiceSearch()
        engine.simulatePartial("cartoon planets")
        assertEquals(InteractionState.LISTENING, vm.uiState.value.interactionState)
        val stops = engine.stopListeningCalls

        vm.searchDrawerQuery("cartoon planets")
        assertEquals(InteractionState.IDLE, vm.uiState.value.interactionState)
        assertTrue(engine.stopListeningCalls > stops)
        assertTrue(vm.uiState.value.showAppDrawer)
        val failure = vm.uiState.value.captionText
        assertFalse(failure.isNullOrBlank())

        engine.simulatePartial("late unrelated draft")
        engine.simulateResult("late unrelated final")
        dispatcher.scheduler.runCurrent()
        assertEquals(failure, vm.uiState.value.captionText)
        assertNull(vm.uiState.value.drawerVoiceCommit)
    }

    @Test fun stopButtonCancelsWithoutCommittingAndCanListenAgain() {
        vm.startDrawerVoiceSearch()
        engine.simulatePartial("unfinished query")
        vm.startDrawerVoiceSearch()
        engine.simulateResult("late final")
        dispatcher.scheduler.runCurrent()
        assertEquals(InteractionState.IDLE, vm.uiState.value.interactionState)
        assertNull(vm.uiState.value.drawerVoiceCommit)
        assertNull(vm.uiState.value.captionText)
        val starts = engine.startListeningCalls
        vm.startDrawerVoiceSearch()
        assertEquals(InteractionState.LISTENING, vm.uiState.value.interactionState)
        assertTrue(engine.startListeningCalls > starts)
    }

    @Test fun manualSubmissionConsumesAnAlreadyFinalizedVoiceQuery() {
        vm.startDrawerVoiceSearch()
        engine.simulateResult("cartoon planets")
        dispatcher.scheduler.runCurrent()
        assertEquals("cartoon planets", vm.uiState.value.drawerVoiceCommit)
        vm.submitDrawerQuery("search for moon gardens", 0, null)
        assertNull(vm.uiState.value.drawerVoiceCommit)
        assertEquals(InteractionState.IDLE, vm.uiState.value.interactionState)
        assertTrue(vm.uiState.value.showAppDrawer)
        assertFalse(vm.uiState.value.captionText.isNullOrBlank())
    }
}
