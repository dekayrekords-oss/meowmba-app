package com.example.meowmba

import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.pm.PackageManager
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.CompanionViewModel
import com.example.meowmba.ui.companion.InteractionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MicrophonePermissionTest {
    @Test fun explicitTapRequestsPermissionAndGrantResumesTheRequestedCapture() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = mock(Context::class.java)
        val storage = mock(SecureStorageManager::class.java)
        `when`(storage.context).thenReturn(context)
        `when`(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO))
            .thenReturn(PackageManager.PERMISSION_DENIED)
        val stt = VoicePipelineTest.FakeSpeechEngine()
        val vm = CompanionViewModel(speechService = stt, captureEngine = stt, secureStorage = storage)
        try {
            repeat(3) { vm.onMicClick() }
            assertTrue(vm.uiState.value.microphonePermissionRequested)
            assertEquals(0, stt.startListeningCalls)
            `when`(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO))
                .thenReturn(PackageManager.PERMISSION_GRANTED)
            vm.onMicrophonePermissionResult(true)
            assertFalse(vm.uiState.value.microphonePermissionRequested)
            assertEquals(InteractionState.LISTENING, vm.uiState.value.interactionState)
            assertEquals(1, stt.startListeningCalls)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun denialDoesNotReopenPermissionPromptWithoutAnotherUserAction() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = CompanionViewModel()
        try {
            vm.onMicrophonePermissionResult(false)
            vm.onMicClick()
            assertTrue(vm.uiState.value.microphonePermissionRequested)
            vm.onMicrophonePermissionResult(false)
            advanceTimeBy(10_000); runCurrent()
            assertFalse(vm.uiState.value.microphonePermissionRequested)
            vm.reclaimWakeMicForForeground()
            assertFalse(vm.uiState.value.microphonePermissionRequested)
            vm.onMicClick()
            assertTrue(vm.uiState.value.microphonePermissionRequested)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun chatVoiceTapWithMicOffRequestsPermissionAndGrantStartsTheTurn() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = mock(Context::class.java)
        val storage = mock(SecureStorageManager::class.java)
        `when`(storage.context).thenReturn(context)
        `when`(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)).thenReturn(PackageManager.PERMISSION_DENIED)
        val stt = VoicePipelineTest.FakeSpeechEngine()
        val tts = VoicePipelineTest.FakeSpeechSynthesizer()
        val vm = CompanionViewModel(speechService = stt, captureEngine = stt, secureStorage = storage, ttsManager = tts)
        try {
            vm.setMicEnabled(false); tts.onSpeechStarted?.invoke(); vm.onChatMicClick()
            assertTrue("Stop the old reply even before asking for permission", tts.stopCalls > 0)
            assertTrue(vm.uiState.value.microphonePermissionRequested)
            assertFalse(vm.uiState.value.isMicEnabled)
            assertEquals(0, stt.startListeningCalls)
            `when`(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)).thenReturn(PackageManager.PERMISSION_GRANTED)
            vm.onMicrophonePermissionResult(true); runCurrent()
            assertTrue(vm.uiState.value.isMicEnabled)
            assertEquals(InteractionState.LISTENING, vm.uiState.value.interactionState)
            assertEquals(1, stt.startListeningCalls)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun interruptWithMissingLocalRecognizerDoesNotStayStuckSpeaking() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val tts = VoicePipelineTest.FakeSpeechSynthesizer()
        val vm = CompanionViewModel(speechService = VoicePipelineTest.FakeSpeechEngine(), ttsManager = tts)
        try {
            tts.onSpeechStarted?.invoke()
            vm.interrupt()
            assertEquals(1, tts.stopCalls)
            assertEquals(InteractionState.IDLE, vm.uiState.value.interactionState)
            assertTrue(vm.uiState.value.captionText.orEmpty().contains("install or repair a local model"))
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun actualOsDenialBlocksCaptureBeforeAnyRecognizerCallback() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = mock(Context::class.java)
        val storage = mock(SecureStorageManager::class.java)
        `when`(storage.context).thenReturn(context)
        `when`(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO))
            .thenReturn(PackageManager.PERMISSION_DENIED)
        val stt = VoicePipelineTest.FakeSpeechEngine()
        val tts = VoicePipelineTest.FakeSpeechSynthesizer()
        val vm = CompanionViewModel(speechService = stt, ttsManager = tts, secureStorage = storage)
        try {
            vm.onMicClick()
            vm.startDrawerVoiceSearch()
            vm.reclaimWakeMicForForeground()
            vm.setConversationMode(true)
            tts.onSpeechStarted?.invoke()
            tts.onSpeechFinished?.invoke()
            advanceTimeBy(10_000); runCurrent()
            assertEquals(0, stt.startListeningCalls)
            assertEquals(InteractionState.IDLE, vm.uiState.value.interactionState)
            assertTrue(vm.uiState.value.captionText.orEmpty().contains("permission"))
            // A context without app storage must not start real model downloads.
            verify(context, never()).cacheDir
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun deniedPermissionStopsRetriesWithoutChangingUserPrivacyPreference() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = CompanionViewModel()
        try {
            assertTrue(vm.uiState.value.isMicEnabled)
            vm.onMicrophonePermissionResult(false)
            repeat(3) { vm.onMicClick(); vm.reclaimWakeMicForForeground(); vm.startDrawerVoiceSearch() }
            advanceTimeBy(10_000); runCurrent()
            assertEquals(InteractionState.IDLE, vm.uiState.value.interactionState)
            assertTrue(vm.uiState.value.captionText.orEmpty().contains("Microphone permission is off"))
            assertTrue(vm.uiState.value.isMicEnabled)
            vm.onMicrophonePermissionResult(false) // dismiss the last explicit request
            vm.openBusinessAgents() // no capture should start when permission returns over a tool page
            vm.onMicrophonePermissionResult(true)
            assertNull(vm.uiState.value.captionText)
            assertTrue(vm.uiState.value.showBusinessAgents)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
