package com.example.meowmba.core.screen

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** Separate translucent task: Meow's home cannot obscure the app being shared. */
class ScreenCapturePromptActivity : ComponentActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
            runCatching { ScreenCaptureOnceService.start(this, r.resultCode, r.data!!) }.onFailure {
                Toast.makeText(this, "Screen capture couldn't start. Share a screenshot with Meow instead.", Toast.LENGTH_LONG).show()
            }
        } else Toast.makeText(this, "Screen sharing cancelled. Nothing was captured.", Toast.LENGTH_SHORT).show()
        finish()
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestCapture() else {
            Toast.makeText(this, "Allow notifications to receive the captured screen, or share a screenshot with Meow.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (state != null) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else requestCapture()
    }
    private fun requestCapture() {
        runCatching { consent.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()) }
            .onFailure { Toast.makeText(this, "Screen sharing isn't available here. Try sharing a screenshot.", Toast.LENGTH_LONG).show(); finish() }
    }
}
