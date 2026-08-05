package com.example.meowmba.core.access

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

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

    companion object {
        @Volatile
        var instance: MeowAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }
}
