package com.example.meowmba.core.agent

/** Conservative guard for autonomous navigation. Explicit single-step user
 * taps use their separate approval path; a planner cannot authorize itself. */
object PhoneActionSafety {
    /** Only social/message actions get the extra on-screen confirmation.
     * Purchases, deletion, account permissions and unidentified targets still
     * require direct manual control. */
    fun canConfirmSocial(label: String): Boolean =
        Regex("^(post|publish|send|share|reply|like|react|downvote|upvote)\\b", RegexOption.IGNORE_CASE).containsMatchIn(label.trim()) &&
            !Regex("\\b(pay|payment|purchase|buy|delete|remove|uninstall|transfer|allow|permission|password)\\b", RegexOption.IGNORE_CASE).containsMatchIn(label)

    fun needsHandoff(label: String): Boolean {
        val words = label.lowercase().replace('_', ' ').replace('-', ' ').trim()
        if (words.none { it.isLetter() }) return true
        return Regex("\\b(send|publish|submit|pay|purchase|buy|checkout|delete|remove|uninstall|transfer|confirm|allow|accept|agree|pair|like|react|follow|join|subscribe)\\b").containsMatchIn(words) ||
            Regex("^(share|reply|downvote|upvote)\\b").containsMatchIn(words) ||
            Regex("^(post|share|reply|order|book)( now| all)?[.!]?$").matches(words)
    }
}
