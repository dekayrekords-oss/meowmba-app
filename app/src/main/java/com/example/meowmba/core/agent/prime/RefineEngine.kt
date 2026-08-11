package com.example.meowmba.core.agent.prime

/**
 * `/refine` — review the trajectory, keep what the evidence supports.
 *
 * Pure, and deliberately NOT a model call. Prime Agent can afford to ask a
 * frontier model to introspect on a desktop; a phone cannot spend a
 * multi-second on-device generation every time it wants to notice something,
 * and a model asked "what did you learn?" will happily invent a lesson from a
 * single sample. Counting real recurrences in the audit log is cheaper, and it
 * is the part that actually needs to be right.
 *
 * The rule throughout: a lesson requires REPEATED evidence. One failure is
 * weather. [MIN_OCCURRENCES] failures of the same thing is a pattern worth
 * remembering.
 */
object RefineEngine {

    /** One line of the trajectory — an audit entry, decoupled from Room. */
    data class Step(
        val timestamp: Long,
        val action: String,
        val detail: String,
    )

    /** How many times a pattern must recur before it becomes a lesson. */
    const val MIN_OCCURRENCES = 3

    /**
     * Actions that mean a tool did not do what was asked. Matched on the audit
     * action verb rather than the detail, because details are free text.
     */
    private val FAILURE_ACTIONS = setOf("task.failed", "tool.failed", "task.timeout")

    private val SUCCESS_ACTIONS = setOf("task.completed", "tool.succeeded")

    /**
     * Review [trajectory] and propose lessons.
     *
     * Returns candidates only — nothing is committed here. [merge] decides what
     * survives, so a caller can show the user what changed before it takes
     * effect.
     */
    fun refine(trajectory: List<Step>, now: Long): List<Lesson> {
        if (trajectory.size < MIN_OCCURRENCES) return emptyList()
        val out = mutableListOf<Lesson>()

        // 1. A tool that keeps failing the same way. The most useful thing the
        //    companion can learn is which of its own capabilities do not work
        //    on this device — a missing permission, absent hardware, a server
        //    that is never up.
        trajectory
            .filter { it.action in FAILURE_ACTIONS }
            .groupBy { toolOf(it.detail) to reasonOf(it.detail) }
            .filter { (key, steps) -> key.first != null && steps.size >= MIN_OCCURRENCES }
            .forEach { (key, steps) ->
                val (tool, reason) = key
                out += Lesson(
                    id = "fail:$tool:${reason.hashCode()}",
                    text = "\"$tool\" keeps failing here ($reason). Say so plainly " +
                        "instead of retrying it.",
                    evidence = "${steps.size} failures, most recent " +
                        "${steps.maxOf { it.timestamp }}",
                    createdAt = now,
                    lastSeenAt = steps.maxOf { it.timestamp },
                    confirmations = steps.size,
                )
            }

        // 2. A tool that has NEVER succeeded, despite being tried repeatedly.
        //    Distinct from the above: the failures may each have a different
        //    reason, which is itself the signal that the capability is absent
        //    rather than flaky.
        val attempts = trajectory.mapNotNull { step -> toolOf(step.detail)?.let { it to step } }
        attempts.groupBy({ it.first }, { it.second })
            .filter { (_, steps) ->
                steps.size >= MIN_OCCURRENCES && steps.none { it.action in SUCCESS_ACTIONS }
            }
            .forEach { (tool, steps) ->
                val id = "never:$tool"
                if (out.none { it.id.startsWith("fail:$tool:") }) {
                    out += Lesson(
                        id = id,
                        text = "\"$tool\" has never worked on this device. Prefer " +
                            "another route, and tell the user it is unavailable.",
                        evidence = "${steps.size} attempts, no success",
                        createdAt = now,
                        lastSeenAt = steps.maxOf { it.timestamp },
                        confirmations = steps.size,
                    )
                }
            }

        return out.filter { it.isSafe }
    }

    /**
     * Fold candidates into the existing state.
     *
     * Re-seeing a lesson reinforces it rather than duplicating it, and the
     * budget evicts by weakest-and-stalest so a rule that keeps proving itself
     * outlives one that fired once a month ago.
     */
    fun merge(state: HarnessState, candidates: List<Lesson>): HarnessState {
        if (candidates.isEmpty()) return state
        val byId = state.lessons.associateBy { it.id }.toMutableMap()
        candidates.filter { it.isSafe }.forEach { candidate ->
            val existing = byId[candidate.id]
            byId[candidate.id] = if (existing == null) {
                candidate
            } else {
                existing.copy(
                    // Recount rather than accumulate: the trajectory is a
                    // rolling window, so adding would inflate the same
                    // evidence every time refine ran.
                    confirmations = maxOf(existing.confirmations, candidate.confirmations),
                    lastSeenAt = maxOf(existing.lastSeenAt, candidate.lastSeenAt),
                    evidence = candidate.evidence,
                )
            }
        }
        val kept = byId.values
            .sortedWith(compareByDescending<Lesson> { it.confirmations }.thenByDescending { it.lastSeenAt })
            .take(HarnessState.MAX_LESSONS)
        return state.copy(lessons = kept)
    }

    /**
     * Drop lessons whose evidence has gone stale.
     *
     * A self-improving agent that only ever accumulates rules eventually acts
     * on a device that no longer exists — the permission was granted, the
     * server came back, the hardware was plugged in. Forgetting is part of
     * learning.
     */
    fun expire(state: HarnessState, now: Long, maxAgeMs: Long = 14L * 24 * 3600 * 1000): HarnessState =
        state.copy(lessons = state.lessons.filter { now - it.lastSeenAt <= maxAgeMs })

    /** `phone.launch_app` out of "phone.launch_app failed: ...". */
    private fun toolOf(detail: String): String? =
        Regex("""\b([a-z_]+\.[a-z_]+)\b""").find(detail)?.groupValues?.get(1)

    /**
     * A coarse reason bucket, so "no app matches Clock" and "no app matches
     * Spotify" count as the SAME pattern. Grouping on the raw string would
     * make every failure unique and nothing would ever reach the threshold.
     */
    private fun reasonOf(detail: String): String {
        val d = detail.lowercase()
        return when {
            "permission" in d -> "missing permission"
            "not connected" in d || "unreachable" in d || "no body" in d -> "hardware not connected"
            "timed out" in d || "timeout" in d -> "times out"
            "no installed app" in d || "no app" in d -> "target not found"
            "network" in d || "host" in d || "connect" in d -> "network unreachable"
            else -> "repeated error"
        }
    }
}
