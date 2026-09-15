package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.audio.*
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpressionIntegrationTest {
    private class Delivery : SpeechSynthesizer {
        override var onSpeechStarted: (() -> Unit)? = null
        override var onSpeechFinished: (() -> Unit)? = null
        override var onWordBoundary: ((Int, Int) -> Unit)? = null
        var delivery = VoiceExpression()
        val queued = mutableListOf<Pair<String, VoiceExpression>>()
        val starts = mutableListOf<() -> Unit>()
        override fun setExpression(expression: VoiceExpression) { this.delivery = expression }
        override fun speak(text: String) { queued += text to delivery }
        override fun speak(text: String, onStarted: () -> Unit) { speak(text); starts += onStarted }
        override fun stop() {}
        override fun shutdown() {}
    }
    private suspend fun TestScope.withVm(block: suspend TestScope.(CompanionViewModel, Delivery) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val tts = Delivery()
        val vm = CompanionViewModel(elapsedRealtimeMs = { System.nanoTime() / 1_000_000L }, ttsManager = tts, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        try { block(vm, tts) } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun seriousTurnBlocksUnsolicitedTricksButRequestedScenesStillPlay() = runTest {
        withVm { vm, _ ->
            vm.onUserTextSubmitted("I'm worried about tomorrow")
            runCurrent()
            vm.playAnimationNamed("Butterfly Chase", userRequested = false)
            assertNull(vm.uiState.value.activeAnimation)
            vm.playAnimationNamed("Lost Mouse", userRequested = true)
            assertNotNull(vm.uiState.value.activeAnimation)
            assertEquals(AvatarAnimal.AUSTRALIAN_SHEPHERD, vm.uiState.value.performanceAnimal)
            assertFalse(vm.uiState.value.robotBodyEnabled)
        }
    }
    @Test fun preferencesAdjustPlansAndDoNotEnableMicrophoneOrRobot() = runTest {
        withVm { vm, _ ->
            vm.setMicEnabled(false)
            vm.setExpressionIntensity(ExpressionIntensity.SUBTLE)
            vm.setPlayfulness(Playfulness.QUIET)
            vm.onUserTextSubmitted("No, that's not what I meant")
            assertEquals(ExpressionIntensity.SUBTLE, vm.uiState.value.expressionIntensity)
            assertEquals(Playfulness.QUIET, vm.uiState.value.playfulness)
            assertEquals(.55f, vm.uiState.value.expressionPlan!!.intensity, .001f)
            assertFalse(vm.uiState.value.isMicEnabled)
            assertFalse(vm.uiState.value.robotBodyEnabled)
        }
    }
    @Test fun realStartTokenCannotCompleteAReplacedTurnAndVoiceUsesItsPlan() = runTest {
        withVm { vm, tts ->
            vm.interactionTimings.setEnabled(true)
            vm.onUserTextSubmitted("what time is it")
            runCurrent()
            assertTrue(tts.starts.isNotEmpty())
            assertEquals(vm.uiState.value.expressionPlan!!.voice(), tts.queued.last().second)
            val oldStart = tts.starts.last()
            vm.onUserTextSubmitted("what is today's date")
            runCurrent()
            val metric = com.example.meowmba.core.diagnostics.InteractionTimings.Metric.TURN_SUBMIT_TO_FIRST_SPEECH_OUTPUT
            oldStart()
            assertEquals(0, vm.interactionTimings.snapshot().summary(metric).sampleCount)
            tts.starts.last()()
            assertEquals(1, vm.interactionTimings.snapshot().summary(metric).sampleCount)
        }
    }
}
