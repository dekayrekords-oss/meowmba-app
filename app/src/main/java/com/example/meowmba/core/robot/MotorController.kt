package com.example.meowmba.core.robot

/**
 * Phase G2: motor control API for a future physical body.
 * The app ships with [SimulatedMotorController] (logging only); real
 * hardware plugs in later via USB/serial or the desktop bridge by
 * implementing the same two methods.
 */
interface MotorController {
    /** Move servo [id] to [angleDeg] (0-180, center 90). */
    fun setServo(id: Int, angleDeg: Float)

    /** Named facial expression, e.g. "happy", "alert", "sleepy". */
    fun setExpression(face: String)
}

/** Logs commands instead of moving hardware — honest stand-in. */
class SimulatedMotorController(
    /**
     * Silent by default. This was `println`, and the IMU drives setServo at
     * ~50 Hz on the main thread: measured on device it emitted 55 lines a
     * second of `motor: servo0 -> 89.2` — the same unchanged value — which is
     * synchronized blocking I/O on the UI thread, and it buried every other
     * log line in logcat. Tests pass their own collector; nothing else needs
     * this narrated.
     */
    private val log: (String) -> Unit = {}
) : MotorController {
    val history = mutableListOf<String>()

    /** Last angle per servo, so unchanged commands cost nothing. */
    private val lastAngle = mutableMapOf<Int, Float>()

    override fun setServo(id: Int, angleDeg: Float) {
        val clamped = angleDeg.coerceIn(0f, 180f)
        // A repeated identical command is not a motion. Dropping it keeps the
        // history readable and, on a real body, saves a serial write per frame.
        val previous = lastAngle[id]
        if (previous != null && kotlin.math.abs(previous - clamped) < 0.05f) return
        lastAngle[id] = clamped
        val entry = "servo$id -> ${"%.1f".format(clamped)}"
        history.add(entry)
        if (history.size > 200) history.removeAt(0)
        log("motor: $entry")
    }

    override fun setExpression(face: String) {
        val entry = "expression -> $face"
        history.add(entry)
        if (history.size > 200) history.removeAt(0)
        log("motor: $entry")
    }
}
