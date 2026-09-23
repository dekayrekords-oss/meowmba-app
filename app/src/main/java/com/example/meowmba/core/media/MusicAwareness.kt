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

/** Read-only playback metadata. No microphone, notification contents or audio recording. */
class MusicAwareness(private val context: Context) {
    private data class Session(val controller: android.media.session.MediaController,
        val callback: android.media.session.MediaController.Callback,
        var playing: Boolean = false, var metadata: MediaMetadata? = null)
    private val sessions = linkedMapOf<android.media.session.MediaSession.Token, Session>()
    private var observing = false
    private var metadataAccess = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { attach(it.orEmpty()) }

    /** Foreground registration. Metadata is fetched once, then maintained by callbacks. */
    fun start() {
        if (observing) return
        observing = true
        try {
            sessionManager?.addOnActiveSessionsChangedListener(listener, ComponentName(context, RoadAlertListener::class.java), handler)
            metadataAccess = sessionManager != null
            attach(sessionManager?.getActiveSessions(ComponentName(context, RoadAlertListener::class.java)).orEmpty())
        } catch (_: SecurityException) { metadataAccess = false; attach(emptyList()) }
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
                    override fun onMetadataChanged(metadata: MediaMetadata?) { sessions[token]?.metadata = metadata }
                    override fun onSessionDestroyed() {
                        sessions.remove(token)?.let { it.controller.unregisterCallback(it.callback) }
                    }
                }
                val session = Session(controller, callback)
                sessions[token] = session
                controller.registerCallback(callback, handler)
                session.playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                session.metadata = controller.metadata
            }
        }
    }

    fun read(ownSpeech: Boolean): MusicPlayback {
        if (observing) {
            val playing = sessions.values.firstOrNull { it.playing }
            if (playing != null) {
                fun field(key: String) = playing.metadata?.getText(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }?.take(200)
                return MusicPlayback(true, field(MediaMetadata.METADATA_KEY_TITLE), field(MediaMetadata.METADATA_KEY_ARTIST), metadataAccess)
            }
            return MusicPlayback(!ownSpeech && context.getSystemService(AudioManager::class.java)?.isMusicActive == true, metadataAccess = metadataAccess)
        }
        val manager = context.getSystemService(MediaSessionManager::class.java)
        var access = true
        val sessions = try {
            manager?.getActiveSessions(ComponentName(context, RoadAlertListener::class.java)).orEmpty()
        } catch (_: SecurityException) {
            access = false
            emptyList()
        }
        val playing = sessions.firstOrNull {
            it.packageName != context.packageName && it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        if (playing != null) {
            val metadata = playing.metadata
            fun field(key: String) = metadata?.getText(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }?.take(200)
            return MusicPlayback(true, field(MediaMetadata.METADATA_KEY_TITLE), field(MediaMetadata.METADATA_KEY_ARTIST), access)
        }
        val audio = context.getSystemService(AudioManager::class.java)
        return MusicPlayback(!ownSpeech && audio?.isMusicActive == true, metadataAccess = access)
    }
}
