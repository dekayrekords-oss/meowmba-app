package com.example.meowmba.core.robot

/**
 * [MotorController] that drives a real LOOI-style base by encoding each command
 * with [LooiProtocol] and handing the frame to a [RobotTransport].
 *
 * The controller is stateless beyond its transport: every call is one frame on
 * the wire, so a reconnect or a swapped transport is all that is needed to move
 * the body.
 */
class LooiMotorController(
    private val transport: RobotTransport,
) : MotorController {
    override fun setServo(id: Int, angleDeg: Float) {
        if (transport.connected) transport.send(LooiProtocol.servo(id, angleDeg))
    }

    override fun setExpression(face: String) {
        if (transport.connected) transport.send(LooiProtocol.expression(face))
    }
}
