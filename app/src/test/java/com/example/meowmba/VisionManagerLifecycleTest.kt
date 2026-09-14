package com.example.meowmba

import android.Manifest
import android.app.Application
import android.os.PowerManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.thermal.ThermalGovernor
import com.example.meowmba.core.vision.AttentionFreshness
import com.example.meowmba.core.vision.FaceTrackingStatus
import com.example.meowmba.core.vision.VisionManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/** Real manager callbacks with the CameraX provider boundary controlled by the test.
 * A seeded running analysis avoids invoking physical cameras or ML Kit initialization.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VisionManagerLifecycleTest {
    private lateinit var context: Application
    private lateinit var manager: VisionManager
    private lateinit var provider: ProcessCameraProvider
    private lateinit var analysis: ImageAnalysis
    private val pending = ArrayDeque<Runnable>()
    private var providerFailure: Exception? = null

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).grantPermissions(Manifest.permission.CAMERA)
        ThermalGovernor.uninstall()
        ThermalGovernor.resetForTest()
        provider = mock(ProcessCameraProvider::class.java)
        `when`(provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)).thenReturn(true)
        @Suppress("UNCHECKED_CAST")
        val future = mock(ListenableFuture::class.java) as ListenableFuture<ProcessCameraProvider>
        `when`(future.get()).thenReturn(provider)
        doAnswer { call -> pending += call.getArgument<Runnable>(0); null }
            .`when`(future).addListener(any(Runnable::class.java), any(Executor::class.java))
        manager = VisionManager(context, mock(LifecycleOwner::class.java)) {
            providerFailure?.let { throw it }
            future
        }
        analysis = mock(ImageAnalysis::class.java)
    }

    @After fun cleanup() {
        manager.close()
        drainProvider()
        ThermalGovernor.uninstall()
        ThermalGovernor.resetForTest()
    }

    private fun field(name: String, value: Any?) {
        VisionManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)
    }

    private fun trackingSession(): AttentionFreshness.Session =
        VisionManager::class.java.getDeclaredField("trackingSession").apply { isAccessible = true }
            .get(manager) as AttentionFreshness.Session

    private fun seedRunningTracking() {
        manager.setCaptureEnabled(true)
        field("_faceTrackingEnabled", true)
        field("imageAnalysis", analysis)
        field("preview", mock(Preview::class.java))
        trackingSession().start()
    }

    private fun drainProvider() {
        while (pending.isNotEmpty()) pending.removeFirst().run()
    }

    private fun boundUseCases(): List<Any?> = mockingDetails(provider).invocations
        .filter { it.method.name == "bindToLifecycle" }
        .flatMap { invocation -> (invocation.rawArguments[2] as Array<*>).toList() }

    @Test fun hidingViewfinderRebindsAnalysisWithoutPreviewAndKeepsTrackingRequested() {
        seedRunningTracking()
        val oldGeneration = trackingSession().generation

        manager.setPreviewSurface(null)
        assertEquals(FaceTrackingStatus.STARTING, manager.trackingStatus.value)
        drainProvider()

        assertEquals(listOf(analysis), boundUseCases())
        assertTrue(manager.isFaceTrackingEnabled)
        assertTrue(trackingSession().generation > oldGeneration)
        assertEquals(FaceTrackingStatus.WAITING_FOR_FRAMES, manager.trackingStatus.value)
        // Activity resume must not be required to repair the session; its enabled
        // call is intentionally idempotent after the replacement bind above.
        assertTrue(manager.setFaceTrackingEnabled(true))
        assertEquals(1, boundUseCases().size)
    }

    @Test fun cameraOffCancelsQueuedPreviewReplacementAndCannotPublishRunningStatus() {
        seedRunningTracking()
        manager.setPreviewSurface(null)
        manager.setCaptureEnabled(false)
        drainProvider()

        assertTrue(boundUseCases().isEmpty())
        assertFalse(manager.isFaceTrackingEnabled)
        assertEquals(FaceTrackingStatus.OFF, manager.trackingStatus.value)
        verify(analysis).clearAnalyzer()
        assertFalse(manager.setFaceTrackingEnabled(true))
        assertTrue(pending.isEmpty())
    }

    @Test fun hidingPreviewWithoutTrackingDoesNotOpenAnotherCameraSession() {
        manager.setCaptureEnabled(true)
        field("preview", mock(Preview::class.java))
        manager.setPreviewSurface(null)
        drainProvider()

        assertTrue(boundUseCases().isEmpty())
        assertEquals(FaceTrackingStatus.PAUSED, manager.trackingStatus.value)
    }

    @Test fun deniedPermissionIsDistinctFromCameraOffAndDoesNotBind() {
        manager.setCaptureEnabled(true)
        shadowOf(context).denyPermissions(Manifest.permission.CAMERA)

        assertFalse(manager.setFaceTrackingEnabled(true))
        drainProvider()

        assertEquals(FaceTrackingStatus.PERMISSION_REQUIRED, manager.trackingStatus.value)
        assertTrue(boundUseCases().isEmpty())
        manager.setCaptureEnabled(false)
        assertEquals(FaceTrackingStatus.OFF, manager.trackingStatus.value)
    }

    @Test fun revokedPermissionBeforeAsyncReplacementIsReportedAndCannotBind() {
        seedRunningTracking()
        manager.setPreviewSurface(null)
        shadowOf(context).denyPermissions(Manifest.permission.CAMERA)
        drainProvider()

        assertEquals(FaceTrackingStatus.PERMISSION_REQUIRED, manager.trackingStatus.value)
        assertTrue(boundUseCases().isEmpty())
    }

    @Test fun thermalPauseStillUnbindsInsteadOfReopeningOnPreviewDetach() {
        seedRunningTracking()
        ThermalGovernor::class.java.getDeclaredField("installed").apply { isAccessible = true }.setBoolean(null, true)
        ThermalGovernor::class.java.getDeclaredField("lastStatus").apply { isAccessible = true }
            .setInt(null, PowerManager.THERMAL_STATUS_MODERATE)

        manager.setPreviewSurface(null)
        drainProvider()

        assertTrue(boundUseCases().isEmpty())
        assertEquals(FaceTrackingStatus.COOLING, manager.trackingStatus.value)
        assertTrue(manager.isFaceTrackingEnabled)
        assertTrue(manager.setFaceTrackingEnabled(true))
        assertTrue(pending.isEmpty())
    }

    @Test fun asynchronousProviderFailureIsNotReportedAsFaceTracking() {
        seedRunningTracking()
        `when`(provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)).thenThrow(IllegalStateException("camera unavailable"))
        manager.setPreviewSurface(null)
        drainProvider()

        assertEquals(FaceTrackingStatus.ERROR, manager.trackingStatus.value)
        assertTrue(boundUseCases().isEmpty())
        assertNull(manager.faceTrack.value)
    }

    @Test fun explicitRetryAfterAsyncBindFailureRequestsAnotherBindWithoutTogglingCameraOff() {
        seedRunningTracking()
        `when`(provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
            .thenThrow(IllegalStateException("camera temporarily unavailable"))
            .thenReturn(true)
        manager.setPreviewSurface(null)
        drainProvider()
        assertEquals(FaceTrackingStatus.ERROR, manager.trackingStatus.value)
        assertTrue(manager.isFaceTrackingEnabled)

        assertTrue(manager.setFaceTrackingEnabled(true))
        assertEquals(FaceTrackingStatus.STARTING, manager.trackingStatus.value)
        drainProvider()

        assertEquals(listOf(analysis), boundUseCases())
        assertEquals(FaceTrackingStatus.WAITING_FOR_FRAMES, manager.trackingStatus.value)
    }

    @Test fun synchronousProviderFailureIsObservableAndExplicitRetryCanRecover() {
        seedRunningTracking()
        providerFailure = IllegalStateException("provider initialization unavailable")
        manager.setPreviewSurface(null)
        assertEquals(FaceTrackingStatus.ERROR, manager.trackingStatus.value)
        assertTrue(boundUseCases().isEmpty())

        providerFailure = null
        assertTrue(manager.setFaceTrackingEnabled(true))
        drainProvider()

        assertEquals(listOf(analysis), boundUseCases())
        assertEquals(FaceTrackingStatus.WAITING_FOR_FRAMES, manager.trackingStatus.value)
    }

    @Test fun normalTrackingEnablesMouthLandmarksWhileBodyFollowRemainsCheap() {
        val normal = manager.detectorOptions(false)
        val follow = manager.detectorOptions(true)
        fun requiredOptions(landmarks: Int, minFaceSize: Float) = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(landmarks)
            .setMinFaceSize(minFaceSize)
            .enableTracking()
            .build()

        // ML Kit options expose value equality but no public property getters.
        assertEquals(requiredOptions(FaceDetectorOptions.LANDMARK_MODE_ALL, 0.08f), normal)
        assertEquals(requiredOptions(FaceDetectorOptions.LANDMARK_MODE_NONE, 0.10f), follow)
    }
}
