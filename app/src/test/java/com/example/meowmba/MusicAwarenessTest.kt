package com.example.meowmba

import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.media.MusicAwareness
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Owner rule (2026-09-24): the live check only answers "is music playing?" (no titles/artists,
 * no metadata reads); the song and artist are read once, only when the user asks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MusicAwarenessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun liveCheckFollowsPlaybackWithoutReadingMetadataAndReleasesCallbacks() {
        val controller = mock(MediaController::class.java)
        val session = android.media.session.MediaSession(context, "music-live-test")
        `when`(controller.sessionToken).thenReturn(session.sessionToken)
        `when`(controller.packageName).thenReturn("example.music")
        `when`(controller.playbackState).thenReturn(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        shadowOf(context.getSystemService(MediaSessionManager::class.java)).addController(controller)
        val monitor = MusicAwareness(context)
        monitor.start()
        repeat(5) { assertTrue(monitor.isPlaying(ownSpeech = false)) }
        verify(controller, never()).metadata
        val callback = org.mockito.ArgumentCaptor.forClass(MediaController.Callback::class.java)
        verify(controller).registerCallback(callback.capture(), org.mockito.ArgumentMatchers.any(android.os.Handler::class.java))
        callback.value.onPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        assertFalse(monitor.isPlaying(ownSpeech = false))
        monitor.stop()
        verify(controller).unregisterCallback(callback.value)
        session.release()
    }

    @Test fun speakerOutputDoesNotMistakeOwnVoiceForMusic() {
        val audio = context.getSystemService(AudioManager::class.java)
        shadowOf(audio).setIsMusicActive(true)
        val monitor = MusicAwareness(context)
        assertFalse(monitor.isPlaying(ownSpeech = true))
        assertTrue(monitor.isPlaying(ownSpeech = false))
        shadowOf(audio).setIsMusicActive(false)
        assertFalse(monitor.isPlaying(ownSpeech = false))
    }

    @Test fun songAndArtistOnlyWhenAsked() {
        val controller = mock(MediaController::class.java)
        `when`(controller.packageName).thenReturn("example.music")
        `when`(controller.metadata).thenReturn(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Test song")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Test artist").build())
        `when`(controller.playbackState).thenReturn(PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        shadowOf(context.getSystemService(MediaSessionManager::class.java)).addController(controller)
        val monitor = MusicAwareness(context)
        assertEquals("Test song — Test artist", monitor.nowPlaying().label)
        `when`(controller.playbackState).thenReturn(PlaybackState.Builder()
            .setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        assertFalse(monitor.nowPlaying().playing)
        assertNull(monitor.nowPlaying().title)
    }
}
