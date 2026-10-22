package com.example.meowmba

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.vision.FaceTrack
import com.example.meowmba.core.vision.VisionManager
import com.example.meowmba.ui.companion.CompanionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

/** ViewModel-level policy regressions. CameraX hardware binding is covered by
 * device acceptance; these mocks verify the actual VM invokes its resource gate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraPrivacyViewModelTest {
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

    private fun camera(permission: Boolean = true): VisionManager = mock(VisionManager::class.java).also {
        `when`(it.hasPermission()).thenReturn(permission)
        `when`(it.setFaceTrackingEnabled(true)).thenReturn(true)
        `when`(it.faceTrack).thenReturn(MutableStateFlow<FaceTrack?>(null))
        `when`(it.sceneLuma).thenReturn(MutableStateFlow<Int?>(null))
    }
    private fun model(camera: VisionManager, storage: SecureStorageManager? = null) = CompanionViewModel(
        visionManager = camera, secureStorage = storage,
        ttsManager = VoicePipelineTest.FakeSpeechSynthesizer(), ioDispatcher = dispatcher,
    ).also { models += it }

    @Test fun omniRouterHasOnDeviceVisionWithoutCloudCredentials() = runTest(dispatcher) {
        val vm = model(camera())
        vm.onModelSelected(com.example.meowmba.ui.companion.MODEL_OMNIROUTE)
        assertTrue(vm.visionProviderFor() is com.example.meowmba.core.vision.DeviceVisionProvider)
    }

    @Test fun explicitCloudVisionDoesNotSilentlySwitchProviders() = runTest(dispatcher) {
        val vm = model(camera())
        vm.onModelSelected("OpenAI (Cloud)")
        assertNull(vm.visionProviderFor())
        vm.onModelSelected("OpenRouter (Cloud)")
        assertNull(vm.visionProviderFor())
    }

    @Test fun explicitEnableDoesNotOpenPictureInPicture() = runTest(dispatcher) {
        val camera = camera()
        val vm = model(camera)
        vm.setCameraEnabled(true)
        assertTrue(vm.uiState.value.isCameraEnabled)
        assertFalse(vm.uiState.value.isPreviewVisible)
        val order = inOrder(camera)
        order.verify(camera).setCaptureEnabled(true)
        order.verify(camera).setFaceTrackingEnabled(true)
    }

    @Test fun offCancelsPendingGrantAndDelayedPermissionRequest() = runTest(dispatcher) {
        val camera = camera(permission = false)
        val vm = model(camera)
        vm.setCameraEnabled(true)
        advanceTimeBy(20)
        runCurrent()
        assertTrue(vm.uiState.value.cameraPermissionRequested)
        vm.setCameraEnabled(false)
        `when`(camera.hasPermission()).thenReturn(true)
        vm.onCameraPermissionResult(true)
        advanceTimeBy(20)
        runCurrent()
        assertFalse(vm.uiState.value.isCameraEnabled)
        assertFalse(vm.uiState.value.isPreviewVisible)
        assertFalse(vm.uiState.value.cameraPermissionRequested)
        verify(camera, never()).setCaptureEnabled(true)
        verify(camera, never()).setFaceTrackingEnabled(true)
        verify(camera).setCaptureEnabled(false)
    }

    @Test fun unsolicitedPermissionCallbackCannotEnableCamera() = runTest(dispatcher) {
        val camera = camera()
        val vm = model(camera)
        vm.onCameraPermissionResult(true)
        assertFalse(vm.uiState.value.isCameraEnabled)
        verify(camera, never()).setCaptureEnabled(true)
    }

    @Test fun homeCameraOffClosesPreviewAndDoesNotRestartOnResume() = runTest(dispatcher) {
        val camera = camera()
        val vm = model(camera)
        vm.setCameraEnabled(true)
        vm.togglePreview()
        assertTrue(vm.uiState.value.isPreviewVisible)
        clearInvocations(camera)
        // Same action as the home camera button when the camera is enabled.
        vm.setCameraEnabled(!vm.uiState.value.isCameraEnabled)
        vm.resumeFaceTrackingIfEnabled()
        vm.closeOverlays()
        runCurrent()
        assertFalse(vm.uiState.value.isCameraEnabled)
        assertFalse(vm.uiState.value.isPreviewVisible)
        verify(camera, atLeastOnce()).setCaptureEnabled(false)
        verify(camera, never()).setCaptureEnabled(true)
        verify(camera, never()).setFaceTrackingEnabled(true)
    }

    @Test fun persistedOffSurvivesNewViewModelAndResume() = runTest(dispatcher) {
        val storage = mock(SecureStorageManager::class.java)
        val context = mock(Context::class.java)
        `when`(storage.context).thenReturn(context)
        var savedCamera = true
        `when`(storage.getBooleanPreference("camera_enabled", false)).thenAnswer { savedCamera }
        doAnswer { savedCamera = false; null }.`when`(storage).saveBooleanPreference("camera_enabled", false)
        val firstCamera = camera()
        val first = model(firstCamera, storage)
        assertTrue(first.uiState.value.isCameraEnabled)
        first.setCameraEnabled(false)
        first.viewModelScope.cancel()
        assertFalse(savedCamera)
        val nextCamera = camera()
        val next = model(nextCamera, storage)
        next.resumeFaceTrackingIfEnabled()
        assertFalse(next.uiState.value.isCameraEnabled)
        assertFalse(next.uiState.value.isPreviewVisible)
        verify(nextCamera).setCaptureEnabled(false)
        verify(nextCamera, never()).setCaptureEnabled(true)
        verify(nextCamera, never()).setFaceTrackingEnabled(true)
    }

    @Test fun brainMonitorKeepsAuthorizedTrackingButFullMenusPauseAndRestoreIt() = runTest(dispatcher) {
        val camera = camera()
        val vm = model(camera)
        vm.setCameraEnabled(true)
        clearInvocations(camera)
        vm.toggleNeuralInfo()
        runCurrent()
        verify(camera, never()).setFaceTrackingEnabled(false)
        vm.openNeuralSettingsFromInfo()
        runCurrent()
        verify(camera).setFaceTrackingEnabled(false)
        vm.returnToAvatar()
        runCurrent()
        verify(camera, atLeastOnce()).setFaceTrackingEnabled(true)
        assertTrue(vm.uiState.value.isCameraEnabled)
    }

    @Test fun backgroundAndHiddenStageCannotRestartCameraUntilVisibleAgain() = runTest(dispatcher) {
        val camera = camera()
        val vm = model(camera)
        vm.setCameraEnabled(true)
        vm.releaseWakeMicForBackground()
        clearInvocations(camera)
        vm.resumeFaceTrackingIfEnabled()
        verify(camera, never()).setFaceTrackingEnabled(true)
        vm.reclaimWakeMicForForeground()
        vm.setAvatarPerformanceStageAllowed(false)
        clearInvocations(camera)
        vm.resumeFaceTrackingIfEnabled()
        verify(camera, never()).setFaceTrackingEnabled(true)
        vm.setAvatarPerformanceStageAllowed(true)
        verify(camera).setFaceTrackingEnabled(true)
        assertTrue(vm.uiState.value.isCameraEnabled)
    }
}
