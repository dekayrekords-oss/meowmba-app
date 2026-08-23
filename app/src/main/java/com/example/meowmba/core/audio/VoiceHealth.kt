package com.example.meowmba.core.audio

/**
 * Which mic to use, and when to abandon a wedged platform recognizer.
 *
 * Measured on the Fold 7: AiAi is accurate, then ERROR_RECOGNIZER_BUSY (8)
 * wedges it, and every listener that shares that one session (always-on,
 * mic button, wake loop) dies together. Sherpa stays up but mishears.
 *
 * Rules this object exists to pin:
 *  1. Prefer the platform recognizer (AiAi) while it is answering.
 *  2. After [strikesToWedge] refusals, or a silent session, switch to Sherpa.
 *  3. After [wedgeMs], try AiAi again — never stay on the weak model forever.
 *  4. Never "give up". A pick is always PLATFORM, SHERPA, or whichever is left.
 *
 * Pure + clock-injected so the matrix is a unit test, not a device session.
 */
class VoiceHealth(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val wedgeMs: Long = DEFAULT_WEDGE_MS,
    private val strikesToWedge: Int = DEFAULT_STRIKES,
) {
    enum class Engine { PLATFORM, SHERPA }

    var platformHealthy: Boolean = true
        private set
    var strikes: Int = 0
        private set
    var unhealthyUntilMs: Long = 0L
        private set

    fun pick(platformAvailable: Boolean, sherpaAvailable: Boolean): Engine? {
        refresh()
        return when {
            platformHealthy && platformAvailable -> Engine.PLATFORM
            sherpaAvailable -> Engine.SHERPA
            platformAvailable -> Engine.PLATFORM
            else -> null
        }
    }

    /** A session became ready or returned speech — AiAi is usable again. */
    fun noteSuccess() {
        platformHealthy = true
        strikes = 0
        unhealthyUntilMs = 0L
    }

    /**
     * BUSY / CLIENT / DISCONNECTED. Returns true the moment we should leave
     * the platform recognizer (caller switches to Sherpa).
     */
    fun noteContention(): Boolean {
        strikes++
        if (strikes >= strikesToWedge) {
            markWedged()
            return true
        }
        return false
    }

    /**
     * startListening accepted and then went silent. That is a wedge, not a
     * maybe — one is enough. Caller must switch engines.
     */
    fun noteSilentSession() {
        markWedged()
    }

    fun refresh() {
        if (!platformHealthy && now() >= unhealthyUntilMs) {
            platformHealthy = true
            strikes = 0
        }
    }

    private fun markWedged() {
        platformHealthy = false
        strikes = strikesToWedge
        unhealthyUntilMs = now() + wedgeMs
    }

    companion object {
        const val DEFAULT_WEDGE_MS = 60_000L
        const val DEFAULT_STRIKES = 4
    }
}
