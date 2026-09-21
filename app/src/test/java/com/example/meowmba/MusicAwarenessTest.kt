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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MusicAwarenessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun speakerOutputDoesNotMistakeOwnVoiceForMusic() {
        val audio = context.getSystemService(AudioManager::class.java)
        shadowOf(audio).setIsMusicActive(true)
        val monitor = MusicAwareness(context)
        assertFalse(monitor.read(ownSpeech = true).playing)
        assertTrue(monitor.read(ownSpeech = false).playing)
        assertNull(monitor.read(ownSpeech = false).title)
        shadowOf(audio).setIsMusicActive(false)
        assertFalse(monitor.read(ownSpeech = false).playing)
    }

    @Test fun metadataFollowsPlayingSessionAndClearsOnPause() {
        val controller = mock(MediaController::class.java)
        `when`(controller.packageName).thenReturn("example.music")
        `when`(controller.metadata).thenReturn(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Test song")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Test artist").build())
        `when`(controller.playbackState).thenReturn(PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        shadowOf(context.getSystemService(MediaSessionManager::class.java)).addController(controller)
        val monitor = MusicAwareness(context)
        assertEquals("Test song — Test artist", monitor.read(false).label)
        `when`(controller.playbackState).thenReturn(PlaybackState.Builder()
            .setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        assertFalse(monitor.read(false).playing)
        assertNull(monitor.read(false).title)
    }
}
