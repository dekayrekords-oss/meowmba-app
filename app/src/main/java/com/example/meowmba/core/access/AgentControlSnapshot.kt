package com.example.meowmba.core.access

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.meowmba.core.agent.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** One tree traversal supplies the prompt, verification data and dispatch nodes.
 * Never combine a prompt from one snapshot with indices from another. */
internal class AgentControlSnapshot(
    val observation: PhoneObservation,
    val controls: List<AccessibilityNodeInfo>,
    private val owned: List<AccessibilityNodeInfo>,
) : AutoCloseable {
    override fun close() { owned.forEach { it.recycle() } }
    companion object {
        private const val MAX_NODES = 4000
        private const val MAX_CONTROLS = 200
        fun control(node: AccessibilityNodeInfo, index: Int): PhoneControl? {
            val protected = node.isPassword
            val text = if (protected) "" else node.text?.toString().orEmpty()
            val hint = if (protected) "" else node.hintText?.toString().orEmpty()
            val desc = if (protected) "" else node.contentDescription?.toString().orEmpty()
            if (listOf(text, hint, desc).any { it.length > 8000 }) return null
            val label = hint.takeIf(String::isNotBlank) ?: desc.takeIf(String::isNotBlank) ?: text
            val bounds = Rect(); node.getBoundsInScreen(bounds)
            return PhoneControl(index, label, node.viewIdResourceName, if (node.isEditable) "edit" else "tap",
                text, node.isEnabled, protected, bounds.toShortString(), hint, desc, node.className?.toString().orEmpty(),
                node.isClickable, node.isCheckable, node.isChecked, node.isSelected,
                labelFromValue = hint.isBlank() && desc.isBlank(), windowId = node.windowId)
        }
        fun capture(service: AccessibilityService): AgentControlSnapshot? {
            val root = service.rootInActiveWindow ?: return null
            val pkg = root.packageName?.toString()?.takeIf { it.isNotBlank() && it != "unknown" }
                ?: run { root.recycle(); return null }
            val seen = hashSetOf(root)
            val owned = mutableListOf(root)
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            val controls = mutableListOf<PhoneControl>()
            val texts = mutableListOf<String>()
            var retained = false
            try {
                var visited = 0
                while (queue.isNotEmpty()) {
                    if (++visited > MAX_NODES) return null
                    val node = queue.removeFirst()
                    if (!node.isPassword) {
                        if (node.isVisibleToUser) {
                            if (node.isClickable || node.isEditable || node.isCheckable || node.isLongClickable) {
                                if (controls.size >= MAX_CONTROLS) return null
                                controls += control(node, controls.size + 1) ?: return null
                                nodes += node
                            }
                            if (!node.isEditable) node.text?.toString()?.takeIf { it.isNotBlank() && it.length <= 200 }?.let {
                                if (texts.size < 240) texts += it
                            }
                        }
                        if (node.childCount > MAX_NODES - owned.size) return null
                        for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
                            if (seen.add(child)) { owned += child; queue.addLast(child) }
                            else if (owned.none { it === child }) child.recycle()
                        }
                    }
                }
                val checkRoot = service.rootInActiveWindow ?: return null
                val samePackage = checkRoot.packageName?.toString() == pkg
                if (checkRoot !== root) checkRoot.recycle()
                if (!samePackage) return null
                val version = runCatching { service.packageManager.getPackageInfo(pkg, 0).longVersionCode }.getOrDefault(-1L)
                val encoded = Json.encodeToString(controls)
                val fingerprint = MessageDigest.getInstance("SHA-256").digest((pkg + encoded).toByteArray()).joinToString("") { "%02x".format(it) }
                val interactive = controls.joinToString("\n") { c ->
                    val label = if (c.role == "edit" && c.value.isNotBlank()) c.value else c.label
                    "${c.index}. ${label.take(200)} [${c.role}]"
                }
                val semantics = controls.joinToString("\n") { "#${it.index} role=${it.role} viewId=${it.viewId.orEmpty()} label=${Json.encodeToString(it.label.take(200))}" }
                val prompt = "Interactive:\n${interactive.take(6000)}\nText: ${texts.distinct().joinToString("; ").take(4000)}\nSemantic controls (reference data):\n${semantics.take(6000)}"
                retained = true
                return AgentControlSnapshot(PhoneObservation(pkg, version, fingerprint, controls, texts.distinct(), prompt), nodes, owned)
            } finally { if (!retained) owned.forEach { it.recycle() } }
        }
    }
}
