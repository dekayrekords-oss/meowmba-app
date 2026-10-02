package com.example.meowmba.core.ring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.meowmba.ui.companion.SmartRingScene
import com.example.meowmba.ui.theme.MeowmbaTheme

/** Dedicated setup page keeps permission requests scoped to the ring feature. */
class SmartRingActivity : ComponentActivity() {
    private val ring by lazy { AndroidSmartRing.get(this) }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ring.permissionResult() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SmartRing.driver = ring
        setContent { MeowmbaTheme { SmartRingScene(onBack = ::finish) } }
    }
    override fun onResume() {
        super.onResume()
        ring.permissionRequest = { permission.launch(ring.permissions()) }
    }
    override fun onPause() {
        ring.setTesting(false); ring.stopSensorTest()
        ring.permissionRequest = null
        super.onPause()
    }
}
