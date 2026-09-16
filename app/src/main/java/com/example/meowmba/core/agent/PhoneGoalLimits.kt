package com.example.meowmba.core.agent

/** Conservative hard limits outside model prompting. A draft is not permission
 * to publish. The native confirmation is still required when sending is asked. */
object PhoneGoalLimits {
    fun mayType(goal: String): Boolean = !Regex(
        "(?:do not|don't|never|without|stop before)\\s+(?:type|typing|enter(?:ing)? text)|navigation[ -]only",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(goal)

    fun maySubmit(goal: String): Boolean {
        if (Regex("(?:do not|don't|never|without|stop before)[^.]*\\b(?:send|sending|post|posting|publish|publishing|share|sharing|reply|replying)\\b", RegexOption.IGNORE_CASE).containsMatchIn(goal)) return false
        return Regex("(?:^|\\b(?:and|then|to))\\s*(?:please )?(?:send|post|publish|share|reply|ask)\\b", RegexOption.IGNORE_CASE).containsMatchIn(goal.trim())
    }
}
