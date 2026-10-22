package com.example.meowmba.core.robot

/** What a fed9 notification turned out to mean. */
sealed class SensorEvent {
    object Touch : SensorEvent()

    /** The base is at an edge. Absolute and immediate: brake, then think. */
    data class Cliff(val raw: ByteArray) : SensorEvent() {
        override fun equals(other: Any?): Boolean =
            other is Cliff && other.raw.contentEquals(raw)
        override fun hashCode(): Int = raw.contentHashCode()
    }

    data class Tof(val mm: Int) : SensorEvent()

    /** Not decoded. Treated as danger everywhere it is consumed. */
    data class Unknown(val raw: ByteArray) : SensorEvent() {
        override fun equals(other: Any?): Boolean =
            other is Unknown && other.raw.contentEquals(raw)
        override fun hashCode(): Int = raw.contentHashCode()
    }
}

/**
 * Decoder for the fed9 sensor characteristic.
 *
 * **The layout is not known.** LooiBleProtocol records it as "notify:
 * touch/cliff; TOF/status partial", and no frames have been captured from this
 * robot. Every frame therefore returns [SensorEvent.Unknown], and every
 * consumer treats Unknown as danger — ConfinementModel collapses to DESK on
 * it.
 *
 * This is deliberate. A speculative bit-mask here would produce a parser that
 * looks finished and silently misreports cliffs, which is the one failure this
 * subsystem must not have. [CaptureLog] exists to make the real decode
 * possible: run the robot, perform motion, dump the log, read the bytes.
 *
 * When the layout is decoded, only [parse] changes.
 */
object LooiSensorParser {

    /** No captured/verified cliff layout exists yet; silence is not safe-floor evidence. */
    const val safetyDecodingVerified: Boolean = false

    fun parse(raw: ByteArray): SensorEvent = SensorEvent.Unknown(raw.copyOf())

    /**
     * A bounded ring of recent frames, each tagged with the motion in flight
     * when it arrived — the correlation that makes the layout decodable.
     */
    class CaptureLog(private val capacity: Int = 200) {
        private val ring = ArrayDeque<String>()

        val entries: List<String> get() = ring.toList()

        fun record(raw: ByteArray, motionInFlight: String?, atMs: Long) {
            val hex = raw.joinToString("") { "%02x".format(it) }
            ring.addLast("t=$atMs ${motionInFlight ?: "idle"} $hex")
            while (ring.size > capacity) ring.removeFirst()
        }

        fun dump(): String = ring.joinToString("\n")
    }
}
