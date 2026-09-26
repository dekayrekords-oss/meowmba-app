package com.example.meowmba.core.agent

/** Per-run destination choice. Model suggestions are references to observed controls,
 * never page names invented by the model. Choosing a destination is not publishing. */
class FacebookPageSelection {
    var selected: String? = null
        private set
    fun prompt(): String = if (selected == null) """
        FACEBOOK POST: First open Facebook's own account/profile switcher (Menu, See all profiles).
        Ask the user which Page/profile to use BEFORE composing. Never build an HTML preview.
        Navigate only to the signed-in account's available Pages/profiles, not search results or suggested Pages.
        When the actual switcher is visible, return {"action":"choose_facebook_page","value":"2,3"}
        with the exact indices of the visible selectable Page/profile rows. Include only account destinations,
        no create-page, add-account, logout or navigation controls. The app will show their actual labels
        to the user. Do not choose a destination yourself. If more rows exist, the user can request more.
        If the picker isn't accessible, stop and explain; never invent a list or assume the personal profile.
    """.trimIndent() else """
        FACEBOOK POST: The user selected this observed destination label: ${kotlinx.serialization.json.JsonPrimitive(selected!!)}.
        Use only that Page/profile. Verify its visible identity before typing or posting. If identity is missing,
        navigate to its profile or stop. Compose the post inside Facebook, never in a local HTML preview.
        Drafting is not publishing. Keep the existing separate final publish confirmation.
    """.trimIndent()

    fun candidates(action: PhoneAction, frame: PhoneObservation): List<PhoneControl> {
        if (frame.pkg != PACKAGE || action.type != "choose_facebook_page" || selected != null) return emptyList()
        val picker = frame.visibleText.any { it.trim().lowercase() in pickerHeadings }
        if (!picker) return emptyList()
        val raw = action.value?.split(',') ?: return emptyList()
        if (raw.isEmpty() || raw.size > 30) return emptyList()
        val indices = raw.map { it.trim().toIntOrNull() ?: return emptyList() }
        if (indices.distinct().size != indices.size) return emptyList()
        return indices.map { index ->
            val node = frame.controls.singleOrNull { it.index == index } ?: return emptyList()
            if (!node.enabled || node.protected || node.role != "tap" || node.label.isBlank() ||
                node.label.length > 200 || node.label.trim().lowercase() in navigation ||
                PhoneActionSafety.needsHandoff(node.label) ||
                frame.find(PhoneSelector(label = node.label, viewId = node.viewId, role = "tap")) != node) return emptyList()
            node
        }
    }
    fun choose(node: PhoneControl): PhoneAction {
        selected = node.label
        return PhoneAction("tap", selector = PhoneSelector(label = node.label, viewId = node.viewId, role = "tap"), expect = PhoneExpectation("changed", PACKAGE))
    }
    fun guard(action: PhoneAction, frame: PhoneObservation): PhoneAction {
        if (action.type == "blocked") return action
        if (selected == null) {
            val allowed = when (action.type) {
                "launch_app" -> action.text?.lowercase() in setOf("facebook", "fact book", "face book")
                "back", "wait", "scroll" -> frame.pkg == PACKAGE
                "tap", "tap_index" -> frame.pkg == PACKAGE && PhoneVerification.ground(action, frame)?.selector?.let(frame::find)?.label?.let(::isNavigation) == true
                else -> false
            }
            return if (allowed) action else blocked("Choose a Facebook Page/profile first. Open Facebook → Menu → See all profiles, then ask me to make the post again. Nothing was posted.")
        }
        if (action.type == "choose_facebook_page") return blocked("The destination is already selected. Nothing was posted.")
        if (action.type == "launch_app" && action.text?.lowercase() !in setOf("facebook", "fact book", "face book"))
            return blocked("This post belongs in Facebook. Nothing was posted.")
        if (action.type == "done" && action.expect?.kind != "editable_equals")
            return blocked("The Facebook draft has not been verified in its composer. Nothing was posted.")
        if (action.type == "type" || action.type == "done" || PhoneVerification.ground(action, frame)?.selector?.let(frame::find)?.let { PhoneActionSafety.needsHandoff(it.label) } == true) {
            val identity = selected!!
            val visible = (frame.visibleText + frame.controls.map { it.label }).any { it.equals(identity, true) }
            if (frame.pkg != PACKAGE || !visible) return blocked("I cannot verify the selected Facebook destination on this screen. Please open $identity's composer and review the account before continuing. Nothing was posted.")
        }
        return action
    }
    private fun blocked(message: String) = PhoneAction("blocked", text = message)
    companion object {
        const val PACKAGE = "com.facebook.katana"
        private fun isNavigation(label: String): Boolean {
            val normalized = label.trim().lowercase()
            return normalized in navigation || Regex("^(?:menu|home),? tab [1-9] of [1-9]$").matches(normalized)
        }
        private val pickerHeadings = setOf("select profile", "switch profile", "switch profiles", "your profiles", "your profiles and pages", "profiles and pages", "see all profiles", "select a profile", "choose a profile")
        private val navigation = setOf("menu", "pages", "your pages", "profiles", "see all profiles", "see more profiles", "switch profile", "switch profiles", "see more", "back", "close", "home", "select profile", "your profiles and pages", "create page", "create a page", "add account", "add an account", "log out", "logout", "settings", "cancel")
    }
}
