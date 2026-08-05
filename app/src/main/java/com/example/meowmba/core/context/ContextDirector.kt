package com.example.meowmba.core.context

import com.example.meowmba.core.screen.ScreenWatchCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fuses what meow can see into short "context events" and decides when to
 * react (animation) versus when to actually say something (commentary).
 *
 * Throttles: at most one vision call per [VISION_INTERVAL_MS]; at most one
 * unsolicited comment per [COMMENT_INTERVAL_MS]; never talks over the user.
 */
data class ContextEvent(
    /** One of: game, document, error, browser, media, general. */
    val kind: String,
    val summary: String,
)

class ContextDirector(
    private val scope: CoroutineScope,
    /** Turns a JPEG into a one-line description. Returns null when busy/down. */
    private val describe: suspend (ByteArray) -> String?,
    /** Fired for every understood frame; the VM maps kind -> animation. */
    private val onEvent: (ContextEvent) -> Unit,
    /** Fired when meow should speak a short comment (already rate-limited). */
    private val onComment: (String) -> Unit,
    /** False = react silently; True = occasional helpful comments. */
    var commentaryEnabled: Boolean = false,
    /** False while the user is speaking/listening — never comment then. */
    private val canSpeak: () -> Boolean = { true },
) {
    private var job: Job? = null
    private var lastVisionAt = 0L
    private var lastCommentAt = 0L
    private var lastFrameSeenAt = 0L

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            ScreenWatchCoordinator.latest.collect { frame ->
                if (!isActive || frame == null) return@collect
                if (frame.capturedAt <= lastFrameSeenAt) return@collect
                lastFrameSeenAt = frame.capturedAt
                val now = System.currentTimeMillis()
                if (now - lastVisionAt < VISION_INTERVAL_MS) return@collect
                lastVisionAt = now

                val jpeg = runCatching {
                    val stream = java.io.ByteArrayOutputStream()
                    frame.bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream)
                    stream.toByteArray()
                }.getOrNull() ?: return@collect

                val summary = describe(jpeg) ?: return@collect
                val kind = classify(summary)
                onEvent(ContextEvent(kind, summary))

                if (commentaryEnabled && canSpeak() &&
                    now - lastCommentAt > COMMENT_INTERVAL_MS && kind != "general"
                ) {
                    lastCommentAt = now
                    onComment(shortComment(kind, summary))
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val VISION_INTERVAL_MS = 30_000L
        const val COMMENT_INTERVAL_MS = 5 * 60_000L

        /** Pure keyword classification — unit-testable. */
        fun classify(summary: String): String {
            val s = summary.lowercase()
            return when {
                listOf("game", "health bar", "score", "gameplay", "player", "level ").any { it in s } -> "game"
                listOf("error", "crash", "exception", "failed", "not responding").any { it in s } -> "error"
                listOf("spreadsheet", "document", "word ", "email", "gmail", "writing", "slides").any { it in s } -> "document"
                listOf("browser", "webpage", "website", "search results", "url").any { it in s } -> "browser"
                listOf("video", "playing", "youtube", "movie").any { it in s } -> "media"
                else -> "general"
            }
        }

        fun shortComment(kind: String, summary: String): String {
            val gist = summary.take(120)
            return when (kind) {
                "game" -> "Looks like you're mid-game — $gist Need any tips?"
                "error" -> "Hmm, that looks like an error on screen. Want me to help figure it out?"
                "document" -> "Working on something? I see: $gist"
                "browser" -> "Browsing, I see. Say the word if you want me to look anything up."
                "media" -> "Enjoying something! I'll keep quiet unless you need me."
                else -> gist
            }
        }
    }
}
