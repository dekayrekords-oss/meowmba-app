package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class CoverAvatarPreferenceTest {
    @Test fun savesRestoresAndDisablesWithoutChangingMic() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val storage = mock(SecureStorageManager::class.java)
        `when`(storage.context).thenReturn(mock(android.content.Context::class.java))
        val first = CompanionViewModel(secureStorage = storage)
        try {
            assertFalse(first.uiState.value.coverAvatarEnabled)
            val mic = first.uiState.value.isMicEnabled
            first.setCoverAvatarEnabled(true)
            assertTrue(first.uiState.value.coverAvatarEnabled)
            assertEquals(mic, first.uiState.value.isMicEnabled)
            verify(storage).saveBooleanPreference("cover_avatar_enabled", true)
            `when`(storage.getBooleanPreference("cover_avatar_enabled", false)).thenReturn(true)
            val restored = CompanionViewModel(secureStorage = storage)
            try {
                assertTrue(restored.uiState.value.coverAvatarEnabled)
                restored.setCoverAvatarEnabled(false)
                assertFalse(restored.uiState.value.coverAvatarEnabled)
                verify(storage).saveBooleanPreference("cover_avatar_enabled", false)
            } finally { restored.viewModelScope.cancel() }
        } finally { first.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
