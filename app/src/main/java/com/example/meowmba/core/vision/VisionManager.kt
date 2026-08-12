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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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

internal fun faceSmoothingAlpha(hasHistory: Boolean): Float = if (hasHistory) 0.32f else 0.65f

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
internal fun faceOffsetOnDisplay(
    boxCenterX: Float,
    boxCenterY: Float,
    bufferW: Int,
    bufferH: Int,
    rotationDegrees: Int,
    displayDeg: Int,
    mirrored: Boolean,
): Pair<Float, Float>? {
    val (w, h) = rotatedFrameSize(bufferW, bufferH, rotationDegrees)
    if (w <= 0 || h <= 0) return null
    var fx = boxCenterX / w.toFloat()
    var fy = boxCenterY / h.toFloat()
    val rot = ((rotationDegrees % 360) + 360) % 360
    val disp = ((displayDeg % 360) + 360) % 360
    if (rot == 0 && disp != 0) {
        val mapped = FaceFrameMap.toDisplay(fx, fy, disp)
        fx = mapped.first
        fy = mapped.second
    }
    var rawX = (fx * 2f - 1f).coerceIn(-1f, 1f)
    val rawY = (fy * 2f - 1f).coerceIn(-1f, 1f)
    if (mirrored) rawX = -rawX
    return rawX to rawY
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
    private var boundSelector: CameraSelector? = null
    private var _useFrontCamera = false
    private var _faceTrackingEnabled = false
    private var lastCoverState = false
    private var displayListener: DisplayManager.DisplayListener? = null
    private val frameInFlight = AtomicBoolean(false)
    // NEVER analyze on the main thread — ML Kit + ImageProxy on main = UI jank.
    private val analysisExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "meow-face-track").apply { isDaemon = true }
    }

    // Smoothed gaze (EMA) + short hold so lost-face frames do not null the stream.
    private var smoothOffX = 0f
    private var smoothOffY = 0f
    private var smoothYaw = 0f
    private var smoothPitch = 0f
    private var smoothRoll = 0f
    private var smoothScale = 1f
    private var lastFaceAtMs = 0L
    private var hasSmooth = false
    private var lastLogMs = 0L
    private var lastPublishAtMs = 0L
    private var analyzeSkip = 0

    private val _faceTrack = MutableStateFlow<FaceTrack?>(null)

    /** Latest face-track result; null when no face is in frame. */
    val faceTrack: StateFlow<FaceTrack?> = _faceTrack.asStateFlow()

    val isFrontCamera: Boolean get() = _useFrontCamera
    val isFaceTrackingEnabled: Boolean get() = _faceTrackingEnabled

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
     * Start or stop continuous face tracking. When enabled the front camera is
     * used and each frame's largest face is published to [faceTrack].
     * @return true if tracking is actually running (permission + bound), false if
     * disabled or blocked on missing CAMERA permission.
     */
    fun setFaceTrackingEnabled(enabled: Boolean): Boolean {
        android.util.Log.i("VM", "setFaceTrackingEnabled($enabled) cur=$_faceTrackingEnabled perm=${hasPermission()}")
        if (enabled == _faceTrackingEnabled) {
            return _faceTrackingEnabled && hasPermission()
        }
        if (enabled && !hasPermission()) {
            // Do not sticky-enable without permission — caller will re-request.
            _faceTrackingEnabled = false
            return false
        }
        _faceTrackingEnabled = enabled
        if (enabled) {
            _useFrontCamera = true
            // Keep a capture use-case warm so "look"/"see me" does not fight a
            // rebind race against the analysis-only session.
            if (imageCapture == null) {
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
            }
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .build()
            faceDetector = FaceDetection.getClient(options)
            // Prefer ~QVGA analysis. setTargetResolution is ignored on modern
            // CameraX (phone was binding 1088x1088 and thrashing the UI thread).
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(320, 240),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy ->
                analyzeFrame(proxy)
            }
            imageAnalysis = analysis
        } else {
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            faceDetector?.close()
            faceDetector = null
            _faceTrack.value = null
            hasSmooth = false
            // keep executor alive for re-enable; process teardown reclaims it
            smoothOffX = 0f
            smoothOffY = 0f
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
        return _faceTrackingEnabled
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
    private fun syncAnalysisRotation() {
        val analysis = imageAnalysis ?: return
        val rotation = currentDisplayRotation() ?: return
        if (analysis.targetRotation != rotation) {
            analysis.targetRotation = rotation
            android.util.Log.i("VM", "analysis targetRotation -> $rotation")
        }
    }

    /**
     * Rotation to hand the analyzer: the device's PHYSICAL orientation, falling
     * back to the display's.
     *
     * Faces are upright with respect to GRAVITY, not with respect to the
     * window manager. Driving this from `display.rotation` fails whenever the
     * two disagree, and on this app they disagree often: Meowmba can be the
     * home launcher, it declares `configChanges` for orientation, and it can be
     * docked or rotation-locked. Measured with the display forced to landscape
     * while the phone lay flat, same scene seconds apart — portrait applied
     * rot=270 and found a face; landscape applied rot=0, handed ML Kit an
     * unrotated frame, and found none. A sideways face is invisible to the FAST
     * detector, so tracking simply stopped.
     *
     * [physicalRotation] is maintained by an OrientationEventListener, which is
     * the orientation the camera actually experiences.
     */
    private fun currentDisplayRotation(): Int? =
        physicalRotation ?: runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.display?.rotation
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                    .defaultDisplay?.rotation
            }
        }.getOrNull()

    /** Latest physical orientation as a Surface.ROTATION_* value, or null. */
    @Volatile
    private var physicalRotation: Int? = null

    private var orientationListener: android.view.OrientationEventListener? = null

    private fun startOrientationTracking() {
        if (orientationListener != null) return
        orientationListener = object : android.view.OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                physicalRotation = surfaceRotationOf(degrees)
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

    private fun analyzeFrame(proxy: ImageProxy) {
        val detector = faceDetector
        val mediaImage = proxy.image
        if (detector == null || mediaImage == null) {
            proxy.close()
            return
        }
        // ~1/2 of frames — the follow target refreshes ~2x faster, so the head
        // chases the user instead of the 5Hz-steppy lag the every-3rd skip caused.
        analyzeSkip++
        if (analyzeSkip % 2 != 0) {
            proxy.close()
            return
        }
        // KEEP_ONLY_LATEST already drops backlog; a detector still busy on the
        // previous frame means we skip this one rather than queue up faces.
        if (!frameInFlight.compareAndSet(false, true)) {
            proxy.close()
            return
        }
        // Match the analysis rotation to the display BEFORE detecting, and do it
        // whether or not a face is in frame — otherwise the correction only
        // lands once someone is already being tracked, and the first frames of
        // every new face arrive in the stale space.
        syncAnalysisRotation()
        val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener { faces ->
                // Orientation diagnostic. "Tracks only in portrait" has two very
                // different causes that look identical from outside: the frame
                // reaching ML Kit rotated wrongly (so an upright face arrives
                // sideways and the FAST detector cannot see it), or simply
                // nobody being in shot. Logging the rotation actually applied,
                // the frame it produced, and the face count separates them in
                // one test. Rate-limited to one line every two seconds.
                if (System.currentTimeMillis() - lastLogMs >= 2000L) {
                    val (dw, dh) = rotatedFrameSize(
                        proxy.width, proxy.height, proxy.imageInfo.rotationDegrees,
                    )
                    android.util.Log.i(
                        "VMrot",
                        "rot=${proxy.imageInfo.rotationDegrees} " +
                            "target=${imageAnalysis?.targetRotation} " +
                            "disp=${displayRotationDegrees()} " +
                            "buf=${proxy.width}x${proxy.height} upright=${dw}x$dh " +
                            "front=$_useFrontCamera faces=${faces.size}",
                    )
                }
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                val now = System.currentTimeMillis()
                if (face != null) {
                    val rotDeg = proxy.imageInfo.rotationDegrees
                    val (w, h) = rotatedFrameSize(proxy.width, proxy.height, rotDeg)
                    val (rawX, rawY) = faceOffsetOnDisplay(
                        boxCenterX = face.boundingBox.exactCenterX(),
                        boxCenterY = face.boundingBox.exactCenterY(),
                        bufferW = proxy.width,
                        bufferH = proxy.height,
                        rotationDegrees = rotDeg,
                        displayDeg = displayRotationDegrees(),
                        mirrored = _useFrontCamera,
                    ) ?: return@addOnSuccessListener
                    // Medium EMA: follows clearly, still damps ML Kit box noise.
                    // (0.10 + center-bias was so strong following looked dead.)
                    // No maxStep clamp — that turned fast motion into a staircase.
                    val a = faceSmoothingAlpha(hasSmooth)
                    smoothOffX = smoothFaceOffset(smoothOffX, rawX, a)
                    smoothOffY = smoothFaceOffset(smoothOffY, rawY, a)
                    val rawYaw = face.headEulerAngleY
                    val rawPitch = face.headEulerAngleX
                    val rawRoll = face.headEulerAngleZ
                    val rawScale = FacePose.scaleFromFaceFraction(
                        face.boundingBox.width().toFloat(), w.toFloat()
                    )
                    // Front camera is mirrored — flip yaw to match the offsetX
                    // convention (+ = user's right on screen). Roll survives a
                    // mirror (clockwise stays clockwise); pitch is unaffected.
                    val yaw = if (_useFrontCamera) -rawYaw else rawYaw
                    smoothYaw = smoothFaceOffset(smoothYaw, yaw, a)
                    smoothPitch = smoothFaceOffset(smoothPitch, rawPitch, a)
                    smoothRoll = smoothFaceOffset(smoothRoll, rawRoll, a)
                    smoothScale = smoothFaceOffset(smoothScale, rawScale, a)
                    hasSmooth = true
                    lastFaceAtMs = now
                    val pub = _faceTrack.value
                    // Publish on a STEADY cadence, with no movement threshold.
                    //
                    // There used to be a dead-band here (and an identical one
                    // again in the ViewModel, so a sample had to clear 0.012
                    // twice). Between them, moving slowly published nothing at
                    // all until the accumulated drift finally crossed the line
                    // and the head jumped — the "erratic tracking" was the
                    // renderer's spring being handed a staircase and faithfully
                    // reproducing it. The spring wants an even sample stream to
                    // integrate; 25Hz of that is trivial, and StateFlow already
                    // conflates a value identical to the last one.
                    val due = now - lastPublishAtMs >= 40L
                    if (pub == null || due) {
                        lastPublishAtMs = now
                        _faceTrack.value = FaceTrack(
                            offsetX = smoothOffX.coerceIn(-1f, 1f),
                            offsetY = smoothOffY.coerceIn(-1f, 1f),
                            yawDeg = smoothYaw.coerceIn(-30f, 30f),
                            pitchDeg = smoothPitch.coerceIn(-30f, 30f),
                            rollDeg = smoothRoll.coerceIn(-30f, 30f),
                            scale = smoothScale,
                            confidence = 0.9f,
                        )
                        if (now - lastLogMs >= 2000L) {
                            lastLogMs = now
                            android.util.Log.i(
                                "VM", "pub x=${smoothOffX} y=${smoothOffY} " +
                                    "yaw=${smoothYaw} pitch=${smoothPitch} " +
                                    "roll=${smoothRoll} scale=${smoothScale}"
                            )
                        }
                    } else if (now - lastLogMs >= 2000L) {
                        lastLogMs = now
                        android.util.Log.i("VM", "face nopub")
                    }
                } else {
                    // Hold last pose, then clear — do not drag to zero while "following"
                    // (that fought the user and felt unresponsive).
                    if (hasSmooth && now - lastFaceAtMs < 500L) {
                        // keep last published value
                    } else {
                        hasSmooth = false
                        smoothOffX = 0f
                        smoothOffY = 0f
                        smoothYaw = 0f
                        smoothPitch = 0f
                        smoothRoll = 0f
                        smoothScale = 1f
                        _faceTrack.value = null
                        if (now - lastLogMs >= 2000L) {
                            lastLogMs = now
                            android.util.Log.i("VM", "noface clear")
                        }
                    }
                }
            }
            .addOnFailureListener {
                val now = System.currentTimeMillis()
                if (!(hasSmooth && now - lastFaceAtMs < 700L)) {
                    hasSmooth = false
                    smoothOffX = 0f
                    smoothOffY = 0f
                    smoothYaw = 0f
                    smoothPitch = 0f
                    smoothRoll = 0f
                    smoothScale = 1f
                    _faceTrack.value = null
                }
            }
            .addOnCompleteListener {
                frameInFlight.set(false)
                proxy.close()
            }
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
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    val provider = future.get()
                    val selector = resolveSelector(provider)
                    provider.unbindAll()
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

    private fun unbind() {
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
