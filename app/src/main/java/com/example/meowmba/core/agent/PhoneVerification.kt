package com.example.meowmba.core.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable data class PhoneSelector(val label: String? = null, val viewId: String? = null, val role: String? = null) {
    fun valid() = listOfNotNull(label, viewId, role).all { it.isNotBlank() && it.length <= 200 } &&
        (label != null || viewId != null || role == "edit") && (role == null || role in setOf("edit", "tap"))
}
@Serializable data class PhoneExpectation(val kind: String, val pkg: String? = null,
    val text: String? = null, val selector: PhoneSelector? = null) {
    fun valid(): Boolean = (pkg == null || pkg.matches(Regex("[a-zA-Z0-9_.]{1,200}"))) && when (kind) {
        "package" -> !pkg.isNullOrBlank() && pkg != "unknown" && text == null && selector == null
        "visible_text" -> !text.isNullOrBlank() && text.length <= 200 && selector == null
        "editable_equals" -> text != null && text.length <= 8000 && selector?.valid() == true
        "element" -> selector?.valid() == true && text == null
        "changed" -> text == null && selector == null
        else -> false
    }
}
/** Values/bounds remain in memory. Password fields never carry labels or values. */
@Serializable data class PhoneControl(val index: Int, val label: String, val viewId: String?, val role: String,
    val value: String = "", val enabled: Boolean = true, val protected: Boolean = false,
    val bounds: String = "", val hint: String = "", val description: String = "",
    val className: String = "", val clickable: Boolean = false, val checkable: Boolean = false,
    val checked: Boolean = false, val selected: Boolean = false, val labelFromValue: Boolean = false,
    val windowId: Int = -1)
data class PhoneObservation(val pkg: String, val version: Long, val fingerprint: String,
    val controls: List<PhoneControl>, val visibleText: List<String>, val prompt: String) {
    fun find(selector: PhoneSelector): PhoneControl? = if (!selector.valid()) null else controls.filter {
        !it.protected && it.enabled && (selector.role == null || it.role == selector.role) &&
            (selector.viewId == null || it.viewId == selector.viewId) &&
            (selector.label == null || it.label.equals(selector.label, true))
    }.singleOrNull()
    /** Only structural fields and fixed navigation words enter this persisted
     * signature. Query values, result titles and arbitrary user content do not. */
    fun recipeContext(): String {
        val words = setOf("home", "inbox", "messages", "orders", "products", "library", "settings", "search", "search results", "results", "explore", "discover", "go", "find")
        val shape = controls.map { listOf(it.viewId.orEmpty(), it.className, it.role, it.bounds,
            if (!it.labelFromValue && it.label.lowercase(java.util.Locale.ROOT) in words) it.label.lowercase(java.util.Locale.ROOT) else "") } +
            listOf(visibleText.map { it.lowercase(java.util.Locale.ROOT) }.filter { it in words }.distinct().sorted())
        val bytes = kotlinx.serialization.json.Json.encodeToString(shape).toByteArray()
        return java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
    fun sameControls(other: PhoneObservation) = pkg == other.pkg && version == other.version && fingerprint == other.fingerprint && controls == other.controls
    fun stableWith(other: PhoneObservation) = sameControls(other) && visibleText == other.visibleText
}
object PhoneVerification {
    fun isSearchField(node: PhoneControl): Boolean = node.role == "edit" && !node.protected &&
        ((!node.labelFromValue && Regex("^(search|find)\\b", RegexOption.IGNORE_CASE).containsMatchIn(node.label.trim())) ||
            node.viewId?.substringAfter(":id/")?.let { Regex("(?:^|_)(search|query)(?:_|$)", RegexOption.IGNORE_CASE).containsMatchIn(it) } == true)

    fun effects(action: PhoneAction, before: PhoneObservation, after: PhoneObservation): Boolean =
        holds(action.expect, before, after) && (action.type != "type" || holds(
            PhoneExpectation("editable_equals", before.pkg, action.value, action.selector), before, after))

    /** A heading left over from a prior search cannot prove the new query returned.
     * Require the query in a real search field plus query-specific visible results,
     * or a results heading with new non-clock result content after that entry. */
    fun searchResult(request: PhoneSearchRequest, frame: PhoneObservation, trace: List<VerifiedPhoneStep>, expectation: PhoneExpectation? = null): Boolean {
        if (frame.controls.none { isSearchField(it) && it.value.equals(request.query, true) }) return false
        val query = request.query.trim()
        val specific = frame.visibleText.filter { it.equals(query, true) || it.equals("results for $query", true) }
        val entry = trace.lastOrNull { it.action.type == "type" && it.action.value == request.query }
        if (specific.isNotEmpty() && (entry == null || specific.any { it !in entry.before.visibleText } ||
                entry.before.controls.any { isSearchField(it) && it.value.equals(query, true) })) return true
        // A model can identify a concrete new result title for this run. Generic
        // headings, clocks and editor echoes cannot serve as that evidence. Such
        // titles are not generalized into automatic recipes by the compiler.
        if (entry == null || expectation?.kind != "visible_text") return false
        val title = expectation.text ?: return false
        return title.lowercase() !in setOf("search", "results", "search results", "loading", "loading…", query.lowercase()) &&
            title.any { it.isLetter() } && !Regex("^(?:clock|time)?\\s*\\d{1,2}:\\d{2}.*$", RegexOption.IGNORE_CASE).matches(title) &&
            title in frame.visibleText && title !in entry.before.visibleText
    }

    fun holds(expect: PhoneExpectation?, before: PhoneObservation, after: PhoneObservation): Boolean {
        if (expect?.valid() != true || (expect.pkg != null && expect.pkg != after.pkg)) return false
        return when (expect.kind) {
            "package" -> expect.pkg == after.pkg
            "changed" -> before.pkg != after.pkg || before.fingerprint != after.fingerprint || before.visibleText != after.visibleText
            "element" -> after.find(expect.selector!!) != null
            "editable_equals" -> after.find(expect.selector!!)?.let { it.role == "edit" && it.value == expect.text } == true
            "visible_text" -> after.visibleText.any { it.equals(expect.text, true) } || after.controls.any {
                !it.protected && it.label.equals(expect.text, true)
            }
            else -> false
        }
    }
    /** Index references are immediately converted to semantic identity. Ambiguous or
     * missing identities never fall back to the first node or an old coordinate. */
    fun ground(action: PhoneAction, frame: PhoneObservation): PhoneAction? {
        if (action.type !in setOf("tap", "tap_index", "type")) return action
        val selector = action.selector
        val candidates = frame.controls.filter { !it.protected && it.enabled && (action.type != "type" || it.role == "edit") }
        val node = when {
            selector != null -> if (selector.valid()) frame.find(selector) else null
            action.index != null -> candidates.singleOrNull { it.index == action.index }
            else -> {
                val label = if (action.type == "type") action.field ?: action.text else action.text
                if (label.isNullOrBlank()) return null
                candidates.filter { it.label.equals(label, true) || it.viewId == label }.singleOrNull()
                    ?: candidates.filter { !label.isNullOrBlank() && it.label.contains(label, true) }.singleOrNull()
            }
        } ?: return null
        if (action.type == "type") {
            if (node.role != "edit" || action.value == null || action.value.length > 8000) return null
            if (node.value.isNotBlank() && node.value != action.value && !isSearchField(node)) return null
        }
        if (action.index != null && action.index != node.index) return null
        val identity = if (!node.viewId.isNullOrBlank() && frame.find(PhoneSelector(viewId = node.viewId, role = node.role)) == node)
            PhoneSelector(viewId = node.viewId, role = node.role)
        else if (node.role == "edit" && frame.find(PhoneSelector(role = "edit")) == node) PhoneSelector(role = "edit")
        else PhoneSelector(label = node.label.takeIf { it.isNotBlank() }, viewId = node.viewId, role = node.role)
        if (!identity.valid() || frame.find(identity) != node) return null
        val expectation = action.expect ?: if (action.type == "type") PhoneExpectation("editable_equals", frame.pkg, action.value ?: "", identity)
            else PhoneExpectation("changed")
        return action.copy(type = if (action.type == "tap") "tap_index" else action.type,
            index = node.index, selector = identity, expect = expectation)
    }
    fun description(expect: PhoneExpectation): String = when (expect.kind) {
        "package" -> "${expect.pkg} is open"
        "editable_equals" -> "the requested text is present in the selected field"
        "element" -> "the requested control is visible"
        "visible_text" -> "\"${expect.text?.take(160)}\" is visible"
        else -> "the screen changed"
    }
}
