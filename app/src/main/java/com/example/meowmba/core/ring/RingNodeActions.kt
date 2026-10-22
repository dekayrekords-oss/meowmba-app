package com.example.meowmba.core.ring

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

internal data class RingScrollResult(val handled: Boolean, val bounds: Rect)

/** Prefer a scroll pane under the target, then a focused pane, then the largest pane.
 * Keep only a node path and bounds; never retain screen contents. */
internal fun ringScrollNode(root: AccessibilityNodeInfo, down: Boolean, window: Rect,
    point: Pair<Float, Float>?): RingScrollResult? {
    data class Candidate(val path: List<Int>, val bounds: Rect, val priority: Int, val size: Long)
    var best: Candidate? = null
    var remaining = 512
    fun visit(node: AccessibilityNodeInfo, path: List<Int>) {
        if (--remaining < 0 || path.size > 48 || !node.isVisibleToUser) return
        val bounds = Rect().also(node::getBoundsInScreen)
        if (!bounds.intersect(window) || bounds.isEmpty) return
        if (node.isEnabled && node.isScrollable) {
            val underTarget = point?.let { bounds.contains(it.first.toInt(), it.second.toInt()) } == true
            val priority = if (underTarget) 3 else if (node.isFocused || node.isAccessibilityFocused) 2 else 1
            val candidate = Candidate(path, bounds, priority, bounds.width().toLong() * bounds.height())
            val current = best
            if (current == null || priority > current.priority || (priority == current.priority &&
                (if (priority == 3) path.size > current.path.size else candidate.size > current.size))) best = candidate
        }
        for (index in node.childCount - 1 downTo 0) {
            val child = node.getChild(index) ?: continue
            try { visit(child, path + index) } finally { @Suppress("DEPRECATION") child.recycle() }
        }
    }
    visit(root, emptyList())
    val candidate = best ?: return null
    fun act(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (!node.isVisibleToUser || !node.isEnabled) return false
        if (depth == candidate.path.size) return node.isScrollable && node.performAction(
            if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        val child = node.getChild(candidate.path[depth]) ?: return false
        return try { act(child, depth + 1) } finally { @Suppress("DEPRECATION") child.recycle() }
    }
    return RingScrollResult(act(root, 0), candidate.bounds)
}

/** Match photo-only shutter controls, never a generic Capture/Record/Submit button.
 * Control identifiers are checked locally and are not logged or transmitted. */
internal fun ringTakePhoto(root: AccessibilityNodeInfo): Boolean {
    var remaining = 512
    val photoLabels = setOf("take picture", "take photo", "capture photo", "capture image", "photo shutter")
    fun click(node: AccessibilityNodeInfo, depth: Int): Boolean? {
        if (--remaining < 0 || depth > 48 || !node.isVisibleToUser || !node.isEnabled) return null
        val label = node.contentDescription?.toString()?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
        // Generic shutter IDs/labels are also used in video mode, so require a photo label.
        val photoLabel = label in photoLabels
        if (node.isClickable && photoLabel) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        for (index in node.childCount - 1 downTo 0) {
            val child = node.getChild(index) ?: continue
            val handled = try { click(child, depth + 1) } finally { @Suppress("DEPRECATION") child.recycle() }
            if (handled != null) return handled
        }
        return null
    }
    return click(root, 0) == true
}
