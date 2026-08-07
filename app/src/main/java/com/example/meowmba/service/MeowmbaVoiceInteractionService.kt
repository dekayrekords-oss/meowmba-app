package com.example.meowmba.service

import android.service.voice.VoiceInteractionService

/**
 * System-level Voice Interaction Service allowing MEOWMBA to act as the default Android Assistant.
 * When configured in Android Settings > Apps > Default Apps > Digital Assistant App, long-pressing
 * the Home button or using the corner assist gesture triggers the companion voice session.
 */
class MeowmbaVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
    }

    override fun onShutdown() {
        super.onShutdown()
    }
}
