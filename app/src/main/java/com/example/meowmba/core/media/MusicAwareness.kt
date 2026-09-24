package com.example.meowmba.core.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.example.meowmba.core.alerts.RoadAlertListener

data class MusicPlayback(
    val playing: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val metadataAccess: Boolean = false,
) {
    val label: String get() = if (!playing) "No music playing" else
        title?.let { listOfNotNull(it, artist).joinToString(" — ") } ?: "Audio playing · track unavailable"
}

/**
 * Is music playing? Owner rule (2026-09-24): this runs only while Meow's mic is live, and only to
 * tell music from the user speaking ([isPlaying]: playback state, no titles or artists). The song
 * and artist are read once, only when the user asks ([nowPlaying]).
 * No microphone, notification contents or audio recording.
 */
class MusicAwareness(private val context: Context) {
    private data class Session(val controller: android.media.session.MediaController,
        val callback: android.media.session.MediaController.Callback,
        var playing: Boolean = false)
    private val sessions = linkedMapOf<android.media.session.MediaSession.Token, Session>()
    private var observing = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { attach(it.orEmpty()) }

    /** While the mic is live: follow playback state by callbacks (no metadata, no polling of players). */
    fun start() {
        if (observing) return
        observing = true
        try {
            sessionManager?.addOnActiveSessionsChangedListener(listener, ComponentName(context, RoadAlertListener::class.java), handler)
            attach(sessionManager?.getActiveSessions(ComponentName(context, RoadAlertListener::class.java)).orEmpty())
        } catch (_: SecurityException) { attach(emptyList()) }
    }

    fun stop() {
        if (!observing) return
        observing = false
        sessionManager?.removeOnActiveSessionsChangedListener(listener)
        attach(emptyList())
    }

    private fun attach(controllers: List<android.media.session.MediaController>) {
        val incoming = controllers.filter { it.packageName != context.packageName }.take(8)
        val tokens = incoming.map { it.sessionToken }.toSet()
        sessions.keys.filter { it !in tokens }.forEach { token ->
            sessions.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
        }
        incoming.forEach { controller ->
            val token = controller.sessionToken
            if (token !in sessions) {
                val callback = object : android.media.session.MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        sessions[token]?.playing = state?.state == PlaybackState.STATE_PLAYING
                    }
                    override fun onSessionDestroyed() {
                        sessions.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
                    }
                }
                val session = Session(controller, callback)
                sessions[token] = session
                controller.registerCallback(callback, handler)
                session.playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            }
        }
    }

    /** Music vs the user: true when another app is playing audio. No titles, no artists. */
    fun isPlaying(ownSpeech: Boolean): Boolean =
        (observing && sessions.values.any { it.playing }) ||
            (!ownSpeech && context.getSystemService(AudioManager::class.java)?.isMusicActive == true)

    /** Title and artist, read once — only when the user asks what is playing. */
    fun nowPlaying(): MusicPlayback {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val sessions = try {
            manager?.getActiveSessions(ComponentName(context, RoadAlertListener::class.java)).orEmpty()
        } catch (_: SecurityException) { return MusicPlayback(isPlaying(false), metadataAccess = false) }
        val playing = sessions.firstOrNull {
            it.packageName != context.packageName && it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: return MusicPlayback(isPlaying(false), metadataAccess = true)
        fun field(key: String) = playing.metadata?.getText(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }?.take(200)
        return MusicPlayback(true, field(MediaMetadata.METADATA_KEY_TITLE), field(MediaMetadata.METADATA_KEY_ARTIST), true)
    }
}
