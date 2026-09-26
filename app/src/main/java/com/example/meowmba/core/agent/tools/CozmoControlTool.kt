package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.robot.cozmo.CozmoController

class CozmoControlTool(private val controller: CozmoController) : Tool {
    override val definition = ToolDefinition(
        id = "cozmo.control", displayName = "Cozmo robot",
        description = "Control only Cozmo, never LOOI. The request must name Cozmo; connect him in the Cozmo controls first. Actions: status, stop, forward, back, left, right, " +
            "look_up, look_down, look_center, lift_up, lift_middle, lift_down, arc_left, arc_right, nod, greet, celebrate, peek, sleep, wake, neutral, happy, curious, surprised, sleepy, sad, " +
            "light_green, light_blue, light_red, light_yellow, light_purple, light_white, light_off, headlight_on, headlight_off. " +
            "Movement is bounded; wheels require the user's floor-move switch. Queued means sent for processing, not confirmed movement. " +
            "Does not connect, enable wheels, or turn on the camera.",
        inputSchema = mapOf("action" to "One of the listed Cozmo actions"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
    )
    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"]?.trim()?.lowercase() ?: return ToolResult.Failure("Missing Cozmo action")
        if (!controller.state.value.connected) return ToolResult.Failure("Cozmo is not connected. Open the Cozmo controls. LOOI is a separate robot.")
        val result = controller.action(action)
        return if (action == "status" || result.startsWith("Command queued") || result.startsWith("Stop requested"))
            ToolResult.Success(result) else ToolResult.Failure(result)
    }
}
