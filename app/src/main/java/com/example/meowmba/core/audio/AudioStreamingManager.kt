package com.example.meowmba.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Manages audio focus and coordinates playback of speech and sounds.
 *
 * Focus is a stack, and every `requestAudioFocus` pushes a layer: a listener
 * mode + a speak both call [requestFocus], and a turn can stack several. The
 * matching `abandonAudioFocus` pops ONE layer, so after a turn that requested
 * two or more times, the app's own earlier request stayed the focus owner and
 * playing music never got its volume raised back. The [focusHeld] flag keeps
 * request/abandon one-to-one: request once, release once.
 */
class AudioStreamingManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var focusHeld = false

    fun requestFocus(): Boolean {
        if (focusHeld) return true
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                // MAY_DUCK + delayed gain requires a listener even though this app
                // never acts on focus changes — build() throws without one
                // ("Can't use delayed focus or pause on duck without a listener").
                // The flag is exchanged when grant lands, which is enough here.
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { /* Handle change */ }
                .build()
            runCatching { audioManager.requestAudioFocus(focusRequest!!) }
                .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_GRANTED) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                audioManager.requestAudioFocus(
                    { /* Handle change */ },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_GRANTED) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        focusHeld = granted
        return granted
    }

    fun abandonFocus() {
        if (!focusHeld) return
        focusHeld = false
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }
}
