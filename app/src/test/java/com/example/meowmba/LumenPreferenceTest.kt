package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class LumenPreferenceTest {
    @Test fun catFormAndIndependentColorsSurviveViewModelRecreation() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val storage = mock(SecureStorageManager::class.java)
        `when`(storage.context).thenReturn(mock(android.content.Context::class.java))
        val first = CompanionViewModel(secureStorage = storage, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        try {
            first.setAvatarStyle(AvatarStyle.LUMEN_CAT)
            first.setAvatarEyeColor(0xFFA371F7L); first.setAvatarGlowColor(0xFF39D0D8L); first.setAvatarFeatureColor(0xFFFFA6C9L)
            first.setAppearanceMode(AppearanceMode.LIGHT)
            first.setExpressionIntensity(ExpressionIntensity.SUBTLE)
            first.setPlayfulness(Playfulness.QUIET)
            for ((key, value) in mapOf("avatar_style" to "lumen_cat", "avatar_eye_color" to "FFA371F7",
                "avatar_glow_color" to "FF39D0D8", "avatar_feature_color" to "FFFFA6C9", "appearance_mode" to "light",
                "expression_intensity" to "SUBTLE", "playfulness" to "QUIET")) {
                verify(storage).savePreference(key, value)
                `when`(storage.getPreference(key)).thenReturn(value)
            }
            val restored = CompanionViewModel(secureStorage = storage, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
            try {
                val state = restored.uiState.value
                assertEquals(AvatarStyle.LUMEN_CAT, state.avatarStyle)
                assertEquals(0xFFA371F7L, state.avatarEyeColor); assertEquals(0xFF39D0D8L, state.avatarGlowColor)
                assertEquals(0xFFFFA6C9L, state.avatarFeatureColor); assertEquals(AppearanceMode.LIGHT, state.appearanceMode)
                assertFalse(state.isCameraEnabled); assertNull(state.performanceAnimal)
                assertEquals(ExpressionIntensity.SUBTLE, state.expressionIntensity)
                assertEquals(Playfulness.QUIET, state.playfulness)
                assertFalse(restored.interactionTimings.isEnabled)
            } finally { restored.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        } finally { first.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
    }
}
