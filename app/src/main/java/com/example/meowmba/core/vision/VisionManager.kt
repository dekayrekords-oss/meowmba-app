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
 * One face-detection frame. Offsets are normalized to -1..1 relative to the
 * frame center: negative X = subject left of the frame, negative Y = above
 * center. [confidence] is the detector's probability that this is a real face.
 * Rotation (yaw/pitch/roll) and distance ([scale]) give the avatar six degrees
 * of freedom to react with.
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
        registerDisplayListener()
        return _faceTrackingEnabled
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
        val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                val now = System.currentTimeMillis()
                if (face != null) {
                    val is90 = (proxy.imageInfo.rotationDegrees % 180) != 0
                    val w = if (is90) proxy.height else proxy.width
                    val h = if (is90) proxy.width else proxy.height
                    if (w <= 0 || h <= 0) return@addOnSuccessListener
                    val cxU = face.boundingBox.exactCenterX() / w.toFloat()
                    val cyU = face.boundingBox.exactCenterY() / h.toFloat()
                    // ML Kit reports the box in the camera's upright frame, which
                    // follows the SENSOR — it does not change when the phone rotates
                    // to landscape, but the display does. Remap into display space so
                    // horizontal head motion stays horizontal on screen; identity in
                    // portrait (measured on device: rot=90 in landscape, upright
                    // frame stays 240x320 while the display is 320x240 — axes swap).
                    val (sx, sy) = FaceFrameMap.toDisplay(cxU, cyU, displayRotationDegrees())
                    var rawX = (sx * 2f - 1f).coerceIn(-1f, 1f)
                    // Front camera is mirrored — flip X so +X is the user's right
                    // on screen, matching how the avatar preview renders.
                    if (_useFrontCamera) rawX = -rawX
                    val rawY = (sy * 2f - 1f).coerceIn(-1f, 1f)
                    // Medium EMA: follows clearly, still damps ML Kit box noise.
                    // (0.10 + center-bias was so strong following looked dead.)
                    val a = if (hasSmooth) 0.32f else 0.65f
                    val targetX = smoothOffX * (1f - a) + rawX * a
                    val targetY = smoothOffY * (1f - a) + rawY * a
                    // Rotation + distance get the same EMA, so the head reacts
                    // smoothly instead of stepping per frame.
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
                    val targetYaw = smoothYaw * (1f - a) + yaw * a
                    val targetPitch = smoothPitch * (1f - a) + rawPitch * a
                    val targetRoll = smoothRoll * (1f - a) + rawRoll * a
                    val targetScale = smoothScale * (1f - a) + rawScale * a
                    val maxStep = 0.14f
                    smoothOffX += (targetX - smoothOffX).coerceIn(-maxStep, maxStep)
                    smoothOffY += (targetY - smoothOffY).coerceIn(-maxStep, maxStep)
                    val maxRotStep = 4f
                    smoothYaw += (targetYaw - smoothYaw).coerceIn(-maxRotStep, maxRotStep)
                    smoothPitch += (targetPitch - smoothPitch).coerceIn(-maxRotStep, maxRotStep)
                    smoothRoll += (targetRoll - smoothRoll).coerceIn(-maxRotStep, maxRotStep)
                    smoothScale += (targetScale - smoothScale).coerceIn(-0.05f, 0.05f)
                    hasSmooth = true
                    lastFaceAtMs = now
                    val pub = _faceTrack.value
                    val dx = if (pub == null) 1f else kotlin.math.abs(smoothOffX - pub.offsetX)
                    val dy = if (pub == null) 1f else kotlin.math.abs(smoothOffY - pub.offsetY)
                    val dRot = if (pub == null) 1f else maxOf(
                        kotlin.math.abs(smoothYaw - pub.yawDeg),
                        kotlin.math.abs(smoothPitch - pub.pitchDeg),
                        kotlin.math.abs(smoothRoll - pub.rollDeg),
                    )
                    val dScale = if (pub == null) 1f else
                        kotlin.math.abs(smoothScale - pub.scale)
                    val due = now - lastPublishAtMs >= 40L
                    val moved = dx > 0.012f || dy > 0.012f ||
                        dRot > 0.8f || dScale > 0.03f
                    if (pub == null || (due && moved)) {
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

    /** Default display rotation in degrees (0/90/180/270), for face-frame remap. */
    private fun displayRotationDegrees(): Int {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return 0
        // Surface.ROTATION_*: 0=0deg, 1=90deg, 2=180deg, 3=270deg.
        return when (dm.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
            1 -> 90
            2 -> 180
            3 -> 270
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
