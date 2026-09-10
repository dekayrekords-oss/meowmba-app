package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmotionalInteractionTest {
    private suspend fun TestScope.withVm(block: suspend TestScope.(CompanionViewModel, VoicePipelineTest.FakeSpeechSynthesizer, VoicePipelineTest.FakeSpeechEngine) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val tts=VoicePipelineTest.FakeSpeechSynthesizer()
        val engine=VoicePipelineTest.FakeSpeechEngine()
        val vm=CompanionViewModel(ttsManager=tts,speechService=engine,captureEngine=engine,
            ioDispatcher=UnconfinedTestDispatcher(testScheduler))
        try { block(vm,tts,engine) } finally { vm.viewModelScope.cancel();Dispatchers.resetMain() }
    }
    private fun speak(vm: CompanionViewModel,text:String) {
        CompanionViewModel::class.java.getDeclaredMethod("speak",String::class.java).apply { isAccessible=true }.invoke(vm,text)
    }
    @Test fun apologySurvivesActualSpeechStartAndMouthClosesAtEndOrInterruption() = runTest {
        withVm { vm,tts,_ ->
            speak(vm,"I'm sorry, I couldn't open that file.")
            tts.onSpeechStarted?.invoke()
            assertEquals(CompanionEmotion.APOLOGETIC,vm.uiState.value.emotion)
            tts.onWordBoundary?.invoke(0,3)
            assertEquals(1f,vm.mouthEnergy.value,0f)
            advanceTimeBy(281);runCurrent()
            assertEquals(0f,vm.mouthEnergy.value,0f)
            tts.onWordBoundary?.invoke(4,8)
            tts.onSpeechFinished?.invoke()
            assertEquals(0f,vm.mouthEnergy.value,0f)
            tts.onSpeechStarted?.invoke();tts.onWordBoundary?.invoke(0,3)
            vm.interrupt()
            assertEquals(0f,vm.mouthEnergy.value,0f)
        }
    }
    @Test fun micAttentionReleasesStoryAndKeepsChosenAnimal() = runTest {
        withVm { vm,_,_ ->
            vm.transformAvatar(AvatarAnimal.OWL)
            vm.playAnimationNamed("Book Reading")
            assertNotNull(vm.uiState.value.activeAnimation)
            vm.onMicClick()
            assertNull(vm.uiState.value.activeAnimation)
            assertNull(vm.uiState.value.performanceAnimal)
            assertEquals(AvatarAnimal.OWL,vm.uiState.value.avatarAnimal)
        }
    }
    @Test fun hiddenStageCancelsAndRepeatedPerformanceGetsANewIdentity() = runTest {
        withVm { vm,_,_ ->
            vm.playAnimationNamed("Mouse Chase")
            val sequence=vm.uiState.value.avatarPerformanceSequence
            vm.playAnimationNamed("Mouse Chase")
            assertTrue(vm.uiState.value.avatarPerformanceSequence>sequence)
            vm.setAvatarPerformanceStageAllowed(false)
            assertNull(vm.uiState.value.activeAnimation)
            vm.playAnimationNamed("Mouse Chase",userRequested=false)
            assertNull(vm.uiState.value.activeAnimation)
            vm.playComposedExpression(CatExpressions.resolve("purr")!!,userRequested=false)
            assertNull(vm.uiState.value.activeComposed)
            vm.playCustomAnimation(CustomAnimationSpec("Invented nod",listOf(
                AnimFrame(0),AnimFrame(1500,headTiltDeg=8f))),userRequested=false)
            assertNull(vm.uiState.value.activeAnimation)
            // A deliberate picker request may close the covering panel.
            vm.playAnimationNamed("Mouse Chase",userRequested=true)
            assertNotNull(vm.uiState.value.activeAnimation)
            vm.setAvatarPerformanceStageAllowed(false)
            vm.setAvatarPerformanceStageAllowed(true)
            assertNull(vm.uiState.value.activeAnimation)
        }
    }
    @Test fun explicitEmotionRehearsalsBlockIdleAndLeaveNoPersistedMood() = runTest {
        withVm { vm,_,_ ->
            val before=vm.uiState.value.emotion
            for (emotion in CompanionEmotion.entries) {
                vm.playAnimationNamed("emotion:${emotion.name}")
                assertEquals(emotion,vm.uiState.value.emotionPreview)
                assertFalse(idlePerformanceAllowed(vm.uiState.value,0f,true))
            }
            advanceTimeBy(4501);runCurrent()
            assertNull(vm.uiState.value.emotionPreview)
            assertEquals(before,vm.uiState.value.emotion)
        }
    }
    @Test fun aRequestedSceneSurvivesReadyListeningButYieldsOnTheFirstDraft() = runTest {
        withVm { vm,tts,engine ->
            vm.setConversationMode(true)
            vm.playAnimationNamed("Book Reading")
            tts.onSpeechStarted?.invoke()
            tts.onSpeechFinished?.invoke()
            advanceTimeBy(300);runCurrent()
            assertEquals(InteractionState.LISTENING,vm.uiState.value.interactionState)
            assertNotNull(vm.uiState.value.activeAnimation)
            engine.partialResults.value="wait a second"
            runCurrent()
            assertNull(vm.uiState.value.activeAnimation)
            assertEquals(InteractionState.LISTENING,vm.uiState.value.interactionState)
        }
    }
}
