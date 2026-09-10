package com.example.meowmba

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.robot.RobotBaseControlPolicy
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class RobotQuarantineViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private var vm: CompanionViewModel? = null
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { vm?.viewModelScope?.cancel(); Dispatchers.resetMain() }

    @Test fun `saved enabled state is revoked and connect scan refresh cannot reenable it`() = runTest {
        val storage = mock(SecureStorageManager::class.java)
        val context = mock(Context::class.java)
        `when`(storage.context).thenReturn(context)
        var savedEnabled = true
        `when`(storage.getBooleanPreference("robot_body_enabled", false)).thenAnswer { savedEnabled }
        doAnswer { savedEnabled = false; null }
            .`when`(storage).saveBooleanPreference("robot_body_enabled", false)
        val model = CompanionViewModel(secureStorage = storage,
            ttsManager = VoicePipelineTest.FakeSpeechSynthesizer(), ioDispatcher = dispatcher)
        vm = model
        runCurrent()
        assertFalse(savedEnabled)
        verify(storage).saveBooleanPreference("robot_body_enabled", false)
        val original = model.uiState.value
        model.setRobotBody(true)
        model.connectRobotFromControls()
        model.scanForRobotBases()
        model.refreshRobot()
        runCurrent()
        val state = model.uiState.value
        assertFalse(savedEnabled)
        assertFalse(state.robotBodyEnabled)
        assertFalse(state.robotConnected)
        assertFalse(state.robotScanning)
        assertTrue(state.robotScanResults.isEmpty())
        assertEquals(RobotBaseControlPolicy.UNAVAILABLE_MESSAGE, state.robotStatus)
        assertEquals(original.isMicEnabled, state.isMicEnabled)
        assertEquals(original.isCameraEnabled, state.isCameraEnabled)
        verify(storage, never()).saveBooleanPreference("robot_body_enabled", true)
        verify(context, never()).getSystemService(Context.BLUETOOTH_SERVICE)
        model.stopRobotFromControls()
        runCurrent()
        assertEquals(RobotBaseControlPolicy.STOP_MESSAGE, model.uiState.value.robotStatus)
        assertFalse(savedEnabled)
    }
}
