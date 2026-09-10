package com.example.meowmba.core.diagnostics

/** Explicit report intake is local state, not a conversation the LLM can pretend to save. */
class BugReportIntake {
    sealed interface Action {
        object Ask : Action
        object Cancel : Action
        data class Save(val detail: String) : Action
    }
    private var pendingUntil = 0L
    fun accept(text: String, now: Long): Action? {
        val t = text.trim()
        val normalized = t.lowercase().trimEnd('.', '!', '?')
        val request = Regex("^(?:(?:please|i said|i want to|i wanted to|i need to|can i|can you|let me)\\s+)*(?:report|log|record|file) (?:a |the )?bug(?: report)?(?:[,:. ]+(.*))?$", RegexOption.IGNORE_CASE).matchEntire(t)
        if (request != null) {
            val detail = request.groupValues[1].trim()
            if (detail.isNotEmpty()) { pendingUntil = 0; return Action.Save(detail) }
            pendingUntil = now + 120_000
            return Action.Ask
        }
        if (normalized in setOf("cancel", "never mind", "nevermind", "cancel bug report", "stop reporting")) {
            if (pendingUntil > now) { pendingUntil = 0; return Action.Cancel }
            return null
        }
        if (pendingUntil > now) {
            pendingUntil = 0
            // A fresh question/navigation request exits intake instead of trapping
            // the conversation on a supposed bug (observed with location questions).
            if (Regex("^(where|what|how|who|when|do you|can you|navigate|let['’]?s go|take me|remember|open)\\b",RegexOption.IGNORE_CASE).containsMatchIn(t)) return null
            if (t.isNotBlank()) return Action.Save(t)
        }
        return PossibleBugReports.extract(t)?.let { Action.Save(it) }
    }
}
