package com.example.meowmba.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.example.meowmba.MainActivity

/**
 * Handles an active system assist session when the user triggers the digital assistant.
 * Launches the primary companion window or handles assist requests.
 */
class MeowmbaVoiceSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("ASSIST_TRIGGERED", true)
        }
        context.startActivity(intent)
        finish()
    }
}
