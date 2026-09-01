package com.example.meowmba.core.robot

/**
 * Decides whether the base is confined (a desk) or free (a floor), from
 * evidence produced by motion the companion was going to perform anyway.
 *
 * It never commands motion. That is the whole point: the robot does not creep
 * around a desk to measure it, because a probe is exactly the risky moment.
 *
 * ## Why windows and not directions
 *
 * LooiBleProtocol.rotateFrame has no direction parameter, so turn direction is
 * neither commandable nor observable and signed dead reckoning is impossible.
 * Evidence is therefore counted in *motion windows* — contiguous stretches of
 * commanded motion — rather than per-direction sectors.
 *
 * This is strictly weaker: it can conclude "not confined" while a cliff sits
 * in an untested direction. That is why [cleanWindowsForFloor] requires
 * several separate windows, why a window must last [minWindowMs] to count, and
 * why any cliff is absolute and immediate. Sector logic becomes possible once
 * turn direction is decoded.
 */
class ConfinementModel(
    private val cleanWindowsForFloor: Int = 4,
    private val minWindowMs: Long = 1_500L,
    private val evidenceDecayMs: Long = 10 * 60_000L,
) {

    var profile: BodyProfile = BodyProfile.DESK
        private set

    /** Clean motion windows currently counting toward FLOOR. */
    var cleanWindows: Int = 0
        private set

    private var windowStartedAt: Long? = null
    private var windowPoisoned = false
    private var lastEvidenceAt: Long = 0L

    /** A stretch of commanded motion began. A second call without an end is ignored. */
    fun onMotionWindowStart(atMs: Long) {
        if (windowStartedAt != null) return
        windowStartedAt = atMs
        windowPoisoned = false
    }

    /** The stretch ended. Long enough and uneventful enough, it is evidence. */
    fun onMotionWindowEnd(atMs: Long): BodyProfile {
        val started = windowStartedAt ?: return profile
        windowStartedAt = null

        decayIfStale(atMs)

        val longEnough = atMs - started >= minWindowMs
        if (longEnough && !windowPoisoned) {
            cleanWindows += 1
            lastEvidenceAt = atMs
            if (cleanWindows >= cleanWindowsForFloor) profile = BodyProfile.FLOOR
        }
        windowPoisoned = false
        return profile
    }

    fun onSensor(event: SensorEvent, atMs: Long): BodyProfile {
        when (event) {
            // An edge, or a frame nobody can read. Both mean stop trusting the room.
            is SensorEvent.Cliff, is SensorEvent.Unknown -> collapse()
            // Being touched says nothing about the surface.
            is SensorEvent.Touch -> Unit
            // TOF is captured for later use; it is not evidence on its own yet.
            is SensorEvent.Tof -> Unit
        }
        return profile
    }

    /** The IMU says the base was lifted: whatever it learned is about a different place. */
    fun onPickedUp(atMs: Long) = collapse()

    fun onLinkLost(atMs: Long) = collapse()

    private fun collapse() {
        profile = BodyProfile.DESK
        cleanWindows = 0
        lastEvidenceAt = 0L
        windowPoisoned = true
    }

    /** Evidence is about a room the robot may have left. Let it expire. */
    private fun decayIfStale(atMs: Long) {
        if (cleanWindows > 0 && atMs - lastEvidenceAt > evidenceDecayMs) {
            cleanWindows = 0
            profile = BodyProfile.DESK
        }
    }
}