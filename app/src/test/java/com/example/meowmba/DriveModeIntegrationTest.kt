package com.example.meowmba

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.car.DrivingDetector
import com.example.meowmba.core.car.DrivingState
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DriveModeIntegrationTest {
    private suspend fun TestScope.withVm(block: suspend TestScope.(CompanionViewModel) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val old = System.getProperty("meowmba.idleAnimations")
        System.setProperty("meowmba.idleAnimations", "false")
        val vm = CompanionViewModel(ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        val ctx = mock(Context::class.java).also { `when`(it.applicationContext).thenReturn(it) }
        val detector = DrivingDetector(ctx, vm.viewModelScope)
        CompanionViewModel::class.java.getDeclaredField("carDetector").apply { isAccessible = true }.set(vm, detector)
        try { block(vm) } finally {
            detector.setCarModeEnabled(false)
            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            Dispatchers.resetMain()
            if (old == null) System.clearProperty("meowmba.idleAnimations") else System.setProperty("meowmba.idleAnimations", old)
        }
    }

    private fun snapshot(vm: CompanionViewModel, s: DrivingState) {
        CompanionViewModel::class.java.getDeclaredMethod("syncDrivingSnapshot", DrivingState::class.java)
            .apply { isAccessible = true }.invoke(vm, s)
    }

    @Test fun voiceModeOnUpdatesMasterAndAvatarImmediatelyAndOffClearsBoth() = runTest {
        withVm { vm ->
            vm.setCarModeEnabled(false)
            vm.setManualDriving(true)
            assertTrue(vm.uiState.value.carModeEnabled)
            assertTrue(vm.uiState.value.driving)
            assertTrue(vm.uiState.value.driveManual)
            assertTrue(vm.uiState.value.driveCardVisible)
            snapshot(vm, DrivingState(isDriving = true, carModeEnabled = true, longitudinalG = -0.8f))
            assertEquals(DriveReaction.LURCH, vm.uiState.value.driveReaction)
            vm.setManualDriving(false)
            assertFalse(vm.uiState.value.driving)
            assertEquals(DriveReaction.NONE, vm.uiState.value.driveReaction)
            assertTrue(vm.uiState.value.carModeEnabled)
            vm.setCarModeEnabled(false)
            assertFalse(vm.uiState.value.driveCardVisible)
            assertFalse(vm.uiState.value.carModeEnabled)
        }
    }

    @Test fun maneuverLabelExpiresWithoutAnotherDetectorEmissionAndDoesNotSpeak() = runTest {
        withVm { vm ->
            val before = vm.uiState.value
            snapshot(vm, DrivingState(isDriving = true, carModeEnabled = true, longitudinalG = -0.8f))
            assertEquals(DriveReaction.LURCH, vm.uiState.value.driveReaction)
            advanceTimeBy(DriveReaction.HOLD_MS + 1); runCurrent()
            assertEquals(DriveReaction.NONE, vm.uiState.value.driveReaction)
            assertEquals(before.captionText, vm.uiState.value.captionText)
            assertEquals(before.activeAnimation, vm.uiState.value.activeAnimation)
            assertEquals(before.isCameraEnabled, vm.uiState.value.isCameraEnabled)
            assertEquals(before.isMicEnabled, vm.uiState.value.isMicEnabled)
        }
    }

    @Test fun disabledMasterCannotLeaveAStaleDriveSnapshotActive() = runTest {
        withVm { vm ->
            snapshot(vm, DrivingState(isDriving = true, carModeEnabled = false, lateralG = 0.8f))
            assertFalse(vm.uiState.value.driving)
            assertFalse(vm.uiState.value.driveCardVisible)
            assertEquals(DriveReaction.NONE, vm.uiState.value.driveReaction)
        }
    }
}
