package com.example.meowmba.core.ring

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.media.AudioManager
import android.provider.MediaStore
import android.os.PowerManager
import android.app.KeyguardManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.example.meowmba.BuildConfig
import java.io.FileDescriptor
import java.io.PrintWriter
import com.example.meowmba.core.ring.SmartRingActivity

/** Filters R08 input only. No screen text is collected or transmitted. */
class RingAccessibilityService : AccessibilityService() {
    companion object { var instance: RingAccessibilityService? = null; private set }
    private lateinit var ring: AndroidSmartRing
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var clickTarget: RingClickTarget
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) { cancelPending(); clickTarget.hide() }
            else refreshTarget()
        }
    }
    private var screenReceiverRegistered = false
    private var tapCount = 0
    private var tapPackage: String? = null
    private var tapDevice = -1
    private var gestureBusy = false
    private data class ScrollRequest(val gesture: String, val action: RingAction, val target: String, val expiresAt: Long)
    private val scrollQueue = ArrayDeque<ScrollRequest>()
    private var pendingTouch: Runnable? = null
    private var lastKeyDown: Triple<Int, Int, Long>? = null
    private var lastTapTime = 0L
    private val consumed = mutableSetOf<Pair<Int, Int>>()
    private val finishTap = Runnable {
        val count = tapCount; val target = tapPackage
        tapCount = 0; tapPackage = null; tapDevice = -1
        val gesture = when (count) { 1 -> "Single tap"; 2 -> "Double tap"; 3 -> "Triple tap"; else -> "Quadruple tap" }
        if (target == foregroundPackage()) perform(gesture)
        else ring.log("Ignored tap: active app changed")
    }
    override fun onServiceConnected() {
        if (!BuildConfig.RING_CONTROLS_ENABLED) { disableSelf(); return }
        instance = this; ring = AndroidSmartRing.get(this)
        clickTarget = RingClickTarget(this, ring::controlsActivity)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }, Context.RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") registerReceiver(screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }) }
        screenReceiverRegistered = true
        scope.launch { ring.state.collect { refreshTarget() } }
        SmartRing.driver = ring; ring.accessibilityConnected(true); ring.restoreConnection()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { refreshTarget() }
    private fun refreshTarget() {
        if (!::ring.isInitialized || !::clickTarget.isInitialized) return
        ring.checkSleepDeadline()
        val state = ring.state.value
        if (state.controlsConsent && state.connected && !state.paused && !state.testing && !state.sleeping && !gestureBusy && state.showTarget &&
            state.mappings.values.any { it in ringPointerActions } && unlocked() && ringAppAllowed(state, foregroundPackage())) clickTarget.show()
        else clickTarget.hide()
    }
    fun centerTarget() { if (::clickTarget.isInitialized) { ring.controlsActivity(); clickTarget.center(); refreshTarget() } }
    override fun onInterrupt() = cancelPending()
    override fun onDestroy() {
        cancelPending(); consumed.clear(); instance = null
        scope.cancel()
        if (::clickTarget.isInitialized) clickTarget.hide()
        if (screenReceiverRegistered) unregisterReceiver(screenReceiver)
        if (::ring.isInitialized) { ring.accessibilityConnected(false); ring.disconnect() }
        super.onDestroy()
    }
    private fun cancelTapSequence() {
        main.removeCallbacks(finishTap); tapCount = 0; tapPackage = null; tapDevice = -1
    }
    fun cancelPending() {
        cancelTapSequence(); cancelTouchAndScroll(); SmartRing.commands.clear()
    }
    private fun cancelTouchAndScroll() {
        scrollQueue.clear()
        pendingTouch?.let { main.removeCallbacks(it); gestureBusy = false }
        pendingTouch = null
    }
    private fun foregroundBounds(): Rect? {
        val appWindow = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused } ?: return null
        return Rect().also { appWindow.getBoundsInScreen(it) }.takeUnless { it.isEmpty }
    }
    private fun packageAt(x: Float, y: Float): String? {
        val appWindow = windows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && Rect().also(it::getBoundsInScreen).contains(x.toInt(), y.toInt())
        } ?: return null
        val root = appWindow.root ?: return null
        return try { root.packageName?.toString() } finally { @Suppress("DEPRECATION") root.recycle() }
    }
    private fun clickNodeAt(x: Float, y: Float): Boolean {
        val appWindow = windows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && Rect().also(it::getBoundsInScreen).contains(x.toInt(), y.toInt())
        } ?: return false
        val root = appWindow.root ?: return false
        return try {
            ringAppAllowed(ring.state.value, root.packageName?.toString()) && ringClickNodeAt(root, x, y)
        } finally { @Suppress("DEPRECATION") root.recycle() }
    }
    private fun foregroundPackage(): String? {
        // A touched crosshair must never masquerade as Meowmba's foreground app.
        val appWindow = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
            ?: return null
        val root = appWindow.root ?: return null
        return try { root.packageName?.toString() } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }
    private fun unlocked() = getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val identity = event.deviceId to event.keyCode
        if (event.action == KeyEvent.ACTION_UP) {
            val handled = consumed.remove(identity)
            if (handled) ring.recordPress(event.keyCode, false, KeyEvent.keyCodeToString(event.keyCode))
            return handled
        }
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
        // The tester binds a single Android input descriptor to the selected ring.
        if (!state.controlsConsent || !state.connected || !unlocked()) { cancelPending(); return false }
        val target = foregroundPackage()
        if (!state.testing && (state.paused || !ringAppAllowed(state, target))) { cancelPending(); return false }
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
        ring.recordPress(event.keyCode, true, KeyEvent.keyCodeToString(event.keyCode))
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
            if (tapPackage != target || tapDevice != event.deviceId) cancelTapSequence()
            tapPackage = target; tapDevice = event.deviceId; tapCount++
            main.removeCallbacks(finishTap)
            if (tapCount >= 4) finishTap.run() else main.postDelayed(finishTap, 450)
        } else {
            val combo = if (gesture in listOf("Next", "Previous") && tapCount in 1..2 && tapPackage == target && tapDevice == event.deviceId)
                "$tapCount ${if (tapCount == 1) "tap" else "taps"} + $gesture" else gesture
            cancelTapSequence(); perform(combo)
        }
        return true
    }
    private fun perform(gesture: String) {
        val state = ring.state.value
        if (!state.controlsConsent) return
        ring.recordGesture(gesture)
        if (state.testing) { ring.verifyGesture(gesture); return }
        if (ring.consumeSleepingGesture(gesture)) return
        if (state.paused || !state.connected || !unlocked()) return
        val target = foregroundPackage() ?: return
        if (!ringAppAllowed(state, target)) return
        val action = ringActionFor(state, gesture, target)
        if (action != RingAction.NONE && action !in setOf(RingAction.SCROLL_DOWN, RingAction.SCROLL_UP)) cancelTouchAndScroll()
        when (action) {
            RingAction.NONE -> {
                val mapped = state.mappings[gesture] ?: RingAction.NONE
                ring.log(when {
                    mapped == RingAction.NONE -> "$gesture: no action mapped"
                    !ringGestureReady(state, gesture, mapped) -> "$gesture: blocked; confirm this gesture in Gesture tester"
                    mapped in ringPointerActions && !state.showTarget -> "$gesture: blocked; Show click target is off"
                    else -> "$gesture: no action"
                })
            }
            RingAction.BACK -> ring.log(if (performGlobalAction(GLOBAL_ACTION_BACK)) "$gesture: Back" else "Back unavailable")
            RingAction.HOME -> ring.log(if (performGlobalAction(GLOBAL_ACTION_HOME)) "Home" else "Home unavailable")
            RingAction.RECENTS -> ring.log(if (performGlobalAction(GLOBAL_ACTION_RECENTS)) "Recent apps" else "Recent apps unavailable")
            RingAction.CONTROLS -> {
                startActivity(Intent(this, SmartRingActivity::class.java).putExtra("open_smart_ring", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                ring.log("Opened ring controls")
            }
            RingAction.SCROLL_DOWN, RingAction.SCROLL_UP -> {
                val request = ScrollRequest(gesture, action, target, SystemClock.elapsedRealtime() + 2_000)
                if (gestureBusy) {
                    if (scrollQueue.size < 4) scrollQueue.addLast(request) else ring.log("Scroll queue full; finish the current swipe")
                } else scroll(request)
            }
            RingAction.MEDIA_PLAY_PAUSE, RingAction.MEDIA_NEXT, RingAction.MEDIA_PREVIOUS, RingAction.MEDIA_STOP -> {
                val code = when (action) {
                    RingAction.MEDIA_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                    RingAction.MEDIA_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    RingAction.MEDIA_STOP -> KeyEvent.KEYCODE_MEDIA_STOP
                    else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                }
                runCatching {
                    val audio = getSystemService(AudioManager::class.java)
                    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                }.onSuccess { ring.log("${action.label}: sent to active media session") }
                    .onFailure { ring.log("Media action unavailable") }
            }
            RingAction.VOLUME_UP, RingAction.VOLUME_DOWN -> {
                runCatching { getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    if (action == RingAction.VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI) }.onSuccess { ring.log(action.label) }.onFailure { ring.log("Volume action unavailable") }
            }
            RingAction.OPEN_CAMERA -> launchShortcut(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Camera")
            RingAction.CAMERA_SHUTTER -> {
                val root = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }?.root
                val clicked = root?.let { try { ringTakePhoto(it) } finally { @Suppress("DEPRECATION") it.recycle() } } == true
                ring.log(if (clicked) "Photo shutter selected" else "Photo shutter unavailable: open a compatible camera in photo mode")
            }
            RingAction.OPEN_APP -> {
                val packageName = state.customApps[gesture].orEmpty()
                val launch = packageName.takeIf { it.isNotBlank() }?.let { packageManager.getLaunchIntentForPackage(it) }
                if (launch == null) ring.log("Choose an installed app for this gesture") else launchShortcut(launch, "App shortcut")
            }
            RingAction.MEOW_COMMAND -> {
                if (SmartRing.commands.offer(gesture, state, SystemClock.elapsedRealtime())) {
                    if (!launchShortcut(Intent(this, com.example.meowmba.MainActivity::class.java), "Meow request")) SmartRing.commands.clear()
                } else ring.log("Meow request unavailable: configure a request or finish the pending request")
            }
            RingAction.CLICK -> {
                refreshTarget()
                if (!clickTarget.visible || clickTarget.dragging) { ring.log("Click needs the visible target; finish dragging first"); return }
                val (x, y) = clickTarget.coordinates()
                if (!ringAppAllowed(state, packageAt(x, y))) { ring.log("Move target inside an allowed app before clicking"); return }
                if (gestureBusy) { ring.log("Touch gesture still in progress"); return }
                if (clickNodeAt(x, y)) ring.log("Click: control selected")
                else inject(Path().apply { moveTo(x, y) }, 80, "Click")
            }
            RingAction.POINTER_UP, RingAction.POINTER_DOWN, RingAction.POINTER_LEFT, RingAction.POINTER_RIGHT -> {
                refreshTarget()
                if (clickTarget.visible && !clickTarget.dragging) { clickTarget.move(action); ring.log(action.label) }
                else ring.log("Target movement unavailable")
            }
        }
    }
    private fun launchShortcut(intent: Intent, label: String): Boolean = runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        ring.log("$label opened"); true
    }.getOrElse { ring.log("$label unavailable"); false }
    private fun drainScroll() {
        if (gestureBusy) return
        val request = scrollQueue.removeFirstOrNull() ?: return
        scroll(request)
    }
    private fun scroll(request: ScrollRequest) {
        ring.checkSleepDeadline()
        val state = ring.state.value
        if (SystemClock.elapsedRealtime() >= request.expiresAt || !unlocked() || foregroundPackage() != request.target ||
            ringActionFor(state, request.gesture, request.target) != request.action) {
            scrollQueue.clear(); ring.log("Queued scroll cancelled: controls or active app changed"); return
        }
        if (clickTarget.dragging) { scrollQueue.clear(); ring.log("Finish dragging the target before scrolling"); return }
        val window = foregroundBounds() ?: run { scrollQueue.clear(); return }
        val down = request.action == RingAction.SCROLL_DOWN
        val root = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }?.root
        val result = root?.let { try { ringScrollNode(it, down, window,
            if (state.showTarget && state.mappings.values.any { action -> action in ringPointerActions }) clickTarget.coordinates() else null)
        } finally { @Suppress("DEPRECATION") it.recycle() } }
        if (result?.handled == true) { ring.log("${request.action.label}: active scroll area moved"); main.post { drainScroll() }; return }
        val bounds = result?.bounds ?: window
        val half = bounds.height() * state.distance / 2
        val center = bounds.exactCenterY()
        val path = Path().apply {
            moveTo(bounds.exactCenterX(), center + if (down) half else -half)
            lineTo(bounds.exactCenterX(), center + if (down) -half else half)
        }
        inject(path, 250, request.action.label)
    }
    private fun inject(path: Path, duration: Long, label: String) {
        if (gestureBusy || clickTarget.dragging) { ring.log("Touch gesture still in progress"); return }
        val target = foregroundPackage()
        gestureBusy = true; clickTarget.hide()
        // Window removal and input-window updates are asynchronous on the phone. Allow the
        // crosshair's touchable window to disappear before injecting at its former position.
        val touch = Runnable {
            pendingTouch = null
            val state = ring.state.value
            ring.checkSleepDeadline()
            if (!state.controlsConsent || !state.connected || state.paused || state.testing || ring.state.value.sleeping || !unlocked() ||
                foregroundPackage() != target || !ringAppAllowed(state, target)) {
                gestureBusy = false; scrollQueue.clear(); ring.log("$label cancelled: controls or active app changed"); refreshTarget()
                return@Runnable
            }
            val accepted = dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gesture: GestureDescription?) { gestureBusy = false; ring.log("$label: gesture completed"); refreshTarget(); drainScroll() }
                override fun onCancelled(gesture: GestureDescription?) { gestureBusy = false; scrollQueue.clear(); ring.log("$label cancelled"); refreshTarget() }
            }, main)
            if (!accepted) { gestureBusy = false; scrollQueue.clear(); ring.log("$label could not be dispatched"); refreshTarget() }
        }
        pendingTouch = touch; main.postDelayed(touch, 100)
    }
    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
        super.dump(fd, writer, args)
        if (!BuildConfig.DEBUG || !::ring.isInitialized) return
        val state = ring.state.value
        writer.println("Ring controls: connected=${state.connected} paused=${state.paused} testing=${state.testing} sleeping=${state.sleeping}")
        writer.println("Target: visible=${::clickTarget.isInitialized && clickTarget.visible} busy=$gestureBusy; tapCount=$tapCount")
        state.events.take(30).forEach(writer::println)
    }
}
