package com.example.meowmba

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.media.SongRecognizer
import com.example.meowmba.core.media.SongMatch
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class SongCaptureOwnershipTest {
    @Test fun completionRestoresMicOffBeforeLookupAndNeverPersistsAlwaysOn() = runTest { verifyCapture(false) }
    @Test fun cancellationRestoresMicOffAndDoesNotUpload() = runTest { verifyCapture(true) }

    private suspend fun TestScope.verifyCapture(cancel: Boolean) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = mock(Context::class.java)
        val storage = mock(SecureStorageManager::class.java)
        `when`(storage.context).thenReturn(context)
        `when`(storage.getApiKey("audd")).thenReturn("fixture-key")
        `when`(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)).thenReturn(PackageManager.PERMISSION_GRANTED)
        val stt = VoicePipelineTest.FakeSpeechEngine()
        val sample = CompletableDeferred<ByteArray>()
        var uploads = 0
        val vm = CompanionViewModel(elapsedRealtimeMs = { testScheduler.currentTime }, secureStorage = storage,
            speechService = stt, captureEngine = stt, ttsManager = VoicePipelineTest.FakeSpeechSynthesizer())
        vm.songRecognizerOverride = object : SongRecognizer {
            override suspend fun recordSample() = sample.await()
            override suspend fun identify(wav: ByteArray, token: String): SongMatch {
                assertFalse("Capture is over before upload", vm.uiState.value.isMicEnabled)
                uploads++
                return SongMatch("Fixture song", "Fixture artist")
            }
        }
        try {
            vm.setMicEnabled(false); clearInvocations(storage)
            vm.requestSongIdentification(); vm.identifyNearbySong(); runCurrent()
            assertTrue(vm.uiState.value.songRecognitionBusy)
            assertTrue("Show the explicitly requested capture", vm.uiState.value.isMicEnabled)
            assertEquals(0, stt.startListeningCalls)
            if (cancel) vm.cancelSongIdentification() else sample.complete(ByteArray(64))
            runCurrent()
            assertFalse(vm.uiState.value.isMicEnabled)
            assertFalse(vm.uiState.value.songRecognitionBusy)
            assertEquals(if (cancel) 0 else 1, uploads)
            assertEquals(0, stt.startListeningCalls)
            verify(storage, never()).saveBooleanPreference(com.example.meowmba.core.audio.MicrophonePrivacy.PREF_KEY, false)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
