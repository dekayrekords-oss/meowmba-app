package com.example.meowmba

import androidx.lifecycle.viewModelScope
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LumenIntegrationTest {
    @Test fun requestedCostumeShowsFaceAndRestoresChosenFormWithoutOpeningBrowser() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val old = System.getProperty("meowmba.idleAnimations")
        System.setProperty("meowmba.idleAnimations", "false")
        val vm = CompanionViewModel(ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        try {
            vm.setAvatarStyle(AvatarStyle.LUMEN)
            vm.setAvatarAnimal(AvatarAnimal.CAT)
            vm.toggleAvatarScreen()
            assertTrue(vm.uiState.value.showAvatarScreen)
            vm.playAnimationNamed("dog")
            assertEquals(AvatarAnimal.DOG, vm.uiState.value.performanceAnimal)
            assertEquals(AvatarAnimal.CAT, vm.uiState.value.avatarAnimal)
            assertFalse(vm.uiState.value.showAvatarScreen)
            assertFalse(vm.uiState.value.showBrowser)
            assertNotNull(vm.uiState.value.activeAnimation)
            advanceTimeBy(5000)
            runCurrent()
            assertNull(vm.uiState.value.performanceAnimal)
            assertNull(vm.uiState.value.activeAnimation)
            assertEquals(AvatarAnimal.CAT, vm.uiState.value.avatarAnimal)
            assertFalse(vm.uiState.value.isCameraEnabled)
        } finally {
            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            Dispatchers.resetMain()
            if (old == null) System.clearProperty("meowmba.idleAnimations") else System.setProperty("meowmba.idleAnimations", old)
        }
    }
}
