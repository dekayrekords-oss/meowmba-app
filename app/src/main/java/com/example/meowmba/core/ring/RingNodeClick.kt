package com.example.meowmba.core.ring

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Activate the deepest control under the crosshair, without the overlay intercepting a touch.
 * A rejected action falls back to a physical tap, never to a different ancestor control.
 * Only bounds/actions are inspected; no screen text is retained.
 */
internal fun ringClickNodeAt(root: AccessibilityNodeInfo, x: Float, y: Float): Boolean {
    var remaining = 512
    fun click(node: AccessibilityNodeInfo, depth: Int): Boolean? {
        if (--remaining < 0 || depth > 48) return null
        val bounds = Rect().also(node::getBoundsInScreen)
        if (!node.isVisibleToUser || !bounds.contains(x.toInt(), y.toInt())) return null
        // Later siblings generally draw above earlier siblings.
        for (index in node.childCount - 1 downTo 0) {
            val child = node.getChild(index) ?: continue
            val result = try { click(child, depth + 1) } finally {
                @Suppress("DEPRECATION") child.recycle()
            }
            if (result != null) return result
        }
        if (!node.isEnabled || !node.isClickable) return null
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    return click(root, 0) == true
}
