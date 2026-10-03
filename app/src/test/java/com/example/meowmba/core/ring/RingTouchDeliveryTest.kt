package com.example.meowmba.core.ring

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Looper
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
class RingTouchDeliveryTest {
    @Test fun touchWaitsForOverlayRemovalAndRechecksControlsBeforeDelivery() {
        val service = spy(Robolectric.buildService(RingAccessibilityService::class.java).create().get())
        val ring = mock(AndroidSmartRing::class.java)
        val state = MutableStateFlow(RingState(controlsConsent = true, connected = true, paused = false, allApps = true, sleepEnabled = false))
        `when`(ring.state).thenReturn(state)
        val root = mock(AccessibilityNodeInfo::class.java)
        `when`(root.packageName).thenReturn("test.app")
        val window = mock(AccessibilityWindowInfo::class.java)
        `when`(window.type).thenReturn(AccessibilityWindowInfo.TYPE_APPLICATION)
        `when`(window.isFocused).thenReturn(true)
        `when`(window.root).thenReturn(root)
        doReturn(listOf(window)).`when`(service).windows
        val target = RingClickTarget(service)
        for ((name, value) in listOf("ring" to ring, "clickTarget" to target)) {
            RingAccessibilityService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
        }
        var dispatches = 0
        doAnswer { dispatches++; true }.`when`(service).dispatchGesture(any(GestureDescription::class.java), any(), any())
        val inject = RingAccessibilityService::class.java.getDeclaredMethod("inject", Path::class.java, Long::class.javaPrimitiveType, String::class.java).apply { isAccessible = true }
        fun enqueue() {
            assertTrue(target.show())
            inject.invoke(service, Path().apply { moveTo(60f, 60f) }, 80L, "Click")
            assertFalse(target.visible)
        }
        enqueue()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(99))
        assertEquals(0, dispatches)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(1, dispatches)
        // Complete the synthetic first gesture before testing the next queue.
        RingAccessibilityService::class.java.getDeclaredField("gestureBusy").apply { isAccessible = true }.set(service, false)
        enqueue(); service.cancelPending()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(1, dispatches)
        enqueue(); state.value = state.value.copy(paused = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(1, dispatches)
        state.value = state.value.copy(paused = false)
        enqueue(); state.value = state.value.copy(controlsConsent = false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(1, dispatches)
        state.value = state.value.copy(controlsConsent = true)
        enqueue(); `when`(root.packageName).thenReturn("different.app")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(1, dispatches)
        target.hide()
    }
}
