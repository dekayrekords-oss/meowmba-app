package com.example.meowmba.vision

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * CameraX analyzer tracking face / person presence and movement in real-time.
 * Computes normalized center coordinates (gazeX, gazeY) in [-1.0, 1.0] and head tilt
 * to dynamically react on the avatar face.
 */
class CameraXFaceTracker(
    private val onGazeTargetUpdated: (gazeX: Float, gazeY: Float, headTiltDeg: Float) -> Unit
) : ImageAnalysis.Analyzer {

    private var smoothedGazeX = 0f
    private var smoothedGazeY = 0f
    private val alpha = 0.18f

    override fun analyze(imageProxy: ImageProxy) {
        val plane = imageProxy.planes.firstOrNull()
        if (plane == null) {
            imageProxy.close()
            return
        }

        val buffer: ByteBuffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        val width = imageProxy.width
        val height = imageProxy.height

        // Calculate luminance center of mass to track movement target
        var totalLum = 0L
        var sumX = 0L
        var sumY = 0L
        val step = 8 // Subsample every 8th pixel for speed

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val index = y * width + x
                if (index < data.size) {
                    val lum = data[index].toInt() and 0xFF
                    totalLum += lum
                    sumX += x * lum
                    sumY += y * lum
                }
            }
        }

        if (totalLum > 0) {
            val avgX = (sumX / totalLum).toFloat()
            val avgY = (sumY / totalLum).toFloat()

            val rawGazeX = (avgX - (width / 2f)) / (width / 2f)
            val rawGazeY = (avgY - (height / 2f)) / (height / 2f)
            val tilt = rawGazeX * 12f // Tilt head slightly toward gaze

            smoothedGazeX = alpha * rawGazeX + (1f - alpha) * smoothedGazeX
            smoothedGazeY = alpha * rawGazeY + (1f - alpha) * smoothedGazeY

            onGazeTargetUpdated(smoothedGazeX, smoothedGazeY, tilt)
        }

        imageProxy.close()
    }
}
