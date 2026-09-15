package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.weather.WeatherRepository
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherFollowUpTest {
    @Test fun cityReplyStaysWithWeatherAndDenialKeepsAlternative() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val weather = mock(WeatherRepository::class.java)
        `when`(weather.latest).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(null))
        `when`(weather.fetch()).thenReturn(WeatherRepository.Result.Failed("Location access is off."))
        `when`(weather.fetchPlace("New River Arizona")).thenReturn(WeatherRepository.Result.Failed("Offline."))
        val tts = VoicePipelineTest.FakeSpeechSynthesizer()
        val vm = CompanionViewModel(elapsedRealtimeMs = { System.nanoTime() / 1_000_000L }, weatherService = weather, ttsManager = tts)
        try {
            vm.onUserTextSubmitted("what's the weather")
            assertTrue(tts.spokenTexts.last().contains("city and state"))
            vm.onUserTextSubmitted("New River Arizona, can't you see?")
            verify(weather).fetchPlace("New River Arizona")
            assertFalse(tts.spokenTexts.any { it.contains("Files library opened") })
            vm.onUserTextSubmitted("use my location")
            assertTrue(vm.uiState.value.locationPermissionRequested)
            vm.onLocationPermissionResult(false)
            assertFalse(vm.uiState.value.locationPermissionRequested)
            assertTrue(tts.spokenTexts.last().contains("city and state"))
            vm.onUserTextSubmitted("cancel")
            assertEquals("Weather request cancelled.", tts.spokenTexts.last())
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
