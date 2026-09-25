package com.example.meowmba.core.vision

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/** Visible one-shot scanner. A decoded value is data; links are never followed automatically. */
class QrScannerActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private var handled = false
    private var inFlight = false
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var cameraPreview: Preview? = null
    private val scanner = BarcodeScanning.getClient()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else fail("Camera permission was denied. No code was scanned.")
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        preview = PreviewView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply {
                text = "Scan a QR code or barcode\nPoint the rear camera at the code. Nothing opens automatically."
                setTextColor(Color.WHITE); textSize = 18f; setPadding(24, 20, 24, 20)
            })
            addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(Button(context).apply { text = "Cancel"; setOnClickListener { finish() } })
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(root)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else if (state == null) permission.launch(Manifest.permission.CAMERA)
    }

    @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            try {
                val camera = future.get()
                provider = camera
                val analyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis = analyzer
                analyzer.setAnalyzer(ContextCompat.getMainExecutor(this)) { proxy ->
                    if (handled || inFlight || isFinishing) { proxy.close(); return@setAnalyzer }
                    val image = proxy.image
                    if (image == null) { proxy.close(); return@setAnalyzer }
                    inFlight = true
                    try {
                        scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { codes ->
                                val code = codes.firstOrNull { !it.rawValue.isNullOrBlank() }
                                if (!handled && !isFinishing && code != null) {
                                    handled = true
                                    setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_VALUE, code.rawValue?.take(8192))
                                        .putExtra(EXTRA_FORMAT, code.format))
                                    finish()
                                }
                            }.addOnFailureListener { if (!isFinishing) fail("The scanner couldn't read the camera. Please try again.") }
                            .addOnCompleteListener { inFlight = false; proxy.close() }
                    } catch (e: Exception) { inFlight = false; proxy.close(); fail("The scanner couldn't start. Please try again.") }
                }
                val cameraView = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                cameraPreview = cameraView
                camera.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, cameraView, analyzer)
            } catch (e: Exception) { fail("The rear camera is unavailable or being used by another app.") }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun fail(message: String) {
        if (handled || isFinishing) return
        handled = true
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message))
        finish()
    }
    override fun onDestroy() {
        handled = true
        analysis?.clearAnalyzer()
        val owned = listOfNotNull(analysis, cameraPreview).toTypedArray()
        if (owned.isNotEmpty()) provider?.unbind(*owned)
        scanner.close()
        super.onDestroy()
    }
    companion object {
        const val EXTRA_VALUE = "qr_value"
        const val EXTRA_FORMAT = "qr_format"
        const val EXTRA_ERROR = "qr_error"
    }
}
