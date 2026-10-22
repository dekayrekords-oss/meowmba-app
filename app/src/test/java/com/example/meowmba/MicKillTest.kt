package com.example.meowmba

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.audio.MicrophonePrivacy
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MicKillTest {
    @Test fun killCancelsBothWakeAcknowledgementAndItsDelayedFollowUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            for (finishFirst in listOf(false, true)) {
                val speech = VoicePipelineTest.FakeSpeechEngine()
                val tts = VoicePipelineTest.FakeSpeechSynthesizer()
                val vm = CompanionViewModel(speechService = speech, captureEngine = speech, ttsManager = tts)
                try {
                    vm.onWakeDetected(null)
                    if (finishFirst) tts.onSpeechFinished?.invoke()
                    vm.setMicEnabled(false)
                    if (!finishFirst) tts.onSpeechFinished?.invoke()
                    advanceTimeBy(3000); runCurrent()
                    assertFalse(vm.uiState.value.isMicEnabled)
                    assertNotEquals(com.example.meowmba.ui.companion.InteractionState.LISTENING, vm.uiState.value.interactionState)
                    assertEquals(0, speech.startListeningCalls)
                } finally { vm.viewModelScope.cancel() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun killStopsCaptureAndCannotBeRearmedByResumeOrSpeakerCompletion() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val speech = VoicePipelineTest.FakeSpeechEngine()
        val tts = VoicePipelineTest.FakeSpeechSynthesizer()
        val vm = CompanionViewModel(speechService = speech, captureEngine = speech, ttsManager = tts)
        try {
            vm.onMicClick()
            assertTrue(speech.startListeningCalls > 0)
            vm.setMicEnabled(false)
            val starts = speech.startListeningCalls
            vm.onMicClick(); vm.startDrawerVoiceSearch(); vm.reclaimWakeMicForForeground()
            vm.onMicrophonePermissionResult(true)
            tts.onSpeechFinished?.invoke()
            advanceTimeBy(15000); runCurrent()
            assertFalse(vm.uiState.value.isMicEnabled)
            assertFalse(vm.uiState.value.isMuted)
            assertEquals(0, tts.stopCalls)
            assertEquals(starts, speech.startListeningCalls)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun killCancelsPendingPermissionActionAndSurvivesRecreation() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = mock(Context::class.java)
        val storage = mock(SecureStorageManager::class.java)
        `when`(storage.context).thenReturn(context)
        `when`(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)).thenReturn(PackageManager.PERMISSION_DENIED)
        val speech = VoicePipelineTest.FakeSpeechEngine()
        val vm = CompanionViewModel(secureStorage = storage, speechService = speech, captureEngine = speech)
        try {
            vm.onMicClick(); assertTrue(vm.uiState.value.microphonePermissionRequested)
            vm.setMicEnabled(false)
            verify(storage).saveBooleanPreference(MicrophonePrivacy.PREF_KEY, true)
            `when`(storage.getBooleanPreference(MicrophonePrivacy.PREF_KEY, false)).thenReturn(true)
            `when`(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)).thenReturn(PackageManager.PERMISSION_GRANTED)
            vm.onMicrophonePermissionResult(true)
            advanceTimeBy(1500); runCurrent()
            assertEquals(0, speech.startListeningCalls)
            val restored = CompanionViewModel(secureStorage = storage)
            try {
                assertFalse(restored.uiState.value.isMicEnabled)
                restored.setMicEnabled(true)
                verify(storage).saveBooleanPreference(MicrophonePrivacy.PREF_KEY, false)
                assertTrue(restored.uiState.value.isMicEnabled)
            } finally { restored.viewModelScope.cancel() }
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
