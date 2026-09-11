package com.example.meowmba.core.agentic

/**
 * Phase 5 model surface for the agentic planner.
 *
 * [complete] is the only required hook; real HTTP/LLM adapters plug in later.
 * Default wiring uses [StubModelAdapter] so the loop compiles without network.
 */
fun interface ModelAdapter {
    /** Complete a prompt to plain text. Must never receive secrets. */
    fun complete(prompt: String): String
}

/**
 * Deterministic stub: returns a canned plan string the heuristic planner can
 * parse. Optional later: swap for an API-backed adapter behind the same flag.
 */
class StubModelAdapter(
    private val canned: String = CANNED_PLAN,
) : ModelAdapter {
    override fun complete(prompt: String): String {
        // Prompt is ignored for the stub; keep signature for API hook point.
        @Suppress("UNUSED_VARIABLE")
        val ignored = prompt
        return canned
    }

    companion object {
        /** Canned plan: prefer ssh_exec when a profile exists; else noop. */
        const val CANNED_PLAN: String =
            "PLAN: If an SSH profile is available, propose tool=ssh_exec " +
                "argv=[uname,-a]; otherwise tool=noop."
    }
}
