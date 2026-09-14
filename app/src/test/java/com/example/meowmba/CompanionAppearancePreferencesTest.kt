package com.example.meowmba

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.core.background.BackgroundKind
import com.example.meowmba.core.background.BackgroundSelection
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.AvatarStyle
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionAppearancePreferencesTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = mutableListOf<CompanionViewModel>()
    private var oldIdle: String? = null
    private lateinit var androidMocks: ScopedCompanionAndroidMocks
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        androidMocks = ScopedCompanionAndroidMocks { dispatcher.scheduler.currentTime }
        oldIdle = System.getProperty("meowmba.idleAnimations")
        System.setProperty("meowmba.idleAnimations", "false")
    }
    @After fun cleanup() {
        models.forEach { it.viewModelScope.cancel() }
        androidMocks.close()
        Dispatchers.resetMain()
        if (oldIdle == null) System.clearProperty("meowmba.idleAnimations") else System.setProperty("meowmba.idleAnimations", oldIdle!!)
    }

    private fun storage(values: MutableMap<String, String>): SecureStorageManager = mock(SecureStorageManager::class.java).also { store ->
        `when`(store.context).thenReturn(mock(Context::class.java))
        `when`(store.getPreference(anyString())).thenAnswer { values[it.getArgument<String>(0)] }
        doAnswer { values[it.getArgument<String>(0)] = it.getArgument(1); null }
            .`when`(store).savePreference(anyString(), anyString())
        `when`(store.getBooleanPreference(anyString(), anyBoolean())).thenAnswer {
            values[it.getArgument<String>(0)]?.toBooleanStrictOrNull() ?: it.getArgument<Boolean>(1)
        }
        doAnswer { values[it.getArgument<String>(0)] = it.getArgument<Boolean>(1).toString(); null }
            .`when`(store).saveBooleanPreference(anyString(), anyBoolean())
    }
    private fun model(storage: SecureStorageManager? = null) = CompanionViewModel(
        secureStorage = storage, ttsManager = VoicePipelineTest.FakeSpeechSynthesizer(), ioDispatcher = dispatcher,
    ).also { models += it }

    @Test fun scaleBoundsRejectNonfiniteAndPersistOnlyUsableValues() = runTest(dispatcher) {
        val values = mutableMapOf<String, String>()
        val vm = model(storage(values))
        for ((input, expected) in listOf(0f to .5f, 8f to 3f, 2.65f to 2.65f,
            Float.NaN to 2f, Float.POSITIVE_INFINITY to 2f, Float.NEGATIVE_INFINITY to 2f)) {
            vm.setAvatarScale(input)
            assertEquals(expected, vm.uiState.value.avatarScale, 0f)
            assertEquals(expected, values.getValue("avatar_scale").toFloat(), 0f)
        }
    }

    @Test fun habitatSelectionDoesNotOverwriteWallpaperOrAvatarChoice() = runTest(dispatcher) {
        val vm = model()
        val wallpaper = BackgroundSelection(BackgroundKind.IMAGE, "content://test/wallpaper", 47)
        vm.setAvatarStyle(AvatarStyle.LUMEN)
        vm.setBackground(wallpaper)
        vm.setBackground(BackgroundSelection(BackgroundKind.ANIMATION, BackgroundAnimation.LIBRARY.id))
        assertTrue(vm.uiState.value.habitatMode)
        assertEquals(BackgroundAnimation.LIBRARY.id, vm.uiState.value.habitatBackgroundId)
        assertEquals(wallpaper, vm.uiState.value.background)
        assertEquals(AvatarStyle.LUMEN, vm.uiState.value.avatarStyle)
        vm.setAvatarStyle(AvatarStyle.FULL_FLY)
        assertEquals(wallpaper, vm.uiState.value.background)
        assertTrue(vm.uiState.value.habitatMode)
        vm.setHabitatMode(false)
        assertEquals(wallpaper, vm.uiState.value.background)
        assertEquals(BackgroundAnimation.LIBRARY.id, vm.uiState.value.habitatBackgroundId)
        vm.setHabitatMode(true)
        assertEquals(wallpaper, vm.uiState.value.background)
        assertEquals(BackgroundAnimation.LIBRARY.id, vm.uiState.value.habitatBackgroundId)
    }

    @Test fun wallpaperHabitatStyleScaleAndModeRestoreIndependently() = runTest(dispatcher) {
        val values = mutableMapOf<String, String>()
        val store = storage(values)
        val wallpaper = BackgroundSelection(BackgroundKind.IMAGE, "content://test/saved-wallpaper", 61)
        val first = model(store)
        first.setBackground(wallpaper)
        first.setBackground(BackgroundSelection(BackgroundKind.ANIMATION, BackgroundAnimation.GREENHOUSE.id))
        first.setAvatarStyle(AvatarStyle.FULL_FLY)
        first.setAvatarScale(1.65f)
        first.setHabitatMode(false)
        first.viewModelScope.cancel()
        val restored = model(store)
        assertEquals(wallpaper, restored.uiState.value.background)
        assertEquals(BackgroundAnimation.GREENHOUSE.id, restored.uiState.value.habitatBackgroundId)
        assertEquals(AvatarStyle.FULL_FLY, restored.uiState.value.avatarStyle)
        assertEquals(1.65f, restored.uiState.value.avatarScale, 0f)
        assertFalse(restored.uiState.value.habitatMode)
        restored.setHabitatMode(true)
        restored.viewModelScope.cancel()
        val resumedHabitat = model(store)
        assertTrue(resumedHabitat.uiState.value.habitatMode)
        assertEquals(wallpaper, resumedHabitat.uiState.value.background)
        assertEquals(BackgroundAnimation.GREENHOUSE.id, resumedHabitat.uiState.value.habitatBackgroundId)
        resumedHabitat.setAvatarStyle(AvatarStyle.LUMEN)
        assertEquals(wallpaper, resumedHabitat.uiState.value.background)
    }

    @Test fun malformedRestoredScaleAndNonHabitatSceneUseSafeDefaults() = runTest(dispatcher) {
        val values = mutableMapOf("avatar_scale" to "NaN", "habitat_background" to "aurora", "habitat_mode" to "true")
        val vm = model(storage(values))
        assertEquals(2f, vm.uiState.value.avatarScale, 0f)
        assertEquals("kitchen", vm.uiState.value.habitatBackgroundId)
        assertTrue(vm.uiState.value.habitatMode)
    }
}
