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
    fun read(ownSpeech: Boolean): MusicPlayback {
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
