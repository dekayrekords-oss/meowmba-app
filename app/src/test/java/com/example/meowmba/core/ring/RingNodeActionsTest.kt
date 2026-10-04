package com.example.meowmba.core.ring

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingNodeActionsTest {
    private val window = Rect(0, 0, 400, 800)
    private fun node(bounds: Rect, scrollable: Boolean = false, vararg children: AccessibilityNodeInfo): AccessibilityNodeInfo {
        val node = mock(AccessibilityNodeInfo::class.java)
        `when`(node.isVisibleToUser).thenReturn(true); `when`(node.isEnabled).thenReturn(true)
        `when`(node.isScrollable).thenReturn(scrollable); `when`(node.childCount).thenReturn(children.size)
        children.forEachIndexed { i, child -> `when`(node.getChild(i)).thenReturn(child) }
        doAnswer { it.getArgument<Rect>(0).set(bounds); null }.`when`(node).getBoundsInScreen(any(Rect::class.java))
        `when`(node.performAction(anyInt())).thenReturn(true)
        return node
    }
    @Test fun scrollUsesThePaneUnderTheTargetRatherThanTheLargerParent() {
        val pane = node(Rect(20, 100, 180, 600), true)
        val root = node(window, true, pane)
        assertTrue(ringScrollNode(root, true, window, 60f to 200f)!!.handled)
        verify(pane).performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        verify(root, never()).performAction(anyInt())
    }
    @Test fun rejectedScrollKeepsThatPaneForTouchFallbackAndDoesNotTryAnAncestor() {
        val pane = node(Rect(20, 100, 180, 600), true)
        `when`(pane.performAction(anyInt())).thenReturn(false)
        val root = node(window, true, pane)
        val result = ringScrollNode(root, false, window, 60f to 200f)!!
        assertFalse(result.handled); assertEquals(Rect(20, 100, 180, 600), result.bounds)
        verify(root, never()).performAction(anyInt())
    }
    @Test fun withoutTargetSelectsTheFocusedPaneAndIgnoresInvisiblePanes() {
        val pane = node(Rect(20, 100, 180, 600), true)
        `when`(pane.isFocused).thenReturn(true)
        val hidden = node(window, true); `when`(hidden.isVisibleToUser).thenReturn(false)
        val root = node(window, false, pane, hidden)
        assertTrue(ringScrollNode(root, false, window, null)!!.handled)
        verify(pane).performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        verify(hidden, never()).performAction(anyInt())
    }
    @Test fun photoOnlyShutterNeverClicksRecordOrGenericCapture() {
        for (label in listOf("Record video", "Capture", "Shutter", "Submit")) {
            val button = node(window); `when`(button.isClickable).thenReturn(true)
            `when`(button.contentDescription).thenReturn(label)
            `when`(button.viewIdResourceName).thenReturn("com.android.camera2:id/shutter_button")
            assertFalse(ringTakePhoto(button)); verify(button, never()).performAction(anyInt())
        }
        val shutter = node(window); `when`(shutter.isClickable).thenReturn(true)
        `when`(shutter.contentDescription).thenReturn("Take picture")
        assertTrue(ringTakePhoto(shutter)); verify(shutter).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    @Test fun aRejectedPhotoShutterDoesNotClickADifferentPhotoControl() {
        val other = node(window); val shutter = node(window)
        for (button in listOf(other, shutter)) { `when`(button.isClickable).thenReturn(true); `when`(button.contentDescription).thenReturn("Take picture") }
        `when`(shutter.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(false)
        assertFalse(ringTakePhoto(node(window, false, other, shutter)))
        verify(other, never()).performAction(anyInt())
    }
}
