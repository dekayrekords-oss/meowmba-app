package com.example.meowmba.core.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.coroutines.resume

/**
 * Phase F3: opt-in UI automation. The service is OFF until the user enables
 * it in system accessibility settings; while off, [instance] is null and
 * every tool call fails honestly.
 *
 * Explicit UI commands are routed through the tool orchestrator. Autonomous
 * navigation has a task approval and hands consequential controls back to
 * the user. A service connection alone is never proof of task completion.
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
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { if (!found.contains(it)) queue.add(it) }
            }
            // Read children before recycling this node (important on API 31/32).
            if (want in text || want in desc) found.add(node) else node.recycle()
        }
        return found
    }

    fun tapByText(text: String, allowConsequential: Boolean = true): String {
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
            if (!allowConsequential && requiresHandoff(node, target)) return "Handoff required: review and tap this final control yourself."
            // One action means one tap, not every matching row on the screen.
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK) || tapVisibleNode(node)) { clicked++; break }
        }
        return if (clicked > 0) "Tapped \"$text\"." else "Found \"$text\" but it isn't tappable."
    }

    fun typeIntoText(field: String, text: String): String {
        val nodes = (if (field.isBlank()) collectInteractiveElements() else findNodes(field))
            .filter { it.isVisibleToUser && it.isEditable }
        val exact = nodes.filter { readableLabel(it, 8000)?.equals(field, true) == true || it.viewIdResourceName?.substringAfterLast('/') == field }
        val target = (exact.ifEmpty { nodes }).singleOrNull()
            ?: return "I couldn't find one unambiguous editable field called \"$field\". Use its visible index."
        return setEditableText(target, text, field)
    }

    fun typeByIndex(index: Int, text: String): String {
        val target = collectInteractiveElements().getOrNull(index - 1) ?: return "Editable index unavailable."
        return setEditableText(target, text, "#$index")
    }

    private fun setEditableText(target: AccessibilityNodeInfo, text: String, field: String): String {
        if (!target.isEditable || !target.isVisibleToUser || !target.isEnabled) return "The selected control is not editable."
        if (target.isPassword) return "Handoff required: password entry stays manual."
        if (text.length > 8000) return "Text is too long for this bounded entry."
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
            val t = readableLabel(node, 160)
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
    fun tapByIndex(index: Int, allowConsequential: Boolean = true): String {
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
        if (!allowConsequential && requiresHandoff(node, target)) return "Handoff required: review and tap this final control yourself."
        return if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK) || tapVisibleNode(node)) {
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
        // Use precisely the same list as tapByIndex, including unlabeled controls.
        // Previously invisible nodes shifted every subsequent index.
        val controls = collectInteractiveElements().mapIndexed { index, node ->
            val label = readableLabel(node, 120)
                ?: node.viewIdResourceName?.substringAfterLast('/') ?: "[unlabeled control]"
            "${index + 1}. $label [${if (node.isEditable) "edit" else "tap"}]"
        }.joinToString("\n")
        val budget = maxChars.coerceAtLeast(0)
        return ("Interactive:\n$controls\nText: " + readScreen((budget - controls.length - 25).coerceAtLeast(0))).take(budget)
    }

    /** Full editable values and bounds stay local; the model sees only the
     * bounded summary. Changes beyond a truncated label also invalidate plans. */
    fun controlFingerprint(): String? {
        val values = collectInteractiveElements().map { node ->
            val value = if (node.isPassword) "[protected]" else node.text?.toString().orEmpty()
            val label = node.contentDescription?.toString().orEmpty()
            if (value.length > 8000 || label.length > 8000) return null
            val bounds = Rect(); node.getBoundsInScreen(bounds)
            "${node.viewIdResourceName}|${node.className}|$bounds|${node.isEnabled}|$value|$label"
        }
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest((getActivePackage() + values.joinToString("\u0000")).toByteArray()).joinToString("") { "%02x".format(it) }
    }


    /** Collect all interactive (clickable/editable/checkable) nodes in tree order. */
    private fun requiresHandoff(node: AccessibilityNodeInfo, target: AccessibilityNodeInfo): Boolean {
        val labels = listOf(node, target).flatMap { listOfNotNull(it.text?.toString(), it.contentDescription?.toString()) }.filter { it.isNotBlank() }
        return labels.isEmpty() || labels.any(com.example.meowmba.core.agent.PhoneActionSafety::needsHandoff)
    }

    private fun readableLabel(node: AccessibilityNodeInfo, limit: Int): String? =
        if (node.isPassword) "[password hidden]" else node.text?.toString()?.takeIf { it.isNotBlank() }?.take(limit)
            ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.take(limit)

    fun hasVisibleText(text: String): Boolean = findNodes(text).any { it.isVisibleToUser }

    /** Human confirmation stays above the target app, so the user sees what
     * they are approving. Dismissing, timeout, cancellation or any changed
     * app/content invalidates it. A dispatched tap is never retried. */
    suspend fun confirmSocialTap(text: String?, index: Int?, goal: String): String {
        val node = if (index != null) collectInteractiveElements().getOrNull(index - 1)
            else text?.let { query ->
                val matches = findNodes(query).filter { it.isVisibleToUser }
                val exact = matches.filter { readableLabel(it, 500)?.equals(query.trim(), true) == true }
                (exact.ifEmpty { matches }).singleOrNull()
            }
        val label = node?.let { readableLabel(it, 500) }
        if (node == null || label == null || !com.example.meowmba.core.agent.PhoneActionSafety.canConfirmSocial(label))
            return "Handoff required: this action needs direct manual control."
        val pkg = getActivePackage()
        if (pkg == "unknown" || pkg == packageName) return "Handoff required: open the intended app first."
        val screen = confirmationScreen() ?: return "Handoff required: this screen is too large to verify safely."
        val ticket = com.example.meowmba.core.agent.PhoneConfirmation(pkg, screen, android.os.SystemClock.elapsedRealtime())
        return kotlinx.coroutines.withTimeoutOrNull(120_000) {
            kotlinx.coroutines.suspendCancellableCoroutine<String> { cont ->
                val handler = android.os.Handler(mainLooper)
                val dialog = android.app.AlertDialog.Builder(android.view.ContextThemeWrapper(this@MeowAccessibilityService, android.R.style.Theme_Material_Dialog_Alert))
                    .setTitle("Verify before acting")
                    .setMessage("Action: $label\nApp: $pkg\nRequest: ${goal.take(3000)}\n\nCheck the account, audience/recipient and content below. This action may be public or send a message. If the content isn't shown here, cancel and review it in the app first. Confirm only if correct.\n\n$screen")
                    .setNegativeButton("Cancel") { _, _ -> if (cont.isActive) cont.resume("Confirmation cancelled: nothing was tapped.") }
                    .setPositiveButton("Confirm action") { dialogInterface, _ ->
                        dialogInterface.dismiss()
                        handler.postDelayed({
                            if (!cont.isActive) return@postDelayed
                            val current = confirmationScreen()
                            if (current == null || !ticket.consume(getActivePackage(), current, android.os.SystemClock.elapsedRealtime())) {
                                cont.resume("Confirmation cancelled: the app or content changed. Review it and ask again.")
                            } else {
                                // Refresh this exact observed node. Do not look up a
                                // different matching Post/Send button after approval.
                                val clicked = node.refresh() && node.isVisibleToUser &&
                                    (node.performAction(AccessibilityNodeInfo.ACTION_CLICK) || tapVisibleNode(node))
                                cont.resume(if (clicked) "Confirmed tap dispatched: $label. Check the app for delivery; I won't retry automatically."
                                    else "Confirmation cancelled: that control is no longer available. Nothing was retried.")
                            }
                        }, 350)
                    }.create()
                dialog.setOnCancelListener { if (cont.isActive) cont.resume("Confirmation cancelled: nothing was tapped.") }
                dialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                cont.invokeOnCancellation { handler.post { dialog.dismiss() } }
                try { dialog.show() } catch (_: Exception) {
                    if (cont.isActive) cont.resume("Handoff required: the verification dialog could not open. Tap the action yourself.")
                }
            }
        } ?: "Confirmation cancelled: verification expired. Nothing was tapped."
    }

    private fun confirmationScreen(): String? {
        // Full editable content is included, not the planner's shortened labels.
        val edits = collectInteractiveElements().filter { it.isEditable }.map { readableLabel(it, 8001).orEmpty() }
        if (edits.any { it.length > 8000 }) return null
        val result = readScreenCompact(6000) + "\nFull editable fields:\n" + edits.joinToString("\n")
        return result.takeIf { it.length <= 16000 }
    }

    /** Some custom controls expose a visible label but reject ACTION_CLICK.
     * Use its freshly observed bounds, never model-invented coordinates. The
     * caller still checks sensitive labels; the next frame verifies the result. */
    private fun tapVisibleNode(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser || node.isPassword) return false
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        if (bounds.isEmpty || bounds.left < 0 || bounds.top < 0) return false
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build(), null, null)
    }

    private fun collectInteractiveElements(): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 4000 && result.size < 50) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser && (node.isClickable || node.isEditable || node.isCheckable || node.isLongClickable)) {
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
