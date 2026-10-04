package com.example.meowmba.core.ring

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Rect
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingScrollDeliveryTest {
    @Test fun consecutivePhysicalSwipesSurviveTheOverlayDelayAndDrainInOrder() {
        exercise(cancel = false)
    }
    @Test fun pausingDuringTheFirstSwipeDiscardsQueuedPhysicalSwipes() {
        exercise(cancel = true)
    }
    private fun exercise(cancel: Boolean) {
        val service = spy(Robolectric.buildService(RingAccessibilityService::class.java).create().get())
        val ring = mock(AndroidSmartRing::class.java)
        val state = MutableStateFlow(RingState(controlsConsent = true, connected = true, paused = false,
            allApps = true, sleepEnabled = false, verifiedGestures = setOf("Next")))
        `when`(ring.state).thenReturn(state); `when`(ring.verifiedDescriptor).thenReturn("test-ring")
        val root = mock(AccessibilityNodeInfo::class.java); `when`(root.packageName).thenReturn("test.app")
        val window = mock(AccessibilityWindowInfo::class.java)
        `when`(window.type).thenReturn(AccessibilityWindowInfo.TYPE_APPLICATION); `when`(window.isFocused).thenReturn(true)
        `when`(window.root).thenReturn(root)
        doAnswer { it.getArgument<Rect>(0).set(0, 0, 400, 800); null }.`when`(window).getBoundsInScreen(any(Rect::class.java))
        doReturn(listOf(window)).`when`(service).windows
        val target = RingClickTarget(service)
        for ((name, value) in listOf("ring" to ring, "clickTarget" to target)) {
            RingAccessibilityService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
        }
        val callbacks = mutableListOf<AccessibilityService.GestureResultCallback>()
        doAnswer { callbacks += it.getArgument<AccessibilityService.GestureResultCallback>(1); true }.`when`(service)
            .dispatchGesture(any(GestureDescription::class.java), any(), any())
        val device = mock(InputDevice::class.java)
        `when`(device.name).thenReturn("R08 test"); `when`(device.descriptor).thenReturn("test-ring")
        val onKey = RingAccessibilityService::class.java.getDeclaredMethod("onKeyEvent", KeyEvent::class.java).apply { isAccessible = true }
        fun swipe(time: Long) {
            val event = mock(KeyEvent::class.java)
            `when`(event.device).thenReturn(device); `when`(event.deviceId).thenReturn(1)
            `when`(event.keyCode).thenReturn(KeyEvent.KEYCODE_MEDIA_NEXT); `when`(event.action).thenReturn(KeyEvent.ACTION_DOWN)
            `when`(event.downTime).thenReturn(time); `when`(event.eventTime).thenReturn(time)
            assertTrue(onKey.invoke(service, event) as Boolean)
            `when`(event.action).thenReturn(KeyEvent.ACTION_UP); assertTrue(onKey.invoke(service, event) as Boolean)
        }
        swipe(100); swipe(130)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(99)); assertEquals(0, callbacks.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1)); assertEquals(1, callbacks.size)
        if (cancel) { state.value = state.value.copy(paused = true); service.cancelPending() }
        callbacks[0].onCompleted(null)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(if (cancel) 1 else 2, callbacks.size)
        service.cancelPending(); target.hide()
    }
}
