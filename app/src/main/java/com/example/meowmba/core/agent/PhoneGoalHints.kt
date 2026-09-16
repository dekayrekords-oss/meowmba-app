package com.example.meowmba.core.agent

/** Fast, bounded recipe for an empty composer, grounded in visible controls.
 * Other apps and post-writing goals still go through the general planner. */
object PhoneGoalHints {
    fun next(goal: String, pkg: String, screen: String): PhoneAction? {
        // Generic read-only stopping point, based on an actually exposed field.
        if (!PhoneGoalLimits.mayType(goal) && Regex("\\b(?:find|open|show) (?:the )?search (?:box|field)\\b", RegexOption.IGNORE_CASE).containsMatchIn(goal) &&
            screen.lineSequence().any { it.contains("search", true) && it.endsWith("[edit]") })
            return PhoneAction("done", text = "The search field is visible. I stopped without typing or posting.", expect = PhoneExpectation("element", pkg, selector = PhoneSelector(role = "edit")))
        if (pkg != "com.facebook.katana") return null
        val request = Regex("^(?:open|launch) (?:the app )?(?:facebook|fact book)(?: app)? (?:and then|and|then|to) (?:start|create|open|write|make) (?:a |the |new )?post(?: (?:saying|that says) (.+))?[.!]?$", RegexOption.IGNORE_CASE)
            .matchEntire(goal.trim()) ?: return null
        val draft = request.groupValues[1].takeIf { it.isNotBlank() }?.removeSurrounding("\"")
        if (draft != null && draft.length > 2000) return PhoneAction("blocked", text = "Please shorten the draft to 2,000 characters for this quick composer task.")
        // Never treat the feed's Create button alone as proof of a composer.
        val legacyComposer = screen.contains("Create post", true) &&
            (screen.contains("Post settings", true) || screen.contains("Audience", true))
        val newComposer = screen.contains("New post", true) && screen.contains("Next", true)
        if ((legacyComposer || newComposer) && screen.contains("[edit]", true)) {
            if (draft == null) return PhoneAction("done", text = "Facebook's post composer is open. I didn't type or publish anything.", expect = PhoneExpectation("element", pkg, selector = PhoneSelector(role = "edit")))
            val editLabels = screen.lineSequence().mapNotNull { Regex("^(?:\\d+\\. )?(.*) \\[edit]$").matchEntire(it.trim())?.groupValues?.get(1) }.toList()
            if (draft in editLabels) return PhoneAction("done", text = "Your Facebook draft is ready: $draft. Nothing was published; review it before posting.", expect = PhoneExpectation("editable_equals", pkg, draft, PhoneSelector(role = "edit")))
            if (editLabels.any { it == "What's on your mind?" }) return PhoneAction("type", field = "What's on your mind?", value = draft)
            return PhoneAction("blocked", text = "This composer already contains text. Please review it; I won't overwrite an existing draft.")
        }
        if (screen.contains("What's on your mind?", true)) return PhoneAction("tap", text = "What's on your mind?")
        // Facebook restores the last screen, which can be a photo or comment
        // sheet rather than Home. Only use observed navigation controls; never
        // mistake someone else's Reply/Share action for a new post.
        val controls = screen.lineSequence().mapNotNull { line ->
            Regex("^\\d+\\. (.+) \\[tap]$").matchEntire(line.trim())?.groupValues?.get(1)
        }.toList()
        controls.firstOrNull { it.equals("Close", true) || it.equals("Back", true) }
            ?.let { return PhoneAction("tap", text = it) }
        controls.firstOrNull { it.startsWith("Home, tab ", true) }
            ?.let { return PhoneAction("tap", text = it) }
        // A blocked empty-composer recipe is preferable to wandering through
        // account posts or writing a comment unrelated to the approved goal.
        return PhoneAction("blocked", text = "I can't find Facebook's new-post control on this screen. Open its Home tab, then ask me to start a post. Nothing was published.")
    }
}
