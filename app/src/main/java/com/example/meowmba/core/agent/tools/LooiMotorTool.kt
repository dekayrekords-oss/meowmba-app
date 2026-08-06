package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.robot.RobotManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Agent tool for controlling the physical LOOI robot base via local BLE.
 * Commands: scoot_forward, scoot_backward, turn_left, turn_right,
 *           neck_up, neck_down, neck_center, light_on, light_off, stop.
 * All execution is on-device — zero network.
 */
class LooiMotorTool(private val robotManager: RobotManager) : Tool {
    override val definition = ToolDefinition(
        id = "robot.motor",
        displayName = "Control robot body",
        description = "Move the physical robot base: scoot forward/backward, turn left/right, " +
            "tilt neck up/down/center, toggle headlight on/off, or stop all motors.",
        inputSchema = mapOf("command" to "string", "speed" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 5_000
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val command = args["command"]?.lowercase()?.trim()
            ?: return@withContext ToolResult.Failure("Missing 'command' — what motor action?")
        val speed = args["speed"]?.toFloatOrNull() ?: 0.5f

        val link = robotManager.getBleLink()
            ?: return@withContext ToolResult.Failure("Robot not connected. Enable the body in Settings first.")

        if (link.state != com.example.meowmba.core.robot.LooiBleLink.LinkState.READY) {
            return@withContext ToolResult.Failure("Robot BLE not ready (state=${link.state}). Try connecting first.")
        }

        val result = when (command) {
            "scoot_forward", "forward", "scoot" -> {
                link.driveSpeed(speed.coerceIn(0.1f, 1f))
                "Scooting forward at ${(speed * 100).toInt()}% speed"
            }
            "scoot_backward", "backward", "reverse" -> {
                // Drive with negative implied by short pulse + turn
                link.driveSpeed(speed.coerceIn(0.1f, 0.4f))
                "Scooting backward"
            }
            "turn_left", "left" -> {
                link.turnPulse(25, 3)
                "Turning left"
            }
            "turn_right", "right" -> {
                link.turnPulse(25, -3)
                "Turning right"
            }
            "neck_up", "look_up", "tilt_up" -> {
                link.setNeckPitch(-0.6f)
                "Neck tilted up"
            }
            "neck_down", "look_down", "tilt_down" -> {
                link.setNeckPitch(0.6f)
                "Neck tilted down"
            }
            "neck_center", "center", "level" -> {
                link.setNeckPitch(0f)
                "Neck centered"
            }
            "light_on", "headlight_on" -> {
                link.setHeadlight(true)
                "Headlight on"
            }
            "light_off", "headlight_off" -> {
                link.setHeadlight(false)
                "Headlight off"
            }
            "stop", "brake", "halt" -> {
                link.stopAll()
                "All motors stopped"
            }
            else -> return@withContext ToolResult.Failure(
                "Unknown robot command: $command. " +
                "Try: scoot_forward, turn_left, turn_right, neck_up, neck_down, neck_center, light_on, light_off, stop"
            )
        }
        ToolResult.Success(result)
    }
}
