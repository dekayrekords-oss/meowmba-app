package com.example.meowmba.core.robot

/** A clip captures this lease before its coroutine can be queued. */
interface BodyPlaybackSource {
    fun beginPlayback(): BodySession?
}

/** A lease cannot outlive its connection, safety state, or higher-priority owner. */
interface BodySession : BodyOutput {
    val isValid: Boolean
    fun submit(verb: BodyVerb): Boolean
    fun finish()
    override fun neck(pitch: Float) { submit(BodyVerb.Neck(pitch)) }
    override fun profiledNeck(pitch: Float) { submit(BodyVerb.Neck(pitch, profiled = true)) }
    override fun turn(speedPercent: Int, ticks: Int, sign: Int) { submit(BodyVerb.Turn(speedPercent, ticks, sign)) }
    override fun drive(speed: Float) { submit(BodyVerb.Drive(speed)) }
    override fun light(on: Boolean) { submit(BodyVerb.Light(on)) }
    override fun brake() { submit(BodyVerb.Brake) }
}

/**
 * The shared BLE output gate: explicit motion > clip > face following > mood.
 * Connection changes and safety interruptions invalidate leases, rather than
 * parking old cues to replay when a touch, pause or disconnection ends.
 *
 * Unknown sensor bytes latch the same stop as a cliff. A new face, mood, clean
 * TOF sample or animation never clears that latch. Only an explicit reset can.
 * This gate adds no approval or motion authority; DESK still forbids driving.
 */
class BodySafety(private val nowMs: () -> Long = System::currentTimeMillis) : BodyOutput, BodyPlaybackSource {
    private val lock = Any()
    private var output: BodyOutput? = null
    private var connected: () -> Boolean = { false }
    private var revision = 0L
    private var owner: Lease? = null
    private var paused = false
    private var stopped: String? = null
    private var touchUntilMs = 0L
    private var followingUntilMs = 0L
    private var followingActive = false
    private var profile = BodyProfile.DESK

    val blockedReason: String? get() = synchronized(lock) { reason() }
    val translationAllowed: Boolean get() = synchronized(lock) { profile == BodyProfile.FLOOR }
    val rotationAllowed: Boolean get() = synchronized(lock) { output?.wheelControlVerified == true }

    /** Replace the destination behind the stable adapter, fencing all old work. */
    fun attach(next: BodyOutput?, isConnected: () -> Boolean = { next != null }) = synchronized(lock) {
        interrupt()
        output = next
        connected = isConnected
        profile = BodyProfile.DESK
        followingUntilMs = 0L
    }

    fun setProfile(value: BodyProfile) = synchronized(lock) {
        // A long clip with no notifications is not verified floor evidence.
        // Keep translation off until the sensor protocol itself is verified.
        val allowed = if (LooiSensorParser.safetyDecodingVerified) value else BodyProfile.DESK
        if (profile != allowed && allowed == BodyProfile.DESK) interrupt()
        profile = allowed
    }

    fun setPaused(value: Boolean) = synchronized(lock) {
        if (paused != value) {
            paused = value
            if (value) interrupt()
        }
    }

    fun stop(reason: String = "Motion disabled; check the base before an explicit resume.") = synchronized(lock) {
        val alreadyStopped = stopped != null
        stopped = reason
        if (!alreadyStopped) interrupt()
    }

    /** Call only for a deliberate user resume, never on a frame or status refresh. */
    fun resume() = synchronized(lock) {
        interrupt()
        stopped = null
        profile = BodyProfile.DESK
    }

    fun touch(ms: Long = RobotManager.TOUCH_PAUSE_MS, atMs: Long = nowMs()) = synchronized(lock) {
        touchUntilMs = maxOf(touchUntilMs, atMs + ms.coerceAtLeast(0L))
        interrupt()
    }

    fun onSensor(event: SensorEvent) {
        when (event) {
            is SensorEvent.Cliff -> stop("Motion disabled: an edge was detected. Check the base before resuming.")
            is SensorEvent.Unknown -> stop("Motion disabled: sensor data is not understood. Check the base before resuming.")
            is SensorEvent.Touch -> touch()
            is SensorEvent.Tof -> Unit
        }
    }

    override fun beginPlayback(): BodySession? = synchronized(lock) { begin(explicit = false) }
    fun beginExplicitMotion(): BodySession? = synchronized(lock) { begin(explicit = true) }

    private fun begin(explicit: Boolean): Lease? {
        if (reason() != null || (owner?.explicit == true && !explicit)) return null
        // A new owner brakes before it replaces either a clip or a follow pulse.
        if (owner != null || followingActive) interrupt()
        return Lease(++revision, explicit).also { owner = it }
    }

    /** Follow is sampled afresh; a suppressed sample is never queued. */
    fun follow(verb: BodyVerb): Boolean = synchronized(lock) {
        if (reason() != null || owner != null) return false
        // Camera samples cannot opt out of their anti-feedback sustain filter.
        if (verb is BodyVerb.Neck && verb.profiled) return false
        if (!write(verb)) return false
        followingActive = verb !is BodyVerb.Brake
        followingUntilMs = if (verb is BodyVerb.Brake) 0L else nowMs() + RobotManager.TURN_PERIOD_MS
        true
    }

    /** Losing a face releases only the follow writer, never a clip/explicit owner. */
    fun stopFollowing() = synchronized(lock) {
        if (followingActive && owner == null) interrupt()
    }

    private fun mood(verb: BodyVerb) = synchronized(lock) {
        if (reason() == null && owner == null && nowMs() >= followingUntilMs) write(verb)
        Unit
    }

    override fun neck(pitch: Float) = mood(BodyVerb.Neck(pitch))
    override fun turn(speedPercent: Int, ticks: Int, sign: Int) = mood(BodyVerb.Turn(speedPercent, ticks, sign))
    override fun drive(speed: Float) = mood(BodyVerb.Drive(speed))
    override fun light(on: Boolean) = mood(BodyVerb.Light(on))
    override fun brake() = synchronized(lock) { interrupt() }

    private fun reason(): String? = when {
        output == null || !connected() -> "No robot body is connected."
        stopped != null -> stopped
        paused -> "Robot motion is paused."
        nowMs() < touchUntilMs -> "Robot is holding still while the screen is in use."
        else -> null
    }

    private fun interrupt() {
        revision++
        owner = null
        followingActive = false
        followingUntilMs = 0L
        output?.perform(BodyVerb.Brake)
    }

    private fun write(verb: BodyVerb): Boolean {
        if (verb is BodyVerb.Turn && output?.wheelControlVerified != true) return false
        if (verb is BodyVerb.Drive && (profile != BodyProfile.FLOOR || !verb.speed.isFinite())) return false
        if (verb is BodyVerb.Neck && !verb.pitch.isFinite()) return false
        // All expressive neck targets stay within the existing follow envelope.
        val bounded = if (verb is BodyVerb.Neck) {
            verb.copy(pitch = verb.pitch.coerceIn(-RobotManager.NECK_RANGE, RobotManager.NECK_RANGE))
        } else verb
        return runCatching {
            when (bounded) {
                is BodyVerb.Neck -> if (bounded.profiled) output?.profiledNeck(bounded.pitch) else output?.neck(bounded.pitch)
                is BodyVerb.Turn -> output?.turn(bounded.speedPercent.coerceIn(0, 100), bounded.ticks.coerceIn(1, 150), bounded.sign)
                is BodyVerb.Drive -> output?.drive(bounded.speed.coerceIn(0f, 1f))
                is BodyVerb.Light -> output?.light(bounded.on)
                is BodyVerb.Brake -> output?.brake()
            }
        }.isSuccess
    }

    private inner class Lease(val token: Long, val explicit: Boolean) : BodySession {
        private var needsBrake = false
        override val lastCommandedNeckPitch: Float? get() = synchronized(lock) {
            if (isValid) output?.lastCommandedNeckPitch else null
        }
        override val isValid: Boolean get() = synchronized(lock) {
            token == revision && owner === this && reason() == null
        }

        override fun submit(verb: BodyVerb): Boolean = synchronized(lock) {
            if (!isValid) return false
            if (!write(verb)) return false
            needsBrake = verb !is BodyVerb.Brake
            true
        }

        override fun finish() = synchronized(lock) {
            // A cancelled old clip must not brake a newer owner's movement.
            if (token == revision && owner === this) {
                if (needsBrake) output?.perform(BodyVerb.Brake)
                owner = null
                revision++
            }
        }
    }
}
