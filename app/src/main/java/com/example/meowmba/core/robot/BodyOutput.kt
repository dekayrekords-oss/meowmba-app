package com.example.meowmba.core.robot

/**
 * Everything the LOOI base can be told to do, in the base's own vocabulary.
 *
 * This deliberately does not reuse [MotorController], whose `setServo(id,
 * angle)` describes an articulated puppet the hardware is not. That interface
 * still serves the TCP/serial transport and is left alone.
 */
interface BodyOutput {
    /** Device-specific protocol and stop bounds must be verified, not inferred from connection. */
    val wheelControlVerified: Boolean get() = false
    /** -1 fully up, 0 center, +1 down. */
    fun neck(pitch: Float)

    /** Last submitted pitch, not measured joint position; null after brake/connect. */
    val lastCommandedNeckPitch: Float? get() = null

    /** Authored samples already eased by a live BodySession; same position channel. */
    fun profiledNeck(pitch: Float) = neck(pitch)

    /** In-place rotation. [sign] is recorded but not yet honourable — see BodyVerb.Turn. */
    fun turn(speedPercent: Int, ticks: Int, sign: Int)

    /** Forward only, 0..1. There is no reverse channel. */
    fun drive(speed: Float)

    fun light(on: Boolean)

    fun brake()
}

/**
 * Dispatch a verb, swallowing any failure.
 *
 * The body must never block the face: a dropped BLE link mid-clip should leave
 * the cat animating happily on screen, not crash the animation coroutine.
 */
fun BodyOutput.perform(verb: BodyVerb) {
    runCatching {
        when (verb) {
            is BodyVerb.Neck -> if (verb.profiled) profiledNeck(verb.pitch) else neck(verb.pitch)
            is BodyVerb.Turn -> turn(verb.speedPercent, verb.ticks, verb.sign)
            is BodyVerb.Drive -> drive(verb.speed)
            is BodyVerb.Light -> light(verb.on)
            is BodyVerb.Brake -> brake()
        }
    }
}

/** Logs instead of moving hardware — the honest stand-in, as SimulatedMotorController is. */
class SimulatedBodyOutput(
    private val log: (String) -> Unit = { println(it) },
) : BodyOutput {

    private val entries = mutableListOf<String>()
    val history: List<String> get() = entries

    private fun record(entry: String) {
        entries.add(entry)
        if (entries.size > 200) entries.removeAt(0)
        log("body: $entry")
    }

    override fun neck(pitch: Float) = record("neck -> ${pitch.coerceIn(-1f, 1f)}")

    override fun turn(speedPercent: Int, ticks: Int, sign: Int) =
        record("turn -> ${speedPercent.coerceIn(0, 100)}% x${ticks}t sign=$sign")

    override fun drive(speed: Float) = record("drive -> ${speed.coerceIn(0f, 1f)}")

    override fun light(on: Boolean) = record("light -> $on")

    override fun brake() = record("brake")
}

/**
 * Drives a real LOOI base over BLE.
 *
 * The constructor takes lambdas so the coalescing logic is pure-JVM testable
 * (LooiBleLink is final and Android-bound). [connected] is polled per call,
 * [writePitch] is the raw `setNeckPitch` (clamping happens here).
 *
 * [turn]'s `sign` is ignored: LooiBleProtocol.rotateFrame carries no direction
 * parameter, so the base rotates whichever way the verified packet rotates.
 * The argument is kept so the mapper can record intent, and so honouring it is
 * a one-line change here once direction is decoded.
 */
class LooiBleBodyOutput(
    private val link: LooiBleLink?,
    private val connected: () -> Boolean,
    private val writePitch: (Float) -> Unit,
    /** Injectable clock: the JVM tests drive the damping by hand. */
    private val nowMs: () -> Long = System::currentTimeMillis,
) : BodyOutput {

    constructor(link: LooiBleLink) : this(link, { link.connected }, {
        check(link.setNeckPitch(it)) { "Robot rejected neck command (disconnected or Bluetooth busy)." }
    })

    /**
     * Face tracking calls neck() every camera frame. Coalescing keeps a steady
     * face from hammering the BLE link at 20-30Hz: only movements of at least
     * [NECK_DEADBAND] really move the servo (measured on device: without this
     * the base is servoed non-stop, an IRQ/latency drain that starved voice).
     */
    private var lastNeckPitch = Float.NaN
    private var neckTarget = Float.NaN
    private var neckTargetSinceMs = 0L
    private var lastNeckWriteAtMs = 0L

    override val lastCommandedNeckPitch: Float?
        get() = lastNeckPitch.takeIf { connected() && it.isFinite() }

    /**
     * An exclusive authored trajectory must not re-enter the camera sustain
     * filter: each intermediate point intentionally changes. Keep the same
     * 250 ms wire budget and travel cap, with no timer or deferred packet here.
     * Only the owning session submits subsequent samples; brake cannot leave
     * an autonomous ramp running. This is open-loop target interpolation.
     */
    override fun profiledNeck(pitch: Float) {
        if (!connected() || !pitch.isFinite()) return
        val now = nowMs()
        if (lastNeckPitch.isFinite() &&
            (now < lastNeckWriteAtMs || now - lastNeckWriteAtMs < NECK_SPACING_MS)) return
        val bounded = pitch.coerceIn(-RobotManager.NECK_RANGE, RobotManager.NECK_RANGE)
        if (bounded == lastNeckPitch) return
        writePitch(bounded)
        lastNeckWriteAtMs = now
        lastNeckPitch = bounded
        neckTarget = bounded
        neckTargetSinceMs = now
    }

    override fun neck(pitch: Float) {
        if (!connected()) return
        val clamped = pitch.coerceIn(-1f, 1f)
        val now = nowMs()
        // A deadband alone does not break the nod loop: tilting the phone moves
        // the camera, which moves the measured offset, which tilts it back. So
        // a pose has to hold long enough to be real ([NECK_SUSTAIN_MS], which
        // kills single-frame jitter) and writes have to leave the servo time to
        // settle ([NECK_SPACING_MS]). Measured on device: without both, the base
        // nodded continuously and the screen was hard to touch.
        if (!clamped.isFinite()) return
        if (neckTarget.isNaN() || kotlin.math.abs(clamped - neckTarget) > NECK_TARGET_TOLERANCE) {
            neckTarget = clamped
            neckTargetSinceMs = now
        }
        // The first pose after connect or a reset has no history to distrust,
        // so it applies immediately; every later change has to hold still.
        val firstPose = lastNeckPitch.isNaN()
        if (!firstPose && now - neckTargetSinceMs < NECK_SUSTAIN_MS) return
        if (!firstPose && now - lastNeckWriteAtMs < NECK_SPACING_MS) return
        if (!lastNeckPitch.isNaN() &&
            kotlin.math.abs(clamped - lastNeckPitch) < NECK_DEADBAND
        ) return
        writePitch(clamped)
        lastNeckWriteAtMs = now
        lastNeckPitch = clamped
    }

    override fun turn(speedPercent: Int, ticks: Int, sign: Int) {
        if (!connected()) return
        // [sign] is the direction the face (or the animation) asked for: -1
        // left, +1 right. Handing the bare magnitude to turnPulse sent every
        // clip-driven turn the same way — the same "left does nothing" bug the
        // follow loop already avoids by passing a signed speed.
        val mag = kotlin.math.abs(speedPercent)
        check(link?.turnPulse(if (sign < 0) -mag else mag, ticks) == true) { "Wheel control is not verified." }
    }

    override fun drive(speed: Float) {
        check(connected() && link?.driveSpeed(speed) == true) { "Robot rejected drive command." }
    }

    override fun light(on: Boolean) {
        check(connected() && link?.setHeadlight(on) == true) { "Robot rejected light command." }
    }

    override fun brake() {
        if (connected()) {
            link?.stopAll()
        }
        lastNeckPitch = Float.NaN
        neckTarget = Float.NaN
    }

    internal companion object {
        const val NECK_DEADBAND = 0.04f
        /** Camera samples may settle within a small band without being bit-identical. */
        const val NECK_TARGET_TOLERANCE = 0.015f

        /** A neck pose must hold this long before it is written. */
        const val NECK_SUSTAIN_MS = 120L

        /** Minimum settle time between two neck writes (an authored nod survives). */
        const val NECK_SPACING_MS = 250L
    }
}
