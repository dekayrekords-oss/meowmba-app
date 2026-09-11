package com.example.meowmba.core.vision

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.util.Size
import android.view.Display
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.meowmba.core.thermal.ThermalGovernor
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayDeque
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * One face-detection frame. Offsets are normalized to -1..1 in **screen space**:
 * +X is toward the right edge of the display as the user sees it, +Y toward the
 * bottom, regardless of device rotation or which lens is active.
 *
 * Screen space (rather than sensor space) is the contract because every consumer
 * — avatar gaze, head pan, robot servos — works in screen terms. Publishing
 * sensor coordinates pushed a rotation correction into each consumer, and they
 * disagreed: see [rotatedFrameSize] for what that cost.
 *
 * Rotation (yaw/pitch/roll) and distance ([scale]) give the avatar six degrees
 * of freedom to react with. [confidence] is the detector's probability that
 * this is a real face.
 */
data class FaceTrack(
    val offsetX: Float,
    val offsetY: Float,
    /** Head turn left/right, degrees. + = toward the user's right (screen). */
    val yawDeg: Float = 0f,
    /** Head nod up/down, degrees. + = looking up. */
    val pitchDeg: Float = 0f,
    /** Head tilt, degrees. + = clockwise as the viewer sees it. */
    val rollDeg: Float = 0f,
    /** Distance proxy: 1.0 at baseline face size; >1 closer, <1 farther. */
    val scale: Float = 1f,
    val confidence: Float,
    val cue: SocialCue = SocialCue.NONE,
    val followSource: FollowSource = FollowSource.FACE,
    /** True only when ML Kit saw a face. Pose wrists must not steer the base. */
    val hasFace: Boolean = false,
    /** ML Kit tracking id for this face; -1 if unknown. */
    val trackingId: Int = -1,
    /** How many faces the detector saw this frame. */
    val faceCount: Int = 0,
    /** Lip opening 0..1; used to pick the talker when two people are in frame. */
    val mouthOpen: Float = 0f,
    /** Face-box offset for keep-in-frame. Gaze [offsetX]/[offsetY] may be a hand. */
    val frameX: Float = offsetX,
    val frameY: Float = offsetY,
    /** Monotonic frame-receipt time, not publication time; -1 means no freshness evidence. */
    val observedAtElapsedMs: Long = -1L,
)

/**
 * Dimensions of the frame ML Kit measured, given the raw buffer size and the
 * rotation applied to make it upright.
 *
 * `InputImage.fromMediaImage(image, rotationDegrees)` hands ML Kit a rotation
 * and gets boxes back in the *rotated* space, so a 90°/270° frame has its width
 * and height swapped relative to the buffer. Normalizing against the raw buffer
 * size instead squashes one axis by the aspect ratio.
 */
internal fun rotatedFrameSize(bufferW: Int, bufferH: Int, rotationDegrees: Int): Pair<Int, Int> {
    val quarter = ((rotationDegrees % 360) + 360) % 360
    return if (quarter == 90 || quarter == 270) bufferH to bufferW else bufferW to bufferH
}

/**
 * Face box center → screen-space offset in -1..1, or null for a degenerate frame.
 *
 * [mirrored] flips X for a front lens, whose preview the user reads as a mirror:
 * moving to their own right must move the avatar's gaze to the right of the
 * screen, and the un-mirrored sensor reports the opposite.
 *
 * This is pure so the axis convention is pinned by tests rather than by
 * rediscovering it on a device — the horizontal axis silently inverted or
 * swapped with vertical three times before it was extracted.
 */
/**
 * How much of a new face sample to take, as an exponential moving average.
 *
 * The FIRST sample after acquiring a face is trusted much more heavily: there
 * is no history worth blending with, and easing in from wherever the last face
 * left off is what made the head sweep across the screen when someone new
 * stepped into frame.
 */
/**
 * Device tilt in degrees (0..359, as OrientationEventListener reports it) to
 * the matching `Surface.ROTATION_*` constant.
 *
 * The 45-degree bands are deliberate: a phone held at 44 degrees is still being
 * used upright, and flipping the analyzer's rotation at every small tilt would
 * make detection stutter in and out near the boundary.
 */
internal fun surfaceRotationOf(degrees: Int): Int {
    val d = ((degrees % 360) + 360) % 360
    return when {
        d >= 315 || d < 45 -> android.view.Surface.ROTATION_0
        d < 135 -> android.view.Surface.ROTATION_270
        d < 225 -> android.view.Surface.ROTATION_180
        else -> android.view.Surface.ROTATION_90
    }
}

internal fun faceSmoothingAlpha(hasHistory: Boolean): Float = if (hasHistory) 0.35f else 0.62f

/** Hand follow needs to catch the wave, not ease in a half-second late. */
internal fun handSmoothingAlpha(hasHistory: Boolean): Float = if (hasHistory) 0.62f else 0.82f

/**
 * One smoothing step: blend [raw] into [smoothed].
 *
 * The EMA is the ONLY rate limit. A `maxStep` clamp used to sit on top of it,
 * capping travel at 0.14 per published frame — so moving quickly produced a
 * staircase, because the head was allowed to cover only a fixed distance per
 * sample no matter how far the face had actually moved. The EMA already bounds
 * the step (it can never exceed `alpha` × the distance remaining) and it does
 * so proportionally, which is the difference between easing and stepping.
 */
internal fun smoothFaceOffset(smoothed: Float, raw: Float, alpha: Float): Float =
    smoothed + (raw - smoothed) * alpha

internal fun faceOffsetInScreenSpace(
    boxCenterX: Float,
    boxCenterY: Float,
    frameW: Int,
    frameH: Int,
    mirrored: Boolean,
): Pair<Float, Float>? {
    if (frameW <= 0 || frameH <= 0) return null
    val nx = (boxCenterX / frameW * 2f - 1f).coerceIn(-1f, 1f)
    val ny = (boxCenterY / frameH * 2f - 1f).coerceIn(-1f, 1f)
    return (if (mirrored) -nx else nx) to ny
}

/**
 * Face box → display-space offset in -1..1.
 *
 * CameraX's `rotationDegrees` is the rotation ML Kit already applied. When it
 * is 0 the box is still in the raw buffer, which follows the SENSOR — and on
 * the Fold 7 cover in landscape that is the measured failure: targetRotation
 * is ROTATION_90, rot stays 0, and left/right head motion lands on Y
 * (`y≈-0.8`, roll ≈ -35°). [FaceFrameMap] turns that sensor frame into the
 * display frame. When ML Kit did rotate (90/270), the box is already in the
 * target space and remapping would swap the axes a second time.
 */
/**
 * True only when the buffer is still in SENSOR space but the display is not.
 *
 * `cam=0` + `targetRotation == display` means CameraX already delivered a
 * buffer that matches the window (Fold cover landscape: target=1, disp=90).
 * Remapping that a second time swaps the axes — measured: X/Y sat at 0 after
 * rest-centering and pitch jumped to 50° from a bogus euler remap.
 */
internal fun shouldRemapSensorToDisplay(
    cameraRotationDegrees: Int,
    displayDeg: Int,
    targetRotation: Int,
): Boolean {
    val cam = ((cameraRotationDegrees % 360) + 360) % 360
    val disp = ((displayDeg % 360) + 360) % 360
    if (cam != 0 || disp == 0) return false
    val targetDeg = when (targetRotation) {
        android.view.Surface.ROTATION_90 -> 90
        android.view.Surface.ROTATION_180 -> 180
        android.view.Surface.ROTATION_270 -> 270
        else -> 0
    }
    return targetDeg != disp
}

internal fun pointInDisplay01(
    px: Float,
    py: Float,
    bufferW: Int,
    bufferH: Int,
    rotationDegrees: Int,
    displayDeg: Int,
    mirrored: Boolean,
    targetRotation: Int = android.view.Surface.ROTATION_0,
): Pair<Float, Float>? {
    val (w, h) = rotatedFrameSize(bufferW, bufferH, rotationDegrees)
    if (w <= 0 || h <= 0) return null
    var fx = px / w.toFloat()
    var fy = py / h.toFloat()
    if (shouldRemapSensorToDisplay(rotationDegrees, displayDeg, targetRotation)) {
        val mapped = FaceFrameMap.toDisplay(fx, fy, displayDeg)
        fx = mapped.first
        fy = mapped.second
    }
    if (mirrored) fx = 1f - fx
    return fx to fy
}

internal fun faceOffsetOnDisplay(
    boxCenterX: Float,
    boxCenterY: Float,
    bufferW: Int,
    bufferH: Int,
    rotationDegrees: Int,
    displayDeg: Int,
    mirrored: Boolean,
    targetRotation: Int = android.view.Surface.ROTATION_0,
): Pair<Float, Float>? {
    val (fx, fy) = pointInDisplay01(
        px = boxCenterX,
        py = boxCenterY,
        bufferW = bufferW,
        bufferH = bufferH,
        rotationDegrees = rotationDegrees,
        displayDeg = displayDeg,
        mirrored = mirrored,
        targetRotation = targetRotation,
    ) ?: return null
    return (fx * 2f - 1f).coerceIn(-1f, 1f) to (fy * 2f - 1f).coerceIn(-1f, 1f)
}

/**
 * Which Surface.ROTATION_* to give ImageAnalysis.
 *
 * Prefer the window's rotation — it is stable. The IMU is used only when the
 * display is stuck in portrait (rotation-lock / home-launcher) while the
 * phone is on its side. Measured on the Fold 7 cover: driving targetRotation
 * from the IMU flipped 0↔1↔3 every frame and landscape detection died.
 */
internal fun analyzerTargetRotation(displayRot: Int?, physicalRot: Int?): Int? {
    if (displayRot == null) return physicalRot
    val lockedPortrait = displayRot == android.view.Surface.ROTATION_0
    if (lockedPortrait && physicalRot != null && physicalRot != android.view.Surface.ROTATION_0) {
        return physicalRot
    }
    return displayRot
}

/**
 * Rotate ML Kit euler angles into display space when the box was remapped
 * the same way. Identity when ML Kit already worked in the display frame.
 *
 * 90° CW (sensor-up → display-right): displayYaw = sensorPitch,
 * displayPitch = -sensorYaw.
 */
internal fun remapEulerToDisplay(
    yaw: Float,
    pitch: Float,
    roll: Float,
    rotationDegrees: Int,
    displayDeg: Int,
    targetRotation: Int = android.view.Surface.ROTATION_0,
): Triple<Float, Float, Float> {
    if (!shouldRemapSensorToDisplay(rotationDegrees, displayDeg, targetRotation)) {
        return Triple(yaw, pitch, roll)
    }
    return when (((displayDeg % 360) + 360) % 360) {
        90 -> Triple(pitch, -yaw, roll)
        270 -> Triple(-pitch, yaw, roll)
        180 -> Triple(-yaw, -pitch, roll)
        else -> Triple(yaw, pitch, roll)
    }
}

/**
 * CameraX frame capture and face tracking for the vision path (Phase 7a).
 *
 * Bound lazily on the first "look" and kept warm afterwards. Capture-only
 * (no preview use case) — the companion looks on demand; a persistent
 * viewfinder is P7b. Face tracking (P7b) is opt-in via
 * [setFaceTrackingEnabled] and drives the avatar gaze and Looi pan.
 *
 * Honesty: every failure (no camera, bind failure, capture error) comes
 * back as Result.failure with a human-readable reason — the companion
 * says what happened instead of pretending.
 */

class VisionManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var faceDetector: FaceDetector? = null
    private var poseDetector: PoseDetector? = null
    private val recentWristX = ArrayDeque<Float>(8)
    private var prevLuma: ByteArray? = null
    private var lastMotionOff: Pair<Float, Float>? = null
    private var lastMotionAtMs = 0L
    private var motionHits = 0
    private var lastFollow = FollowSource.FACE
    private var gazeSettled = false
    private var lastRealFaceAtMs = 0L
    private var pendingHandCue = SocialCue.NONE
    private var pendingHandHits = 0
    private var boundSelector: CameraSelector? = null
    @Volatile private var _useFrontCamera = false
    @Volatile private var _faceTrackingEnabled = false
    private var lastCoverState = false
    private var displayListener: DisplayManager.DisplayListener? = null
    private val frameInFlight = AtomicBoolean(false)
    private val workBudget = VisionWorkBudget()
    @Volatile private var thermalPaused = false
    private var thermalStatus = 0
    private var lastThermalReadMs = 0L
    // NEVER analyze on the main thread — ML Kit + ImageProxy on main = UI jank.
    private val analysisExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "meow-face-track").apply { isDaemon = true }
    }
    private val trackingSession = AttentionFreshness.Session()
    private val attentionTarget = StableAttentionTarget()
    private var freshnessWatchdog: ScheduledFuture<*>? = null
    private var lastDetectionAtElapsedMs = -1L
    /**
     * Set once by [close]. Every mutator checks it: a manager whose executor has
     * been shut down must refuse to start camera work again rather than throwing
     * RejectedExecutionException out of a lifecycle callback.
     */
    @Volatile
    private var closed = false

    // Smoothed gaze (EMA) + short hold so lost-face frames do not null the stream.
    private var smoothOffX = 0f
    private var smoothOffY = 0f
    private var smoothFrameX = 0f
    private var smoothFrameY = 0f
    private var hasFrameSmooth = false
    private var smoothYaw = 0f
    private var smoothPitch = 0f
    private var smoothRoll = 0f
    private var smoothScale = 1f
    private var lastFaceAtMs = 0L
    private var hasSmooth = false
    private var lastLogMs = 0L
    private var lastPublishAtMs = 0L
    @Volatile
    private var speechLikely = false
    @Volatile private var lastSeenFaces: List<SeenFace> = emptyList()

    private val _faceTrack = MutableStateFlow<FaceTrack?>(null)
    private val _sceneLuma = MutableStateFlow<Int?>(null)

    /** Latest attention target; missing faces may hold visual gaze briefly with hasFace=false. */
    val faceTrack: StateFlow<FaceTrack?> = _faceTrack.asStateFlow()

    /** Mean luma of the analysis frame, 0..255. For the auto headlight. */
    val sceneLuma: StateFlow<Int?> = _sceneLuma.asStateFlow()

    val isFrontCamera: Boolean get() = _useFrontCamera
    val isFaceTrackingEnabled: Boolean get() = _faceTrackingEnabled

    /** Faces from the last published frame (screen space). */
    val lastFaces: List<SeenFace> get() = lastSeenFaces

    /** Audio path says someone is speaking — prefer the open mouth. */
    fun setSpeechLikely(on: Boolean) {
        speechLikely = on
    }

    @Synchronized
    fun setUseFrontCamera(useFront: Boolean) {
        // Face tracking pins the front camera — don't let a stray call fight it.
        if (_faceTrackingEnabled && !useFront) return
        if (useFront == _useFrontCamera) return
        _useFrontCamera = useFront
        unbind()
        bindPreviewAsync()
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Body-follow mode. The base only needs to know WHERE the face box is, a
     * few times a second — it does not need accurate classification, pose, or
     * every frame. Continuous full-rate ACCURATE face + pose is this app's
     * biggest heat hog (measured: it is what "cooked the Fold"), so following
     * uses the shared time/thermal budget, skips pose entirely, and uses the
     * FAST detector. Asking to be seen ("can you see me", eye tap) switches
     * the full pipeline back on.
     */
    @Volatile
    var followOnly = false
        private set

    private fun detectorOptions(follow: Boolean): FaceDetectorOptions =
        if (follow) {
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.10f)
                .enableTracking()
                .build()
        } else {
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.08f)
                .enableTracking()
                .build()
        }

    /** Turn body-follow mode on/off. Safe to call while tracking is live. */
    @Synchronized
    fun setFollowOnly(on: Boolean) {
        if (closed) return
        if (followOnly == on) return
        followOnly = on
        if (!_faceTrackingEnabled) return
        trackingSession.invalidate()
        clearAttention()
        faceDetector?.close()
        faceDetector = FaceDetection.getClient(detectorOptions(on))
        android.util.Log.i("MeowVision", "followOnly=$on (time/thermal budget, pose=${!on})")
    }

    /**
     * Start or stop continuous face tracking. When enabled the front camera is
     * used and a stable visual target is published to [faceTrack].
     * @return true if tracking is actually running (permission + bound), false if
     * disabled or blocked on missing CAMERA permission.
     */
    @Synchronized
    fun setFaceTrackingEnabled(enabled: Boolean): Boolean {
        if (closed) return false
        android.util.Log.i("VM", "setFaceTrackingEnabled($enabled) cur=$_faceTrackingEnabled perm=${hasPermission()}")
        if (enabled && !hasPermission()) {
            if (_faceTrackingEnabled) setFaceTrackingEnabled(false)
            return false
        }
        if (enabled == _faceTrackingEnabled) {
            return _faceTrackingEnabled && hasPermission()
        }
        _faceTrackingEnabled = enabled
        if (enabled) trackingSession.start() else trackingSession.stop()
        freshnessWatchdog?.cancel(false)
        freshnessWatchdog = null
        clearAttention()
        if (enabled) {
            _useFrontCamera = true
            // Keep a capture use-case warm so "look"/"see me" does not fight a
            // rebind race against the analysis-only session.
            if (imageCapture == null) {
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
            }
            faceDetector = FaceDetection.getClient(detectorOptions(followOnly))
            poseDetector = PoseDetection.getClient(
                PoseDetectorOptions.Builder()
                    .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                    .build()
            )
            // QVGA is enough for a face box. 480x360 plus pose every frame
            // cooked the Fold.
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(480, 360),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy ->
                analyzeFrame(proxy, analysis)
            }
            imageAnalysis = analysis
        } else {
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            faceDetector?.close()
            faceDetector = null
            poseDetector?.close()
            poseDetector = null
            unregisterDisplayListener()
        }
        unbind()
        bindPreviewAsync()
        if (_faceTrackingEnabled) startOrientationTracking() else stopOrientationTracking()
        // Only while tracking. This used to run unconditionally, so the OFF
        // branch above unregistered the display listener and then this line
        // immediately registered a NEW one: turning face tracking off leaked a
        // listener every time, and each surviving listener kept calling
        // bindPreviewAsync() on every fold and rotation — phantom camera
        // rebinds for a feature the user had switched off.
        if (_faceTrackingEnabled) registerDisplayListener()
        if (_faceTrackingEnabled) {
            freshnessWatchdog = analysisExecutor.scheduleWithFixedDelay(
                { synchronized(this) { expireAttention(android.os.SystemClock.elapsedRealtime()) } },
                AttentionFreshness.WATCHDOG_PERIOD_MS,
                AttentionFreshness.WATCHDOG_PERIOD_MS,
                TimeUnit.MILLISECONDS,
            )
        }
        return _faceTrackingEnabled
    }

    /**
     * Release everything this manager owns. Call from the host Activity's
     * teardown — without it the `meow-face-track` executor thread and its 10 Hz
     * freshness watchdog outlive the Activity: nothing shut the executor down,
     * and the watchdog is only cancelled when the user switches tracking off, so
     * a recreate with tracking still on left a live thread plus a scheduled task
     * behind, one set per recreate.
     *
     * Idempotent. After this call the instance is dead: [setFaceTrackingEnabled],
     * [setFollowOnly], [setUseFrontCamera], [setPreviewSurface] and the internal
     * bind all refuse to restart camera work.
     */
    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        _faceTrackingEnabled = false
        trackingSession.stop()
        freshnessWatchdog?.cancel(true)
        freshnessWatchdog = null
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        faceDetector?.close()
        faceDetector = null
        poseDetector?.close()
        poseDetector = null
        stopOrientationTracking()
        unregisterDisplayListener()
        // Last, and never awaited: unbind() clears attention, drops the capture
        // use case and queues unbindAll on the main thread. A lifecycle callback
        // must not block on the camera provider.
        unbind()
        preview?.setSurfaceProvider(null)
        preview = null
        analysisExecutor.shutdownNow()
        android.util.Log.i("VM", "closed: camera unbound, analysis executor shut down")
    }

    /** Called under the manager lock: privacy-off and watchdog expiry cannot race a callback. */
    private fun clearAttention() {
        recentWristX.clear()
        prevLuma = null
        lastMotionOff = null
        lastMotionAtMs = 0L
        motionHits = 0
        lastFollow = FollowSource.FACE
        gazeSettled = false
        lastRealFaceAtMs = 0L
        lastFaceAtMs = 0L
        lastDetectionAtElapsedMs = -1L
        lastPublishAtMs = 0L
        pendingHandCue = SocialCue.NONE
        pendingHandHits = 0
        hasSmooth = false
        hasFrameSmooth = false
        smoothOffX = 0f
        smoothOffY = 0f
        smoothFrameX = 0f
        smoothFrameY = 0f
        smoothYaw = 0f
        smoothPitch = 0f
        smoothRoll = 0f
        smoothScale = 1f
        attentionTarget.reset()
        lastSeenFaces = emptyList()
        _faceTrack.value = null
        _sceneLuma.value = null
    }

    private fun expireAttention(nowMs: Long) {
        if (!trackingSession.active) return
        // Skipping inference leaves CameraX/ISP capturing at full rate. Release
        // the automatic camera while hot; this existing watchdog also resumes
        // it after cooling without changing the user's camera preference.
        if (nowMs - lastThermalReadMs >= 1_000L) {
            thermalStatus = ThermalGovernor.currentStatus()
            lastThermalReadMs = nowMs
        }
        val pause = thermalStatus >= android.os.PowerManager.THERMAL_STATUS_MODERATE
        if (pause != thermalPaused) {
            thermalPaused = pause
            if (pause) unbind() else bindPreviewAsync()
            android.util.Log.i("MeowVision", "automatic camera thermal pause=$pause")
        }
        if (thermalPaused) return
        val track = _faceTrack.value
        val staleTrack = track != null && !AttentionFreshness.isFresh(track.observedAtElapsedMs, nowMs)
        val stalledCamera = lastDetectionAtElapsedMs >= 0L &&
            !AttentionFreshness.isFresh(lastDetectionAtElapsedMs, nowMs)
        if (staleTrack || stalledCamera) clearAttention()
    }

    /**
     * Point [ImageAnalysis] at the display's current rotation.
     *
     * This is the fix for "the head only follows me vertically". CameraX freezes
     * `targetRotation` at the value the display had when the use case was built,
     * and every frame's `rotationDegrees` is measured against it. MainActivity
     * declares `configChanges="orientation|screenLayout|..."` so the Activity is
     * never recreated and CameraX is never rebound — meaning after a rotation
     * (or a fold, where the cover and inner displays have different natural
     * orientations) the boxes arrive in a space 90° off the screen. The user's
     * horizontal movement then drives the avatar's vertical axis.
     *
     * setTargetRotation is explicitly safe to call on a bound use case, so this
     * is a per-frame no-op once the values agree.
     */
    @Synchronized
    private fun syncAnalysisRotation() {
        val analysis = imageAnalysis ?: return
        val rotation = currentDisplayRotation() ?: return
        if (analysis.targetRotation != rotation) {
            trackingSession.invalidate()
            clearAttention()
            analysis.targetRotation = rotation
            android.util.Log.i("VM", "analysis targetRotation -> $rotation")
        }
    }

    private fun readWindowRotation(): Int? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            context.display?.rotation
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay?.rotation
        }
    }.getOrNull()

    private fun currentDisplayRotation(): Int? =
        analyzerTargetRotation(readWindowRotation(), physicalRotation)

    /** Latest physical orientation as a Surface.ROTATION_* value, or null. */
    @Volatile
    private var physicalRotation: Int? = null
    private var pendingPhysical: Int? = null
    private var pendingPhysicalCount = 0

    private var orientationListener: android.view.OrientationEventListener? = null

    private fun startOrientationTracking() {
        if (orientationListener != null) return
        orientationListener = object : android.view.OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                val next = surfaceRotationOf(degrees)
                if (next == physicalRotation) {
                    pendingPhysical = null
                    pendingPhysicalCount = 0
                    return
                }
                if (next == pendingPhysical) {
                    pendingPhysicalCount++
                    if (pendingPhysicalCount >= 4) {
                        physicalRotation = next
                        pendingPhysical = null
                        pendingPhysicalCount = 0
                    }
                } else {
                    pendingPhysical = next
                    pendingPhysicalCount = 1
                }
            }
        }.also { if (it.canDetectOrientation()) it.enable() }
    }

    private fun stopOrientationTracking() {
        orientationListener?.disable()
        orientationListener = null
        physicalRotation = null
    }

    /** Rebind when the fold changes so the user-facing lens follows the display. */
    private fun registerDisplayListener() {
        if (displayListener != null) return
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        lastCoverState = isCoverDisplayActive()
        val l = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                // Fires on rotation too, not just fold — take the cheap
                // rotation resync here so the first frame after a turn is
                // already in the right space.
                syncAnalysisRotation()
                val cover = isCoverDisplayActive()
                if (cover != lastCoverState) {
                    lastCoverState = cover
                    unbind()
                    bindPreviewAsync()
                    android.util.Log.i("VM", "display changed cover=$cover -> rebind")
                }
            }
        }
        dm.registerDisplayListener(l, null)
        displayListener = l
    }

    private fun unregisterDisplayListener() {
        displayListener?.let { l ->
            (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .unregisterDisplayListener(l)
        }
        displayListener = null
    }

    @Synchronized
    private fun analyzeFrame(proxy: ImageProxy, analysis: ImageAnalysis) {
        val detector = faceDetector
        val mediaImage = proxy.image
        if (!trackingSession.active || imageAnalysis !== analysis || detector == null || mediaImage == null) {
            proxy.close()
            return
        }
        // The old skip counter admitted every normal-mode camera frame: device
        // logs showed ~30 face inferences/sec even with no face and a hot phone.
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (nowMs - lastThermalReadMs >= 1000L) {
            thermalStatus = ThermalGovernor.currentStatus()
            lastThermalReadMs = nowMs
        }
        val faceLockHot = lastRealFaceAtMs != 0L &&
            nowMs - lastRealFaceAtMs < 900L
        if (!workBudget.admit(nowMs, faceLockHot, thermalStatus)) {
            proxy.close()
            return
        }
        if (!frameInFlight.compareAndSet(false, true)) {
            proxy.close()
            return
        }
        syncAnalysisRotation()
        val generation = trackingSession.generation
        val observedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        val displayDeg = displayRotationDegrees()
        val cameraRot = proxy.imageInfo.rotationDegrees
        val targetRot = analysis.targetRotation
        val input: InputImage
        val faceTask: com.google.android.gms.tasks.Task<List<Face>>
        try {
            input = InputImage.fromMediaImage(mediaImage, cameraRot)
            faceTask = detector.process(input)
        } catch (e: Exception) {
            frameInFlight.set(false)
            proxy.close()
            android.util.Log.w("MeowVision", "face processing could not start", e)
            return
        }
        // Following a body needs no skeleton: pose is the single most
        // expensive thing in this pipeline and follow mode never reads it.
        val poseTask = if (workBudget.admitPose(nowMs, faceLockHot, thermalStatus, followOnly)) {
            // A pose failure must not close the image while the face task still reads it.
            runCatching { poseDetector?.process(input) }.getOrNull()
        } else {
            null
        }
        val done = if (poseTask != null) Tasks.whenAllComplete(faceTask, poseTask) else faceTask
        done.addOnCompleteListener(analysisExecutor) {
            try {
                val faces = if (faceTask.isSuccessful) {
                    faceTask.result ?: emptyList()
                } else {
                    emptyList()
                }
                val pose = if (poseTask?.isSuccessful == true) poseTask.result else null
                synchronized(this) {
                    if (imageAnalysis === analysis && targetRot == analysis.targetRotation &&
                        displayDeg == displayRotationDegrees() &&
                        trackingSession.accepts(generation, observedAtElapsedMs, android.os.SystemClock.elapsedRealtime())
                    ) {
                        publishDetections(proxy, faces, pose, cameraRot, displayDeg, targetRot, observedAtElapsedMs)
                    }
                }
            } finally {
                frameInFlight.set(false)
                proxy.close()
            }
        }
    }

    private fun publishDetections(
        proxy: ImageProxy,
        faces: List<Face>,
        pose: Pose?,
        cameraRot: Int,
        displayDeg: Int,
        targetRot: Int,
        observedAtElapsedMs: Long,
    ) {
        val now = observedAtElapsedMs
        lastDetectionAtElapsedMs = observedAtElapsedMs
        if (now - lastLogMs >= 2000L) {
            val (dw, dh) = rotatedFrameSize(proxy.width, proxy.height, cameraRot)
            android.util.Log.i(
                "VMrot",
                "cam=${cameraRot} " +
                    "target=${imageAnalysis?.targetRotation} " +
                    "disp=${displayDeg} " +
                    "buf=${proxy.width}x${proxy.height} upright=${dw}x$dh " +
                    "front=$_useFrontCamera faces=${faces.size} pose=${pose != null}",
            )
        }
        val (uprightW, _) = rotatedFrameSize(proxy.width, proxy.height, cameraRot)
        val detections = faces.mapNotNull { f ->
            val off = faceOffsetOnDisplay(
                boxCenterX = f.boundingBox.exactCenterX(),
                boxCenterY = f.boundingBox.exactCenterY(),
                bufferW = proxy.width,
                bufferH = proxy.height,
                rotationDegrees = cameraRot,
                displayDeg = displayDeg,
                mirrored = _useFrontCamera,
                targetRotation = targetRot,
            ) ?: return@mapNotNull null
            SeenFace(
                trackingId = f.trackingId ?: -1,
                offsetX = off.first,
                offsetY = off.second,
                scale = FacePose.scaleFromFaceFraction(
                    f.boundingBox.width().toFloat(),
                    uprightW.toFloat(),
                ),
                mouthOpen = mouthOpenOf(f),
                area = (f.boundingBox.width() * f.boundingBox.height()).toFloat(),
            ) to f
        }.sortedByDescending { it.first.area }.take(3)
        val seen = detections.map { it.first }
        lastSeenFaces = seen
        val pick = attentionTarget.select(seen, speechLikely, now)
        // Pair the exact detection, including unknown (-1) IDs; never silently select a different box.
        val face = pick.face?.let { selected -> detections.firstOrNull { it.first === selected }?.second }
        if (pick.changed) {
            hasSmooth = false
            hasFrameSmooth = false
            gazeSettled = false
        }
        if (face != null) lastRealFaceAtMs = now
        // Drop hasFace immediately so the base halts. Holding hasFace=true
        // with the last edge offset is what kept the robot spinning.
        if (face == null) {
            val last = _faceTrack.value
            if (last != null && last.hasFace) {
                _faceTrack.value = last.copy(
                    hasFace = false,
                    faceCount = faces.size,
                    cue = SocialCue.NONE,
                )
            }
            if (hasSmooth && lastRealFaceAtMs != 0L && now - lastRealFaceAtMs < 180L) {
                return
            }
        }
        val yPlane = proxy.planes.firstOrNull()
        val step = 4
        val sampled = yPlane?.let {
            MotionHand.sampleY(
                it.buffer, proxy.width, proxy.height,
                it.rowStride, it.pixelStride.coerceAtLeast(1), step,
            )
        }
        val sw = proxy.width / step
        val sh = proxy.height / step
        val face01buf = face?.let {
            (it.boundingBox.exactCenterX() / proxy.width.toFloat()) to
                (it.boundingBox.exactCenterY() / proxy.height.toFloat())
        }
        val motion01 = sampled?.let {
            MotionHand.followPoint(
                prevLuma, it, sw, sh,
                origin = face01buf ?: (0.5f to 0.5f),
                exclude = face01buf,
                excludeR = 0.32f,
                minFrac = 0.055f,
            )
        }
        if (sampled != null) {
            prevLuma = sampled
            _sceneLuma.value = MotionHand.meanLuma(sampled)
        }
        val motionOff = motion01?.let { p ->
            pointInDisplay01(
                p.first * proxy.width, p.second * proxy.height,
                proxy.width, proxy.height, cameraRot, displayDeg, _useFrontCamera, targetRot,
            )?.let { d ->
                (d.first * 2f - 1f).coerceIn(-1f, 1f) to (d.second * 2f - 1f).coerceIn(-1f, 1f)
            }
        }
        if (motionOff != null) {
            lastMotionOff = motionOff
            lastMotionAtMs = now
            motionHits++
        } else {
            motionHits = 0
        }
        val heldMotion = lastMotionOff.takeIf { now - lastMotionAtMs < 200L }

        val hands = pose?.let {
            handsFromPose(
                pose = it,
                bufferW = proxy.width,
                bufferH = proxy.height,
                cameraRot = cameraRot,
                displayDeg = displayDeg,
                targetRot = targetRot,
            )
        }.orEmpty()
        val raisedHands = hands.filter { SocialCues.handRaised(it) }
        val hand = raisedHands.minByOrNull { it.wristY } ?: hands.minByOrNull { it.wristY }
        if (hand != null) {
            recentWristX.addLast(hand.wristX)
            while (recentWristX.size > 8) recentWristX.removeFirst()
        } else {
            recentWristX.clear()
        }
        val handMoving = recentWristX.size >= 4 &&
            ((recentWristX.maxOrNull() ?: 0f) - (recentWristX.minOrNull() ?: 0f)) > 0.08f
        // Hand-of-god: a waved hand (pose wrist or luma outside the face)
        // takes the gaze. Two motion hits so talking/blinks don't steal.
        // With a face in frame, a static pose ghost must not yank the head —
        // require a moving raised wrist or luma outside the face box.
        val motionHand = motionHits >= 2
        val raised = hand != null && SocialCues.handRaised(hand)
        val follow = SocialCues.pickFollow(
            handRaised = raised && (face == null || handMoving),
            handMoving = raised && handMoving,
            motionHand = motionHand,
        )
        val faceCue = if (face != null) {
            SocialCues.faceCue(
                smile = face.smilingProbability ?: -1f,
                leftEyeOpen = face.leftEyeOpenProbability ?: -1f,
                rightEyeOpen = face.rightEyeOpenProbability ?: -1f,
            )
        } else {
            SocialCue.NONE
        }
        val rawHandCue = if (hand != null) {
            SocialCues.handSign(hand, recentWristX.toList())
        } else {
            SocialCue.NONE
        }
        val handCue = confirmHandCue(rawHandCue)
        val cue = SocialCues.pickCue(faceCue, handCue)

        val faceOff = face?.let {
            faceOffsetOnDisplay(
                boxCenterX = it.boundingBox.exactCenterX(),
                boxCenterY = it.boundingBox.exactCenterY(),
                bufferW = proxy.width,
                bufferH = proxy.height,
                rotationDegrees = cameraRot,
                displayDeg = displayDeg,
                mirrored = _useFrontCamera,
                targetRotation = targetRot,
            )
        }
        val handOff = hand?.let { SocialCues.followOffset(it) }
        val raw = when {
            follow == FollowSource.HAND && motionOff != null -> motionOff
            follow == FollowSource.HAND && heldMotion != null -> heldMotion
            follow == FollowSource.HAND && handOff != null -> handOff
            faceOff != null -> faceOff
            else -> null
        }

        if (raw == null) {
            // Hold last pose, then clear — do not drag to zero while "following"
            // (that fought the user and felt unresponsive).
            if (hasSmooth && now - lastFaceAtMs < 500L) return
            hasSmooth = false
            hasFrameSmooth = false
            gazeSettled = false
            smoothOffX = 0f
            smoothOffY = 0f
            smoothFrameX = 0f
            smoothFrameY = 0f
            smoothYaw = 0f
            smoothPitch = 0f
            smoothRoll = 0f
            smoothScale = 1f
            _faceTrack.value = null
            if (now - lastLogMs >= 2000L) {
                lastLogMs = now
                android.util.Log.i("VM", "noface clear")
            }
            return
        }

        val (rawX, rawY) = raw
        val a = when {
            follow != lastFollow -> 0.78f
            follow == FollowSource.HAND -> handSmoothingAlpha(hasSmooth)
            else -> FaceAttentionSmoothing.alpha(now - lastFaceAtMs,
                kotlin.math.hypot(rawX - smoothOffX, rawY - smoothOffY), hasSmooth)
        }
        lastFollow = follow
        smoothOffX = if (hasSmooth) smoothFaceOffset(smoothOffX, rawX, a) else rawX
        smoothOffY = if (hasSmooth) smoothFaceOffset(smoothOffY, rawY, a) else rawY
        if (faceOff != null) {
            val fa = FaceAttentionSmoothing.alpha(now - lastFaceAtMs,
                kotlin.math.hypot(faceOff.first - smoothFrameX, faceOff.second - smoothFrameY), hasFrameSmooth)
            smoothFrameX = if (hasFrameSmooth) {
                smoothFaceOffset(smoothFrameX, faceOff.first, fa)
            } else {
                faceOff.first
            }
            smoothFrameY = if (hasFrameSmooth) {
                smoothFaceOffset(smoothFrameY, faceOff.second, fa)
            } else {
                faceOff.second
            }
            hasFrameSmooth = true
        }
        gazeSettled = GazeSettle.apply(smoothOffX, smoothOffY, gazeSettled, follow).settled
        // Publish the live box. GazeSettle is an avatar-fidget filter applied
        // at the gaze sink — feeding 0,0 to motors made the neck never move.
        val pubX = smoothOffX.coerceIn(-1f, 1f)
        val pubY = smoothOffY.coerceIn(-1f, 1f)
        val (w, _) = rotatedFrameSize(proxy.width, proxy.height, cameraRot)
        val rawScale = if (face != null) {
            FacePose.scaleFromFaceFraction(face.boundingBox.width().toFloat(), w.toFloat())
        } else {
            1f
        }
        // Box-only follow. ML Kit eulers fidget a locked face (and go
        // sideways on the cover in landscape). The avatar already looks
        // at the remapped box; extra 6DOF is the twitch.
        smoothYaw = 0f
        smoothPitch = 0f
        smoothRoll = 0f
        if (!hasSmooth) {
            smoothScale = rawScale
        } else if (!gazeSettled) {
            smoothScale = smoothFaceOffset(smoothScale, rawScale, a)
        }
        hasSmooth = true
        lastFaceAtMs = now
        val pub = _faceTrack.value
        val due = now - lastPublishAtMs >= 20L
        if (pub == null || due || pub.cue != cue || pub.followSource != follow) {
            lastPublishAtMs = now
            _faceTrack.value = FaceTrack(
                offsetX = pubX,
                offsetY = pubY,
                yawDeg = 0f,
                pitchDeg = 0f,
                rollDeg = 0f,
                scale = smoothScale,
                confidence = if (face != null) 0.9f else 0.7f,
                cue = cue,
                followSource = follow,
                hasFace = face != null,
                trackingId = face?.trackingId ?: -1,
                faceCount = faces.size,
                mouthOpen = face?.let { mouthOpenOf(it) } ?: 0f,
                frameX = if (hasFrameSmooth) smoothFrameX.coerceIn(-1f, 1f) else pubX,
                frameY = if (hasFrameSmooth) smoothFrameY.coerceIn(-1f, 1f) else pubY,
                observedAtElapsedMs = observedAtElapsedMs,
            )
            if (now - lastLogMs >= 2000L) {
                lastLogMs = now
                android.util.Log.i(
                    "VM",
                    "pub x=$pubX y=$pubY " +
                        "raw=${smoothOffX}/${smoothOffY} settled=$gazeSettled " +
                        "scale=${smoothScale} cue=$cue follow=$follow " +
                        "hands=${hands.size} motion=${motionOff != null} faces=${faces.size}",
                )
            }
        } else if (now - lastLogMs >= 2000L) {
            lastLogMs = now
            android.util.Log.i("VM", "face nopub")
        }
    }

    private fun mouthOpenOf(face: Face): Float {
        val left = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return 0f
        val right = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return 0f
        val bottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position ?: return 0f
        return TalkerPicker.mouthOpenRatio(
            mouthLeftY = left.y,
            mouthRightY = right.y,
            mouthBottomY = bottom.y,
            faceHeight = face.boundingBox.height().toFloat(),
        )
    }

    private fun handsFromPose(
        pose: Pose,
        bufferW: Int,
        bufferH: Int,
        cameraRot: Int,
        displayDeg: Int,
        targetRot: Int,
    ): List<HandSample> {
        val out = ArrayList<HandSample>(2)
        for (left in listOf(true, false)) {
            val wristLm = pose.getPoseLandmark(
                if (left) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST,
            )
            val elbowLm = pose.getPoseLandmark(
                if (left) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW,
            )
            if (wristLm == null || wristLm.inFrameLikelihood < 0.20f) continue
            val wrist = pointInDisplay01(
                wristLm.position.x, wristLm.position.y,
                bufferW, bufferH, cameraRot, displayDeg, _useFrontCamera, targetRot,
            ) ?: continue
            val elbow = if (elbowLm != null && elbowLm.inFrameLikelihood >= 0.20f) {
                pointInDisplay01(
                    elbowLm.position.x, elbowLm.position.y,
                    bufferW, bufferH, cameraRot, displayDeg, _useFrontCamera, targetRot,
                )
            } else {
                null
            } ?: (wrist.first to (wrist.second + 0.22f).coerceAtMost(1f))
            fun tip(kind: Int): Pair<Float, Float>? {
                val lm = pose.getPoseLandmark(kind) ?: return null
                if (lm.inFrameLikelihood < 0.35f) return null
                return pointInDisplay01(
                    lm.position.x, lm.position.y,
                    bufferW, bufferH, cameraRot, displayDeg, _useFrontCamera, targetRot,
                )
            }
            val thumb = tip(if (left) PoseLandmark.LEFT_THUMB else PoseLandmark.RIGHT_THUMB)
            val index = tip(if (left) PoseLandmark.LEFT_INDEX else PoseLandmark.RIGHT_INDEX)
            val pinky = tip(if (left) PoseLandmark.LEFT_PINKY else PoseLandmark.RIGHT_PINKY)
            out += HandSample(
                wristX = wrist.first.coerceIn(0f, 1f),
                wristY = wrist.second.coerceIn(0f, 1f),
                elbowX = elbow.first.coerceIn(0f, 1f),
                elbowY = elbow.second.coerceIn(0f, 1f),
                thumbX = thumb?.first,
                thumbY = thumb?.second,
                indexX = index?.first,
                indexY = index?.second,
                pinkyX = pinky?.first,
                pinkyY = pinky?.second,
            )
        }
        return out
    }

    /** Hand signs flicker on a close-up pose — need two matching frames. */
    private fun confirmHandCue(cue: SocialCue): SocialCue {
        if (cue == SocialCue.NONE) {
            pendingHandCue = SocialCue.NONE
            pendingHandHits = 0
            return SocialCue.NONE
        }
        if (cue == pendingHandCue) {
            pendingHandHits++
        } else {
            pendingHandCue = cue
            pendingHandHits = 1
        }
        return if (pendingHandHits >= 2) cue else SocialCue.NONE
    }

    /**
     * Show or hide the live camera preview. The Preview use case is created
     * eagerly with the surface provider so that it is ready whenever the
     * camera is bound (on first capture or immediately if already bound).
     */
    fun setPreviewSurface(provider: Preview.SurfaceProvider?) {
        if (provider != null) {
            val p = preview ?: Preview.Builder().build().also { preview = it }
            p.setSurfaceProvider(provider)
            bindPreviewAsync()
        } else {
            preview?.setSurfaceProvider(null)
            unbind()
        }
    }

    /**
     * Capture one frame as downscaled JPEG (max [maxDimension] px on the long
     * edge, quality 80) — small enough for vision-model context limits.
     * Prefers the back camera, falls back to front.
     */
    suspend fun captureJpeg(maxDimension: Int = 896): Result<ByteArray> {
        if (!hasPermission()) {
            return Result.failure(Exception("Camera permission not granted"))
        }
        val capture = try {
            bindIfNeeded()
        } catch (e: Exception) {
            return Result.failure(Exception("camera unavailable: ${e.message}"))
        }
        return try {
            val jpeg = takePicture(capture)
            Result.success(downscaleToJpeg(jpeg, maxDimension))
        } catch (e: Exception) {
            // One rebind retry: a stale session after suspend is the usual cause
            try {
                unbind()
                val fresh = bindIfNeeded()
                Result.success(downscaleToJpeg(takePicture(fresh), maxDimension))
            } catch (e2: Exception) {
                Result.failure(Exception("capture failed: ${e2.message ?: e.message}"))
            }
        }
    }

    /**
     * Bind the live preview (and any existing ImageCapture / ImageAnalysis) on
     * the main thread asynchronously. Safe to call from any thread — queues the
     * bind via the CameraX provider listener.
     */
    private fun bindPreviewAsync() {
        // Also the guard for the thermal-pause resume path in expireAttention():
        // once closed, nothing may rebind a camera, even from a stale watchdog.
        if (closed) return
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    val provider = future.get()
                    thermalStatus = ThermalGovernor.currentStatus()
                    thermalPaused = thermalStatus >= android.os.PowerManager.THERMAL_STATUS_MODERATE
                    provider.unbindAll()
                    if (thermalPaused) { clearAttention(); return@addListener }
                    val selector = resolveSelector(provider)
                    val useCases = mutableListOf<androidx.camera.core.UseCase>()
                    preview?.let { p -> p.surfaceProvider?.let { useCases.add(p) } }
                    imageCapture?.let { useCases.add(it) }
                    imageAnalysis?.let { useCases.add(it) }
                    if (useCases.isNotEmpty()) {
                        provider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
                        boundSelector = selector
                    }
                } catch (e: Exception) {
                    android.util.Log.w("VisionManager", "bindPreviewAsync failed: ${e.message}", e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private suspend fun bindIfNeeded(): ImageCapture {
        if (ThermalGovernor.currentStatus() >= android.os.PowerManager.THERMAL_STATUS_MODERATE) {
            throw ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "Camera paused while cooling", null)
        }
        imageCapture?.let { return it }
        val provider = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    { cont.resume(future.get()) },
                    ContextCompat.getMainExecutor(context)
                )
            }
        } ?: throw ImageCaptureException(
            ImageCapture.ERROR_UNKNOWN, "camera provider unavailable after 10s", null,
        )
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val selector = resolveSelector(provider)
        provider.unbindAll()
        val useCases = mutableListOf<androidx.camera.core.UseCase>(capture)
        preview?.let { p -> p.surfaceProvider?.let { useCases.add(p) } }
        imageAnalysis?.let { useCases.add(it) }
        provider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
        imageCapture = capture
        boundSelector = selector
        return capture
    }

    private fun resolveSelector(provider: ProcessCameraProvider): CameraSelector {
        if (!_useFrontCamera) {
            return when {
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> throw IllegalStateException("no camera on this device")
            }
        }
        // Front camera choice matters on foldables: the lens must face the ACTIVE
        // display. Closed, the app runs on the cover screen and the inner front
        // camera faces the folded panel — black frames, ML Kit finds no face —
        // while the cover front camera sees the user. Prefer a non-default front
        // camera whenever a secondary display (the cover) is switched on.
        if (isCoverDisplayActive()) {
            val defaultFrontId = provider.availableCameraInfos
                .firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_FRONT }
                ?.let { info -> runCatching { Camera2CameraInfo.from(info).cameraId }.getOrNull() }
            if (defaultFrontId != null) {
                val coverSelector = CameraSelector.Builder()
                    .addCameraFilter { candidates ->
                        candidates.filter { c ->
                            c.lensFacing == CameraSelector.LENS_FACING_FRONT &&
                                runCatching { Camera2CameraInfo.from(c).cameraId }.getOrNull() != defaultFrontId
                        }
                    }
                    .build()
                if (provider.hasCamera(coverSelector)) {
                    android.util.Log.i("VM", "cover display on -> cover front camera (default id=$defaultFrontId)")
                    return coverSelector
                }
            }
        }
        return when {
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> throw IllegalStateException("no camera on this device")
        }
    }

    /**
     * True when the active display is a tall cover screen. On Samsung folds the
     * cover becomes display 0 when closed, so "secondary display on" does not
     * work — the cover is identified by its portrait aspect (the inner screen is
     * near-square). Non-fold phones are also portrait but have one front camera,
     * so the wrong pick degrades to the default front camera anyway.
     */
    private fun isCoverDisplayActive(): Boolean {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val active = dm.displays.firstOrNull { it.state == Display.STATE_ON } ?: return false
        val size = android.graphics.Point()
        active.getRealSize(size)
        val minSide = minOf(size.x, size.y).toFloat()
        val maxSide = maxOf(size.x, size.y).toFloat()
        if (maxSide <= 0f) return false
        val cover = minSide / maxSide < 0.65f
        android.util.Log.i("VM", "active display=${active.displayId} ${size.x}x${size.y} aspect=${minSide / maxSide} cover=$cover")
        return cover
    }

    /**
     * Rotation of the display the user is looking at, in degrees.
     * Used to remap sensor-space boxes when CameraX reports rotationDegrees=0
     * in landscape (Fold cover: target=ROTATION_90, rot stays 0).
     */
    private fun displayRotationDegrees(): Int {
        val rot = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.display?.rotation
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                    .defaultDisplay?.rotation
            }
        }.getOrNull() ?: return 0
        return when (rot) {
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    @Synchronized
    private fun unbind() {
        trackingSession.invalidate()
        clearAttention()
        imageCapture = null
        boundSelector = null
        // Never block the calling (main) thread on the provider future — a stale
        // camera session must not wedge the crash-alert coroutine. Queue the
        // unbind via the provider's listener instead.
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { runCatching { future.get().unbindAll() } },
            ContextCompat.getMainExecutor(context)
        )
    }

    private suspend fun takePicture(capture: ImageCapture): ByteArray {
        // The capture must never hang the caller: if the camera session stalls
        // (stale after suspend, folded cover, sensor busy) fail loudly instead of
        // leaving the crash-alert coroutine blocked forever.
        val jpeg = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<ByteArray> { cont ->
                capture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                val rotation = image.imageInfo.rotationDegrees
                                image.close()
                                cont.resume(rotateJpeg(bytes, rotation))
                            } catch (e: Exception) {
                                image.close()
                                cont.resumeWith(Result.failure(e))
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            cont.resumeWith(Result.failure(exception))
                        }
                    }
                )
            }
        } ?: throw ImageCaptureException(
            ImageCapture.ERROR_UNKNOWN, "camera capture timed out after 10s", null,
        )
        return jpeg
    }

    private fun rotateJpeg(bytes: ByteArray, degrees: Int): ByteArray {
        if (degrees == 0) return bytes
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val rotated = Bitmap.createBitmap(
            bmp, 0, 0, bmp.width, bmp.height,
            Matrix().apply { postRotate(degrees.toFloat()) }, true
        )
        val out = ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    private fun downscaleToJpeg(bytes: ByteArray, maxDimension: Int): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return bytes
        val longEdge = maxOf(bmp.width, bmp.height)
        if (longEdge <= maxDimension) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            return out.toByteArray()
        }
        val scale = maxDimension.toFloat() / longEdge
        val scaled = Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * scale).roundToInt(),
            (bmp.height * scale).roundToInt(),
            true
        )
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }
}
