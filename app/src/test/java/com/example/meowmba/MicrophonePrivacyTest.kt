package com.example.meowmba

import android.app.Service
import android.content.Context
import android.content.Intent
import com.example.meowmba.core.audio.MicOwner
import com.example.meowmba.core.audio.MicrophonePrivacy
import com.example.meowmba.core.audio.SpeechService
import com.example.meowmba.core.audio.TextToSpeechManager
import com.example.meowmba.core.audio.WakeWordEngine
import com.example.meowmba.core.audio.WakeWordService
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mockito records the matcher but returns null; Kotlin callbacks cannot accept it. */
private fun <T> anyCallback(): (T) -> Unit = any<(T) -> Unit>() ?: { _ -> }

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MicrophonePrivacyTest {
    @Test fun followUpAfterEarlyOnCreateReturnDoesNotReadUninitializedStorage() {
        val service = Robolectric.buildService(WakeWordService::class.java).get()
        WakeWordService::class.java.getDeclaredField("isForeground").apply {
            isAccessible = true; set(service, true)
        }
        try {
            assertEquals(Service.START_NOT_STICKY, service.onStartCommand(
                Intent().putExtra(WakeWordService.EXTRA_MODE, WakeWordService.MODE_FOLLOW_UP), 0, 1))
        } finally { service.onDestroy() }
    }
    @Test fun freshSetupPersistsMicrophoneOffBeforeAnyViewModelOrServiceStarts() {
        val storage = mock(SecureStorageManager::class.java)
        MicrophonePrivacy.initializeForFirstRun(storage)
        verify(storage).saveBooleanPreference(MicrophonePrivacy.PREF_KEY, true)
    }

    @Test fun initializationPreservesSavedChoicesAndCompletedLegacySetup() {
        for ((saved, completed) in listOf(true to false, false to true, true to true)) {
            val storage = mock(SecureStorageManager::class.java)
            `when`(storage.hasBooleanPreference(MicrophonePrivacy.PREF_KEY)).thenReturn(saved)
            `when`(storage.getBooleanPreference("onboarding_done", false)).thenReturn(completed)
            MicrophonePrivacy.initializeForFirstRun(storage)
            verify(storage, never()).saveBooleanPreference(anyString(), anyBoolean())
        }
    }

    @Test fun missingPreferenceKeepsLegacyMicrophoneEnabledWithoutWritingAnything() {
        val storage = mock(SecureStorageManager::class.java)
        assertTrue(MicrophonePrivacy.isEnabled(storage))
        verify(storage).getBooleanPreference("microphone_killed", false)
        verifyNoMoreInteractions(storage)
    }

    @Test fun killPreferenceIsIndependentOfBackgroundWakeAndSpeakerSettings() {
        val storage = mock(SecureStorageManager::class.java)
        `when`(storage.getBooleanPreference(MicrophonePrivacy.PREF_KEY, false)).thenReturn(true)
        assertFalse(MicrophonePrivacy.isEnabled(storage))
        verify(storage).getBooleanPreference(MicrophonePrivacy.PREF_KEY, false)
        verifyNoMoreInteractions(storage)
    }

    @Test fun killedMicrophoneBlocksBothStaticServiceEntryPoints() {
        val context = mock(Context::class.java)
        mockConstruction(SecureStorageManager::class.java) { storage, _ ->
            `when`(storage.getBooleanPreference(MicrophonePrivacy.PREF_KEY, false)).thenReturn(true)
        }.use {
            WakeWordService.start(context)
            WakeWordService.startFollowUp(context)
        }
        verify(context, never()).startForegroundService(any(Intent::class.java))
        verify(context, never()).startService(any(Intent::class.java))
    }

    @Test fun unreadablePrivacyStorageFailsClosedBeforeStartingAService() {
        val context = mock(Context::class.java)
        mockConstruction(SecureStorageManager::class.java) { storage, _ ->
            `when`(storage.getBooleanPreference(MicrophonePrivacy.PREF_KEY, false))
                .thenThrow(IllegalStateException("Unavailable"))
        }.use {
            WakeWordService.start(context)
            WakeWordService.startFollowUp(context)
        }
        verify(context, never()).startForegroundService(any(Intent::class.java))
    }

    private class FakeWake : WakeWordEngine {
        override val isRunning = MutableStateFlow(true)
        override var onWake: ((String?) -> Unit)? = null
        var starts = 0
        var stops = 0
        override fun start() { starts++; isRunning.value = true }
        override fun stop() { stops++; isRunning.value = false }
    }

    /** Attach a service but never call onCreate or construct real audio engines. */
    private class Fixture : AutoCloseable {
        val service = Robolectric.buildService(WakeWordService::class.java).get()
        val storage = mock(SecureStorageManager::class.java)
        val speech = mock(SpeechService::class.java)
        val tts = mock(TextToSpeechManager::class.java)
        val wake = FakeWake()
        var killed = false
        var result: ((String) -> Unit)? = null
        var error: ((Int) -> Unit)? = null

        init {
            MicOwner.resetForTest()
            MicOwner.acquire(MicOwner.SERVICE)
            `when`(storage.getBooleanPreference(MicrophonePrivacy.PREF_KEY, false)).thenAnswer { killed }
            doAnswer { call ->
                result = call.getArgument(0)
                error = call.getArgument(1)
                null
            }.`when`(speech).startListening(anyCallback<String>(), anyCallback<Int>())
            set("secureStorage", storage)
            set("speechService", speech)
            set("tts", tts)
            set("wakeEngine", wake)
            set("micHeld", true)
            set("engineStarted", true)
            set("isForeground", true)
            set("capturingCommand", true)
        }

        fun set(name: String, value: Any) {
            WakeWordService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
        }
        fun call(name: String) {
            WakeWordService::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(service)
        }
        override fun close() {
            service.onDestroy()
            MicOwner.resetForTest()
        }
    }

    @Test fun onStartHonorsPersistedKillAndDoesNotChangeWakeOrSpeakerPreferences() {
        Fixture().use { f ->
            f.killed = true
            assertEquals(Service.START_NOT_STICKY,
                f.service.onStartCommand(Intent().putExtra(WakeWordService.EXTRA_MODE, WakeWordService.MODE_FOLLOW_UP), 0, 1))
            assertEquals(0, f.wake.starts)
            assertEquals(1, f.wake.stops)
            assertNull(MicOwner.current)
            verify(f.speech).stopListening()
            verify(f.storage, never()).saveBooleanPreference(anyString(), anyBoolean())
            verifyNoInteractions(f.tts)
        }
    }

    @Test fun lateCaptureCallbacksCannotDeliverSpeakOrRearmAfterKill() {
        Fixture().use { f ->
            f.call("startFollowUpListen")
            assertNotNull(f.result)
            f.killed = true
            f.result!!.invoke("private transcript")
            f.error!!.invoke(android.speech.SpeechRecognizer.ERROR_NO_MATCH)
            f.call("rearmWakeEngine")
            assertEquals(0, f.wake.starts)
            assertNull(MicOwner.current)
            verify(f.speech, times(1)).startListening(anyCallback<String>(), anyCallback<Int>())
            verify(f.speech, times(1)).stopListening()
            verifyNoInteractions(f.tts)
        }
    }

    @Test fun oldServiceCannotResurrectAfterExplicitUnmuteOrStopNewForegroundCapture() {
        Fixture().use { f ->
            f.killed = true
            f.call("rearmWakeEngine")
            f.killed = false
            MicOwner.acquire(MicOwner.IN_APP)
            f.call("rearmWakeEngine")
            f.call("startFollowUpListen")
            assertEquals(0, f.wake.starts)
            assertEquals(MicOwner.IN_APP, MicOwner.current)
            verify(f.speech, times(1)).stopListening()
            verify(f.speech, never()).startListening(anyCallback<String>(), anyCallback<Int>())
            verifyNoInteractions(f.tts)
        }
    }
}
