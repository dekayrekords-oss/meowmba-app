package com.example.meowmba.core.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.example.meowmba.core.alerts.RoadAlertListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class MediaKind { MUSIC, VIDEO, AUDIOBOOK }

/** One app's current media, as the Desk rooms show it. */
data class NowPlaying(val kind: MediaKind, val title: String, val subtitle: String?, val playing: Boolean, val packageName: String)

data class RoomNowPlaying(
    val music: NowPlaying? = null,
    val video: NowPlaying? = null,
    val audiobook: NowPlaying? = null,
    /** False when Android's notification access is off (media titles need it). */
    val access: Boolean = true,
)

/**
 * What's playing, for Meow's rooms: the stereo (music), the big TV (YouTube) and the bookshelf
 * (audiobooks). Owner request 2026-09-25: the rooms show the current song, video and audiobook.
 *
 * Runs only while the Desk is on screen ([start] on resume, [stop] on pause). It follows media
 * sessions by callbacks; nothing is polled, no microphone, no notification text. This is separate
 * from [MusicAwareness], whose mic-time rule (no titles unless asked) is unchanged.
 */
class RoomMediaWatcher(private val context: Context) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, RoadAlertListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val controllers = linkedMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val _now = MutableStateFlow(RoomNowPlaying())
    val now: StateFlow<RoomNowPlaying> = _now
    private var started = false
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { attach(it.orEmpty()) }

    fun start() {
        if (started) return
        started = true
        try {
            manager?.addOnActiveSessionsChangedListener(listener, component, handler)
            attach(manager?.getActiveSessions(component).orEmpty())
        } catch (_: SecurityException) {
            _now.value = RoomNowPlaying(access = false)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { manager?.removeOnActiveSessionsChangedListener(listener) }
        attach(emptyList())
    }

    private fun attach(list: List<MediaController>) {
        val incoming = list.filter { it.packageName != context.packageName }.take(8)
        val tokens = incoming.map { it.sessionToken }.toSet()
        controllers.keys.filter { it !in tokens }.forEach { t -> controllers.remove(t)?.let { (c, cb) -> c.unregisterCallback(cb) } }
        incoming.forEach { c ->
            if (c.sessionToken in controllers) return@forEach
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                override fun onSessionDestroyed() {
                    controllers.remove(c.sessionToken)?.let { (cc, callback) -> cc.unregisterCallback(callback) }
                    publish()
                }
            }
            controllers[c.sessionToken] = c to cb
            c.registerCallback(cb, handler)
        }
        publish()
    }

    private fun publish() {
        if (!started) { _now.value = RoomNowPlaying(); return }
        val items = controllers.values.mapNotNull { (c, _) -> describe(c) }
        fun pick(kind: MediaKind) = items.filter { it.kind == kind }.let { list -> list.firstOrNull { it.playing } ?: list.firstOrNull() }
        _now.value = RoomNowPlaying(pick(MediaKind.MUSIC), pick(MediaKind.VIDEO), pick(MediaKind.AUDIOBOOK), access = true)
    }

    private fun describe(c: MediaController): NowPlaying? {
        val md = c.metadata ?: return null
        fun f(key: String) = md.getText(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }?.take(120)
        return nowPlaying(c.packageName, f(MediaMetadata.METADATA_KEY_TITLE), f(MediaMetadata.METADATA_KEY_ARTIST),
            f(MediaMetadata.METADATA_KEY_ALBUM), c.playbackState?.state == PlaybackState.STATE_PLAYING)
    }

    private fun controllerFor(kind: MediaKind): MediaController? {
        val wanted = when (kind) { MediaKind.MUSIC -> _now.value.music; MediaKind.VIDEO -> _now.value.video; MediaKind.AUDIOBOOK -> _now.value.audiobook }
        return controllers.values.map { it.first }.firstOrNull { it.packageName == wanted?.packageName }
    }

    /** Play/pause the stereo, TV or audiobook: the user's tap, forwarded to that app's own session. */
    fun togglePlay(kind: MediaKind) {
        val c = controllerFor(kind) ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next(kind: MediaKind) { controllerFor(kind)?.transportControls?.skipToNext() }

    companion object {
        val VIDEO_APPS = setOf("com.google.android.youtube", "com.google.android.apps.youtube.kids", "com.google.android.youtube.tv",
            "app.revanced.android.youtube", "app.rvx.android.youtube", "com.vanced.android.youtube", "org.schabi.newpipe")
        val AUDIOBOOK_APPS = listOf("com.audible.application", "com.overdrive.mobile.android.libby", "com.google.android.apps.books",
            "com.storytel", "com.hoopladigital.android", "com.bookbeat", "com.scribd.app.reader0", "com.kobobooks.android", "fm.libro.android")

        fun kindOf(packageName: String): MediaKind = when (packageName) {
            in VIDEO_APPS -> MediaKind.VIDEO
            in AUDIOBOOK_APPS -> MediaKind.AUDIOBOOK
            else -> MediaKind.MUSIC
        }

        /** Audiobook apps put the book in "album" and the chapter in "title"; show the book. */
        fun nowPlaying(pkg: String, title: String?, artist: String?, album: String?, playing: Boolean): NowPlaying? {
            val kind = kindOf(pkg)
            val shown = (if (kind == MediaKind.AUDIOBOOK) album ?: title else title) ?: return null
            return NowPlaying(kind, shown, artist, playing, pkg)
        }
    }
}
