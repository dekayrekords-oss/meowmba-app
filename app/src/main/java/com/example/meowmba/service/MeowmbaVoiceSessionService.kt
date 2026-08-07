package com.example.meowmba.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Service spawning assistant voice interaction sessions when system assist cues fire.
 */
class MeowmbaVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return MeowmbaVoiceSession(this)
    }
}
