package com.example.meowmba.core.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Phase F3: opt-in UI automation. The service is OFF until the user enables
 * it in system accessibility settings; while off, [instance] is null and
 * every tool call fails honestly.
 *
 * Every mutating action (tap/type) is only reachable through the
 * UiAutomationTool, which is MODERATE-risk with ALWAYS confirmation — the
 * orchestrator's approval card gates each act.
 */
class MeowAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Breadth-first search for nodes whose text or content description matches. */
    private fun findNodes(query: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val want = query.trim().lowercase()
        val found = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 4000 && found.size < 8) {
            val node = queue.removeFirst()
            visited++
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (want in text || want in desc) found.add(node) else node.recycle()
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { if (!found.contains(it)) queue.add(it) }
            }
        }
        return found
    }

    fun tapByText(text: String): String {
        val nodes = findNodes(text)
        if (nodes.isEmpty()) return "I couldn't find \"$text\" on screen."
        var clicked = 0
        for (node in nodes) {
            var target = node
            // Click might live on a parent (e.g. a row wrapping a label).
            var hops = 0
            while (!target.isClickable && target.parent != null && hops < 3) {
                target = target.parent
                hops++
            }
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) clicked++
        }
        return if (clicked > 0) "Tapped \"$text\"." else "Found \"$text\" but it isn't tappable."
    }

    fun typeIntoText(field: String, text: String): String {
        val nodes = findNodes(field)
        val target = nodes.firstOrNull { it.isEditable } ?: nodes.firstOrNull()
            ?: return "I couldn't find a field called \"$field\"."
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            "Typed into \"$field\"."
        } else "Found \"$field\" but couldn't type into it."
    }

    fun scroll(forward: Boolean): String {
        val root = rootInActiveWindow ?: return "No active window to scroll."
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 4000) {
            val node = queue.removeFirst()
            visited++
            if (node.isScrollable) {
                val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                return if (node.performAction(action)) {
                    "Scrolled ${if (forward) "down" else "up"}."
                } else "Found a scrollable area but couldn't scroll it."
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return "Nothing scrollable on screen."
    }

    /** Collect visible text for a spoken screen summary. */
    fun readScreen(maxChars: Int = 1200): String {
        val root = rootInActiveWindow ?: return "I can't see any active window."
        val parts = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var total = 0
        while (queue.isNotEmpty() && visited < 4000 && total < maxChars) {
            val node = queue.removeFirst()
            visited++
            val t = node.text?.toString()?.take(160) ?: node.contentDescription?.toString()?.take(160)
            if (!t.isNullOrBlank()) {
                parts.add(t.trim())
                total += t.length
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return if (parts.isEmpty()) "The screen seems empty." else parts.joinToString("; ").take(maxChars)
    }

    // ── Phase H: expanded primitives for full phone control ────────────────

    /** Tap the Nth interactive element on screen (1-based). */
    fun tapByIndex(index: Int): String {
        val elements = collectInteractiveElements()
        if (elements.isEmpty()) return "No interactive elements on screen."
        if (index < 1 || index > elements.size) return "Index $index out of range (1–${elements.size})."
        val node = elements[index - 1]
        var target = node
        var hops = 0
        while (!target.isClickable && target.parent != null && hops < 3) {
            target = target.parent
            hops++
        }
        val label = node.text?.toString()?.take(40) ?: node.contentDescription?.toString()?.take(40) ?: "#$index"
        return if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            "Tapped #$index (\"$label\")."
        } else "Element #$index (\"$label\") isn't tappable."
    }

    /** Long-press by text match. */
    fun longPress(text: String): String {
        val nodes = findNodes(text)
        if (nodes.isEmpty()) return "I couldn't find \"$text\" on screen."
        val node = nodes.first()
        var target = node
        var hops = 0
        while (!target.isLongClickable && target.parent != null && hops < 3) {
            target = target.parent
            hops++
        }
        return if (target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            "Long-pressed \"$text\"."
        } else "Found \"$text\" but long-press didn't work."
    }

    /** Swipe gesture in a direction (up/down/left/right). */
    fun swipe(direction: String): String {
        val display = resources.displayMetrics
        val w = display.widthPixels.toFloat()
        val h = display.heightPixels.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val dist = h * 0.35f

        val path = Path()
        when (direction.lowercase()) {
            "up" -> { path.moveTo(cx, cy + dist / 2); path.lineTo(cx, cy - dist / 2) }
            "down" -> { path.moveTo(cx, cy - dist / 2); path.lineTo(cx, cy + dist / 2) }
            "left" -> { path.moveTo(cx + dist / 2, cy); path.lineTo(cx - dist / 2, cy) }
            "right" -> { path.moveTo(cx - dist / 2, cy); path.lineTo(cx + dist / 2, cy) }
            else -> return "Unknown direction \"$direction\". Use up/down/left/right."
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        var done = false
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { done = true }
            override fun onCancelled(gestureDescription: GestureDescription?) { done = true }
        }, null)
        // dispatchGesture is async; give it a beat
        Thread.sleep(350)
        return "Swiped $direction."
    }

    /** Perform a global action: back, home, recents, notifications. */
    fun globalAction(action: String): String {
        val id = when (action.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return "Unknown global action \"$action\"."
        }
        return if (performGlobalAction(id)) "Performed $action." else "Global action $action failed."
    }

    /** Package name of the currently active window (e.g. "com.spotify.music"). */
    fun getActivePackage(): String {
        return rootInActiveWindow?.packageName?.toString() ?: "unknown"
    }

    /**
     * Compact, numbered screen summary for the LLM brain.
     * Interactive elements get a numbered index; non-interactive text is
     * appended as context. Capped at [maxChars] for token budget.
     */
    fun readScreenCompact(maxChars: Int = 800): String {
        val root = rootInActiveWindow ?: return "No active window."
        val interactive = mutableListOf<String>()
        val context = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var total = 0

        while (queue.isNotEmpty() && visited < 4000 && total < maxChars) {
            val node = queue.removeFirst()
            visited++
            val text = node.text?.toString()?.take(80)
                ?: node.contentDescription?.toString()?.take(80)
            val isInteractive = node.isClickable || node.isEditable || node.isCheckable || node.isLongClickable

            if (!text.isNullOrBlank()) {
                if (isInteractive) {
                    val flags = buildList {
                        if (node.isClickable) add("tap")
                        if (node.isEditable) add("edit")
                        if (node.isCheckable) add(if (node.isChecked) "checked" else "unchecked")
                        if (node.isLongClickable) add("hold")
                    }.joinToString(",")
                    val entry = "${interactive.size + 1}. \"${text.trim()}\" [$flags]"
                    interactive.add(entry)
                    total += entry.length + 1
                } else {
                    val entry = text.trim()
                    context.add(entry)
                    total += entry.length + 1
                }
            } else if (isInteractive && node.viewIdResourceName != null) {
                // No text but has a resource id (icon buttons)
                val id = node.viewIdResourceName!!.substringAfterLast('/')
                val entry = "${interactive.size + 1}. [${id}] [tap]"
                interactive.add(entry)
                total += entry.length + 1
            }

            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }

        val sb = StringBuilder()
        if (interactive.isNotEmpty()) {
            sb.append("Interactive:\n")
            sb.append(interactive.joinToString("\n"))
        }
        if (context.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append("Text: ")
            // coerceAtLeast(0): once the interactive list alone exceeds
            // maxChars — routine on a button-dense screen — this budget goes
            // negative and String.take throws IllegalArgumentException, taking
            // down the accessibility service mid-read.
            sb.append(
                context.joinToString("; ")
                    .take((maxChars - sb.length).coerceAtLeast(0))
                    .trimEnd()
            )
        }
        return if (sb.isEmpty()) "Screen appears empty." else sb.toString().take(maxChars)
    }

    /** Collect all interactive (clickable/editable/checkable) nodes in tree order. */
    private fun collectInteractiveElements(): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 4000 && result.size < 50) {
            val node = queue.removeFirst()
            visited++
            if (node.isClickable || node.isEditable || node.isCheckable || node.isLongClickable) {
                result.add(node)
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return result
    }

    companion object {
        @Volatile
        var instance: MeowAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }
}
