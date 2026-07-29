package com.example.meowmba.core.vision

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * CameraX frame capture for the vision path (Phase 7a).
 *
 * Bound lazily on the first "look" and kept warm afterwards. Capture-only
 * (no preview use case) — the companion looks on demand; a persistent
 * viewfinder and face-follow are P7b.
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
    private var boundSelector: CameraSelector? = null
    private var _useFrontCamera = false

    val isFrontCamera: Boolean get() = _useFrontCamera

    fun setUseFrontCamera(useFront: Boolean) {
        if (useFront == _useFrontCamera) return
        _useFrontCamera = useFront
        unbind()
        bindPreviewAsync()
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

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
     * Bind the live preview (and any existing ImageCapture) on the main thread
     * asynchronously. Safe to call from any thread — queues the bind via the
     * CameraX provider listener.
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
                    if (useCases.isNotEmpty()) {
                        provider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
                        boundSelector = selector
                    }
                } catch (_: Exception) {
                    // Camera unavailable — next capture attempt will surface the error
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private suspend fun bindIfNeeded(): ImageCapture {
        imageCapture?.let { return it }
        val provider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { cont.resume(future.get()) },
                ContextCompat.getMainExecutor(context)
            )
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val selector = resolveSelector(provider)
        provider.unbindAll()
        val useCases = mutableListOf<androidx.camera.core.UseCase>(capture)
        preview?.let { p -> p.surfaceProvider?.let { useCases.add(p) } }
        provider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
        imageCapture = capture
        boundSelector = selector
        return capture
    }

    private fun resolveSelector(provider: ProcessCameraProvider): CameraSelector =
        if (_useFrontCamera) {
            when {
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                else -> throw IllegalStateException("no camera on this device")
            }
        } else {
            when {
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> throw IllegalStateException("no camera on this device")
            }
        }

    private fun unbind() {
        imageCapture = null
        boundSelector = null
        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
    }

    private suspend fun takePicture(capture: ImageCapture): ByteArray =
        suspendCancellableCoroutine { cont ->
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
