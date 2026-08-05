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
    private val log: (String) -> Unit = { println(it) }
) : MotorController {
    val history = mutableListOf<String>()

    override fun setServo(id: Int, angleDeg: Float) {
        val clamped = angleDeg.coerceIn(0f, 180f)
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
