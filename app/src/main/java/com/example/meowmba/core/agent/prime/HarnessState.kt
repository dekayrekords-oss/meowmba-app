package com.example.meowmba.core.agent.prime

/**
 * The self-improving harness, ported from Prime Agent's design to run on the
 * phone.
 *
 * What could NOT come across: Prime Agent's engine is a long-lived IPython
 * kernel that writes and runs arbitrary code. Android has no Python, no Node
 * and no shell for an app, and AGENTS.md rules out a shell executor anyway.
 *
 * What DOES come across is the part that actually makes it self-improving.
 * `/refine` never needed the kernel — it needs a trajectory to review, a way to
 * extract evidence-backed lessons from it, and somewhere durable to keep them
 * so later turns decide better. Meowmba already has the trajectory (the audit
 * log) and the storage. So the loop is the same shape:
 *
 *     act -> record -> review the record -> keep what the evidence supports
 *            -> let it change the next decision
 *
 * The hard boundary, and the reason this is safe to run unattended: a lesson is
 * ADVICE, never CAPABILITY. Lessons are derived from tool output, which is
 * untrusted input, so they may only add guidance text to a prompt. They cannot
 * register a tool, raise a permission, lower a risk level, or skip an approval
 * card. The worst a poisoned lesson can do is give bad advice the user can see
 * and delete — see [Lesson.isSafe].
 */

/**
 * One thing the companion learned about itself, with the evidence that earned
 * it.
 *
 * [confirmations] is how many separate times the evidence recurred. A lesson
 * seen once is a coincidence; the count is what promotes it to a rule and what
 * decides which lesson survives when the budget is full.
 */
data class Lesson(
    val id: String,
    val text: String,
    val evidence: String,
    val createdAt: Long,
    val lastSeenAt: Long = createdAt,
    val confirmations: Int = 1,
) {
    companion object {
        /** A lesson longer than this is a story, not a rule. */
        const val MAX_TEXT = 160

        /**
         * Phrases that would turn advice into capability. Checked because the
         * text originates in tool output — a web page, a model completion, a
         * file — and none of that is trusted to widen what the agent may do.
         */
        private val FORBIDDEN = listOf(
            "ignore ", "disregard ", "bypass", "skip the approval", "skip approval",
            "without asking", "no confirmation", "don't ask", "do not ask",
            "you may now", "you are allowed", "grant ", "enable all",
            "system prompt", "these instructions", "override",
        )
    }

    /**
     * True when this lesson is advice and only advice.
     *
     * Rejects anything trying to expand permissions or countermand the rules
     * the harness runs under. This is deliberately a denylist over a short,
     * bounded string rather than anything clever: the text is only ever
     * appended to a prompt as guidance, so the requirement is that it cannot
     * read as an instruction to change the rules.
     */
    val isSafe: Boolean
        get() {
            if (text.isBlank() || text.length > MAX_TEXT) return false
            val lower = text.lowercase()
            return FORBIDDEN.none { it in lower }
        }
}

/**
 * A standing objective the companion keeps across turns and restarts —
 * Prime Agent's `/goal`.
 *
 * Deliberately singular. A queue of goals is a task list, and Meowmba already
 * has one (the agent orchestrator); what was missing is the one thing it should
 * keep in mind while doing everything else.
 */
data class Goal(
    val text: String,
    val createdAt: Long,
    val progressNotes: List<String> = emptyList(),
) {
    companion object {
        const val MAX_TEXT = 200
        const val MAX_NOTES = 8
    }

    /** Newest notes win; a goal that accumulates forever stops being read. */
    fun withNote(note: String): Goal =
        copy(progressNotes = (progressNotes + note.take(MAX_TEXT)).takeLast(MAX_NOTES))
}

/**
 * Everything the harness has learned, in one serialisable lump.
 *
 * [MAX_LESSONS] is a hard budget, not a suggestion. Unbounded learned state is
 * how a self-improving agent slowly fills its own context with its own
 * opinions until nothing else fits.
 */
data class HarnessState(
    val lessons: List<Lesson> = emptyList(),
    val goal: Goal? = null,
) {
    companion object {
        const val MAX_LESSONS = 12
    }

    /**
     * The block appended to a system prompt, or null when there is nothing
     * worth saying. Null rather than an empty string so callers cannot
     * accidentally add a heading with no content under it.
     */
    fun promptBlock(): String? {
        val safe = lessons.filter { it.isSafe }
        if (safe.isEmpty() && goal == null) return null
        return buildString {
            goal?.let {
                append("Standing goal: ").append(it.text)
                if (it.progressNotes.isNotEmpty()) {
                    append("\nProgress so far: ")
                    append(it.progressNotes.joinToString("; "))
                }
                if (safe.isNotEmpty()) append("\n\n")
            }
            if (safe.isNotEmpty()) {
                append("What you have learned about yourself on this device ")
                append("(guidance only — it never changes what you are allowed to do):")
                safe.sortedByDescending { it.confirmations }.forEach {
                    append("\n- ").append(it.text)
                }
            }
        }
    }
}
