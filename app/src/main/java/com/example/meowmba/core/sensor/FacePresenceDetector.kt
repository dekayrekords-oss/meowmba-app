package com.example.meowmba.core.sensor

import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects whether a face is present and looking at the screen using ML Kit.
 * Runs on front-camera ImageAnalysis frames, throttled to ~1 frame/2s.
 *
 * Emits [AttentionState] indicating face presence and gaze direction.
 * Used for wake-word-free interaction: if user is looking + speaks -> respond.
 */
data class AttentionState(
    /** A face is detected in the frame. */
    val facePresent: Boolean = false,
    /** Face is frontal (euler Y angle small) AND both eyes open. */
    val lookingAtScreen: Boolean = false,
    /** Timestamp of last face detection. */
    val lastSeenAt: Long = 0L,
)

class FacePresenceDetector {

    companion object {
        private const val TAG = "FacePresence"
        /** Minimum interval between analyses (ms). */
        private const val THROTTLE_MS = 2000L
        /** Euler-Y threshold for "frontal" (degrees). */
        private const val FRONTAL_THRESHOLD = 20f
        /** Eye-open probability threshold. */
        private const val EYE_OPEN_THRESHOLD = 0.4f
        /** Auto-disable after no face for this long (ms). */
        const val AUTO_DISABLE_MS = 5 * 60_000L
    }

    private val _state = MutableStateFlow(AttentionState())
    val state: StateFlow<AttentionState> = _state.asStateFlow()

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.25f)
            .build()
    )

    private var lastAnalysisTime = 0L
    private var enabled = true

    /** Called from ImageAnalysis use-case. Throttles internally. */
    @androidx.camera.core.ExperimentalGetImage
    fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (!enabled || now - lastAnalysisTime < THROTTLE_MS) {
            imageProxy.close()
            return
        }
        lastAnalysisTime = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                processResults(faces, now)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Face detection failed: ${e.message}")
                _state.value = AttentionState(facePresent = false, lookingAtScreen = false, lastSeenAt = _state.value.lastSeenAt)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun processResults(faces: List<Face>, now: Long) {
        if (faces.isEmpty()) {
            _state.value = _state.value.copy(facePresent = false, lookingAtScreen = false)
            return
        }

        // Use the largest (closest) face
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return

        val isFrontal = Math.abs(face.headEulerAngleY) < FRONTAL_THRESHOLD
        val leftEyeOpen = (face.leftEyeOpenProbability ?: 0f) > EYE_OPEN_THRESHOLD
        val rightEyeOpen = (face.rightEyeOpenProbability ?: 0f) > EYE_OPEN_THRESHOLD
        val isLooking = isFrontal && leftEyeOpen && rightEyeOpen

        _state.value = AttentionState(
            facePresent = true,
            lookingAtScreen = isLooking,
            lastSeenAt = now,
        )

        if (isLooking) {
            Log.d(TAG, "User looking at screen (eulerY=${face.headEulerAngleY.toInt()}deg)")
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        if (!on) {
            _state.value = AttentionState()
        }
    }

    fun release() {
        detector.close()
    }
}
