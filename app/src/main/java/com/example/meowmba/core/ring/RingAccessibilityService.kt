package com.example.meowmba.core.ring

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.app.KeyguardManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.meowmba.core.ring.SmartRingActivity

/** Filters R08 input only. No screen text is collected or transmitted. */
class RingAccessibilityService : AccessibilityService() {
    companion object { var instance: RingAccessibilityService? = null; private set }
    private lateinit var ring: AndroidSmartRing
    private val main = Handler(Looper.getMainLooper())
    private var tapCount = 0
    private var tapPackage: String? = null
    private var tapDevice = -1
    private var gestureBusy = false
    private var lastKeyDown: Triple<Int, Int, Long>? = null
    private var lastTapTime = 0L
    private val consumed = mutableSetOf<Pair<Int, Int>>()
    private val finishTap = Runnable {
        val count = tapCount; val target = tapPackage
        tapCount = 0; tapPackage = null; tapDevice = -1
        val gesture = when (count) { 1 -> "Single tap"; 2 -> "Double tap"; else -> "Triple tap" }
        if (target == foregroundPackage()) perform(gesture)
        else ring.log("Ignored tap: active app changed")
    }
    override fun onServiceConnected() {
        instance = this; ring = AndroidSmartRing.get(this)
        SmartRing.driver = ring; ring.accessibilityConnected(true); ring.restoreConnection()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() = cancelPending()
    override fun onDestroy() {
        cancelPending(); consumed.clear(); instance = null
        if (::ring.isInitialized) { ring.accessibilityConnected(false); ring.disconnect() }
        super.onDestroy()
    }
    fun cancelPending() { main.removeCallbacks(finishTap); tapCount = 0; tapPackage = null; tapDevice = -1 }
    private fun foregroundPackage(): String? {
        val root = rootInActiveWindow ?: return null
        return try { root.packageName?.toString() } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }
    private fun unlocked() = getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val identity = event.deviceId to event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return consumed.remove(identity)
        val device = event.device ?: return false
        if (!device.name.contains("R08", ignoreCase = true)) return false
        val gesture = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> "Next"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Previous"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> "Tap"
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> "Double tap"
            else -> null
        }
        val state = ring.state.value
        val target = foregroundPackage()
        // The tester binds a single Android input descriptor to the selected ring.
        if (!state.connected || !unlocked()) { cancelPending(); return false }
        if (!state.testing && (state.paused || target !in ringAllowedPackages(state.allowlist))) { cancelPending(); return false }
        if (state.testing) {
            if (!ring.verifyInput(device.descriptor, device.name)) return false
        } else if (ring.verifiedDescriptor.isEmpty() || ring.verifiedDescriptor != device.descriptor) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (gesture == null && !state.testing) return false
        consumed.add(identity)
        if (event.repeatCount > 0) return true
        val down = Triple(event.deviceId, event.keyCode, event.downTime)
        if (lastKeyDown == down) return true
        lastKeyDown = down
        if (gesture == null) {
            ring.log("Unmapped input · ${KeyEvent.keyCodeToString(event.keyCode)} · ${device.name}")
            return true
        }
        if (gesture == "Tap") {
            if (event.eventTime - lastTapTime in 0..75 && lastTapTime != 0L) return true
            lastTapTime = event.eventTime
        }
        ring.log("$gesture · ${KeyEvent.keyCodeToString(event.keyCode)} · ${device.name} · app=${target ?: "unknown"}")
        if (gesture == "Tap") {
            if (tapPackage != target || tapDevice != event.deviceId) cancelPending()
            tapPackage = target; tapDevice = event.deviceId; tapCount++
            main.removeCallbacks(finishTap)
            if (tapCount >= 3) finishTap.run() else main.postDelayed(finishTap, 400)
        } else { cancelPending(); perform(gesture) }
        return true
    }
    private fun perform(gesture: String) {
        val state = ring.state.value
        if (state.testing) { ring.verifyGesture(gesture); return }
        if (state.paused || !state.connected || !unlocked()) return
        val target = foregroundPackage() ?: return
        if (target !in ringAllowedPackages(state.allowlist)) return
        val action = ringActionFor(state, gesture, target)
        when (action) {
            RingAction.NONE -> ring.log("$gesture: no action")
            RingAction.BACK -> ring.log(if (performGlobalAction(GLOBAL_ACTION_BACK)) "$gesture: Back" else "Back unavailable")
            RingAction.CONTROLS -> {
                startActivity(Intent(this, SmartRingActivity::class.java).putExtra("open_smart_ring", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                ring.log("Opened ring controls")
            }
            RingAction.SCROLL_DOWN, RingAction.SCROLL_UP -> scroll(action, state.distance)
        }
    }
    private fun scroll(action: RingAction, distance: Float) {
        if (gestureBusy) { ring.log("Scroll still in progress"); return }
        val metrics = resources.displayMetrics
        val half = metrics.heightPixels * distance / 2
        val center = metrics.heightPixels * .5f
        val down = action == RingAction.SCROLL_DOWN
        val path = Path().apply {
            moveTo(metrics.widthPixels * .5f, center + if (down) half else -half)
            lineTo(metrics.widthPixels * .5f, center + if (down) -half else half)
        }
        gestureBusy = true
        val accepted = dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 250)).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gesture: GestureDescription?) { gestureBusy = false; ring.log("${action.label}: gesture completed") }
                override fun onCancelled(gesture: GestureDescription?) { gestureBusy = false; ring.log("Scroll cancelled") }
            }, main)
        if (!accepted) { gestureBusy = false; ring.log("Scroll could not be dispatched") }
    }
}
