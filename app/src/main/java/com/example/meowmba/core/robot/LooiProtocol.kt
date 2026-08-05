package com.example.meowmba.core.robot

/**
 * Line protocol spoken to a LOOI-style robot base over any byte transport.
 *
 * Kept deliberately tiny and text-based so it is trivially debuggable over a
 * serial console and trivially testable on the JVM:
 *   S <servoId> <angleDeg>   move a servo (angle clamped 0-180, one decimal)
 *   E <expression>           set a named face ("happy", "alert", ...)
 *   PING                     liveness probe; the base answers "PONG"
 *
 * Every frame is a single LF-terminated line. [encode] never throws and always
 * clamps, so a bad command degrades to a safe in-range servo position rather
 * than a malformed frame on the wire.
 */
object LooiProtocol {

    /** Servo command frame. Angle is clamped to the 0-180 servo range. */
    fun servo(id: Int, angleDeg: Float): String =
        "S $id ${"%.1f".format(angleDeg.coerceIn(0f, 180f))}"

    /** Named-expression frame. */
    fun expression(face: String): String = "E ${face.trim()}"

    /** Liveness probe frame. */
    fun ping(): String = "PING"

    /** True when [line] is a well-formed servo frame. */
    fun isServoFrame(line: String): Boolean {
        val parts = line.trim().split(' ')
        if (parts.size != 3 || parts[0] != "S") return false
        val id = parts[1].toIntOrNull() ?: return false
        val angle = parts[2].toFloatOrNull() ?: return false
        return id in 0..15 && angle in 0f..180f
    }

    /** True when [line] is a well-formed expression frame. */
    fun isExpressionFrame(line: String): Boolean =
        line.trim().startsWith("E ") && line.trim().removePrefix("E ").isNotBlank()
}
