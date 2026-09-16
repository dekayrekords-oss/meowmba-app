package com.example.meowmba.core.audio

/** Text alone cannot identify a TV or its speaker. These high-precision
 * broadcast cues request clarification, never execute a suspected advert. */
object VoiceIntentGuard {
    fun needsClarification(text: String): Boolean {
        val n = text.lowercase().trim()
        if (Regex("^(hey |okay |ok )?meow\\b").containsMatchIn(n)) return false
        if (Regex("\\b(i heard|the tv|on television|commercial says|advert says|what does|why does)\\b").containsMatchIn(n)) return false
        val cues = listOf("limited time offer", "call now", "terms and conditions apply", "operators are standing by", "brought to you by", "subscribe to our channel", "after these messages", "don't miss out")
        return cues.count { it in n } >= 2
    }
}
