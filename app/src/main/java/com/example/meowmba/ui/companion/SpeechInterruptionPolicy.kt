package com.example.meowmba.ui.companion

/** An environment never makes an ordinary message important. The producer supplies its purpose. */
object SpeechInterruptionPolicy {
    enum class Purpose { REPLY, ROUTINE, ANNOUNCEMENT, URGENT }
    data class Context(val quiet: Boolean, val muted: Boolean, val driving: Boolean,
        val mediaPlaying: Boolean, val privateAudio: Boolean, val voiceReply: Boolean,
        val mode: SpeechMode)

    fun maySpeak(context: Context, purpose: Purpose): Boolean = with(context) {
        // "Be quiet" is an explicit stop-until-addressed instruction, including queued output.
        if (quiet || muted) return false
        if (purpose == Purpose.URGENT) return true
        if (purpose == Purpose.REPLY) return voiceReply || mode == SpeechMode.ALWAYS
        if (driving || mediaPlaying) return purpose == Purpose.ANNOUNCEMENT && mode != SpeechMode.REPLIES
        when (mode) {
            SpeechMode.ALWAYS -> true
            SpeechMode.REPLIES -> false
            SpeechMode.IMPORTANT -> purpose == Purpose.ANNOUNCEMENT && privateAudio
        }
    }

    fun isQuietRequest(raw: String): Boolean {
        val text = SpeechIntents.stripAddress(com.example.meowmba.core.audio.WakePhraseMatcher.normalize(raw))
            .removePrefix("please ").removeSuffix(" please")
        return text in setOf("be quiet", "stay quiet", "stop talking", "stop speaking", "shush", "shut up", "silence", "quiet")
    }
}
