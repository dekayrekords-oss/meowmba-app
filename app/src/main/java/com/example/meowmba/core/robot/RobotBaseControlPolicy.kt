package com.example.meowmba.core.robot

/**
 * Fail closed until there is a firmware-specific, physically validated stop
 * protocol. 1.3.54 blocked turns but still sent a rotation script on Brake.
 * A numeric zero in an unverified packet is NOT proof of a safe command.
 * No preference, LLM proposal, resume, or sensor observation overrides this.
 */
object RobotBaseControlPolicy {
    const val CONTROL_AVAILABLE = false
    const val UNAVAILABLE_MESSAGE =
        "Physical robot control is disabled: the stop protocol is not verified. " +
        "Keep the base powered off. On-screen tracking is still available."
    const val STOP_MESSAGE =
        "App control disconnected. No motor command was sent because the stop protocol is not verified. " +
        "Switch the base off if it is moving."
}
