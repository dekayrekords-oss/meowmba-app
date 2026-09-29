package com.example.meowmba.core.audio

/**
 * Decides whether a finished voice transcript is meant for Meow before it costs a model call.
 * Measured on the Fold: "(clicking) (clicking) (", "Oh no.", "Ooh.", "Great." and overheard
 * fragments ("other will lead it by at least") were each sent to the LLM and answered.
 * Typed messages never pass through here.
 */
object VoiceTurnGate {
    enum class Decision { ACCEPT, DROP_NOISE, DROP_REACTION, DROP_AMBIENT }

    /**
     * @param addressed the wake phrase / Meow's name is in the utterance.
     * @param inConversation Meow spoke or the user took a turn moments ago (or follow-up listening).
     * @param meowAskedQuestion Meow's last reply was a question, so "no"/"okay" is an answer.
     */
    fun decide(text: String, addressed: Boolean, inConversation: Boolean, meowAskedQuestion: Boolean): Decision {
        if (NonSpeechSound.parse(text) != null || SpokenText.isNonSpeechAnnotation(text)) return Decision.DROP_NOISE
        if (addressed) return Decision.ACCEPT
        val words = words(text)
        if (words.isEmpty()) return Decision.DROP_NOISE
        if (words.size <= 6 && words.all { it in REACTIONS }) {
            return if (meowAskedQuestion && inConversation) Decision.ACCEPT else Decision.DROP_REACTION
        }
        if (inConversation) return Decision.ACCEPT
        return if (looksDirected(text, words)) Decision.ACCEPT else Decision.DROP_AMBIENT
    }

    /** A question, a request, or talk to "you" — the shapes people use when addressing an assistant. */
    internal fun looksDirected(text: String, words: List<String>): Boolean =
        text.trim().endsWith("?") || words.first() in REQUEST_STARTS ||
            (words.size >= 2 && "${words[0]} ${words[1]}" in REQUEST_PAIRS) || words.any { it in ADDRESS_WORDS }

    private fun words(text: String) = text.lowercase().replace('’', '\'')
        .replace(Regex("[^a-z0-9' ]+"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }

    private val REACTIONS = setOf(
        "oh", "ooh", "ah", "aw", "aww", "wow", "whoa", "woah", "oops", "ok", "okay", "alright", "all", "right",
        "great", "good", "nice", "cool", "sweet", "awesome", "perfect", "yes", "yeah", "yep", "yup", "no",
        "nope", "nah", "thanks", "thank", "you", "cheers", "dude", "really", "sure", "fine", "hmm", "huh",
        "uh", "um", "mm", "damn", "man", "lol", "haha", "yay", "ugh", "hey", "so", "well", "wait",
    )
    private val REQUEST_STARTS = setOf(
        "play", "pause", "stop", "resume", "skip", "open", "close", "show", "tell", "set", "remind", "remember",
        "add", "call", "text", "send", "message", "turn", "start", "search", "find", "look", "take", "read",
        "what", "what's", "whats", "when", "where", "who", "whose", "why", "how", "which", "can", "could",
        "would", "will", "should", "is", "are", "do", "does", "did", "please", "let's", "lets", "give", "make",
        "create", "check", "help", "hey", "hi", "hello", "write", "draft", "post", "save", "delete", "cancel",
        "schedule", "navigate", "explain", "describe", "translate", "summarize", "list", "put", "move",
    )
    private val REQUEST_PAIRS = setOf("i need", "i want", "i'd like", "i would")
    private val ADDRESS_WORDS = setOf("you", "your", "you're", "youre", "yourself", "meow", "meowmba")
}
