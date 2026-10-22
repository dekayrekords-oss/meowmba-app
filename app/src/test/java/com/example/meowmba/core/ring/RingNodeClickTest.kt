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
class RingNodeClickTest {
    private fun node(bounds: Rect, clickable: Boolean = true, vararg children: AccessibilityNodeInfo): AccessibilityNodeInfo {
        val node = mock(AccessibilityNodeInfo::class.java)
        `when`(node.isVisibleToUser).thenReturn(true)
        `when`(node.isEnabled).thenReturn(true)
        `when`(node.isClickable).thenReturn(clickable)
        `when`(node.childCount).thenReturn(children.size)
        children.forEachIndexed { i, child -> `when`(node.getChild(i)).thenReturn(child) }
        doAnswer { it.getArgument<Rect>(0).set(bounds); null }.`when`(node).getBoundsInScreen(any(Rect::class.java))
        `when`(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(true)
        return node
    }
    @Test fun selectsNestedButtonRatherThanItsClickableContainer() {
        val button = node(Rect(40, 40, 80, 80))
        val root = node(Rect(0, 0, 200, 200), true, button)
        assertTrue(ringClickNodeAt(root, 60f, 60f))
        verify(button).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        verify(root, never()).performAction(anyInt())
        @Suppress("DEPRECATION") verify(button).recycle()
    }
    @Test fun labelInsideButtonUsesItsClickableParent() {
        val label = node(Rect(40, 40, 80, 80), false)
        val button = node(Rect(20, 20, 100, 100), true, label)
        assertTrue(ringClickNodeAt(button, 60f, 60f))
        verify(button).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        verify(label, never()).performAction(anyInt())
    }
    @Test fun rejectedChildClickFallsBackWithoutActivatingAnAncestor() {
        val child = node(Rect(40, 40, 80, 80))
        `when`(child.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(false)
        val root = node(Rect(0, 0, 200, 200), true, child)
        assertFalse(ringClickNodeAt(root, 60f, 60f))
        verify(root, never()).performAction(anyInt())
    }
    @Test fun ignoresControlsOutsidePointOrHiddenOrDisabled() {
        val outside = node(Rect(100, 100, 150, 150))
        val hidden = node(Rect(20, 20, 100, 100))
        `when`(hidden.isVisibleToUser).thenReturn(false)
        val disabled = node(Rect(20, 20, 100, 100))
        `when`(disabled.isEnabled).thenReturn(false)
        val root = node(Rect(0, 0, 200, 200), false, outside, hidden, disabled)
        assertFalse(ringClickNodeAt(root, 60f, 60f))
        for (n in listOf(root, outside, hidden, disabled)) verify(n, never()).performAction(anyInt())
    }
}
