package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.robot.RobotManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `robot.move` — drive the physical LOOI base.
 *
 * The companion has a body; asking it to spin, look up, or turn its headlight
 * off should move the body, not produce a sentence about moving the body. This
 * is the only route from language to the motors.
 *
 * MODERATE, no approval card, for the same reason as opening an app: the user
 * said it out loud, the result is immediately visible, and every motion is
 * short and self-terminating. A confirmation tap between "spin around" and the
 * robot spinning would make the body feel broken rather than safe. `stop` is
 * deliberately part of the same vocabulary so the undo is also one phrase.
 *
 * Honest when there is no body: with the base absent or disconnected this
 * reports that plainly instead of claiming a movement nobody saw.
 */
class RobotMoveTool(private val robotManager: RobotManager) : Tool {

    override val definition = ToolDefinition(
        id = "robot.move",
        displayName = "Move LOOI",
        description = "Control only the LOOI base, never Cozmo. " +
            "action is one of: forward, back, left, right, spin, stop, " +
            "look_up, look_down, look_center, nod, shake_head, wiggle, " +
            "headlight_on, headlight_off. " +
            "Use for requests to LOOI to move, turn, spin, drive, look somewhere, " +
            "nod, shake its head, dance, or switch the headlight on or off. " +
            "This moves real hardware — do not use it to describe a movement.",
        inputSchema = mapOf(
            "action" to "one of the listed motions",
            "intensity" to "optional 0.2-1.0, how vigorous (default 1.0)",
        ),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        // Long enough for the slowest scripted motion (a full spin) to finish.
        timeoutMs = 15_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult =
        withContext(Dispatchers.IO) {
            val raw = args["action"]?.trim().orEmpty()
            if (raw.isBlank()) {
                return@withContext ToolResult.Failure(
                    "Missing 'action' — what should the robot do? " +
                        "Try spin, forward, look_up, nod, or headlight_off."
                )
            }
            // Accept the enum name OR the spoken phrase, so both the model
            // (which sends "shake_head") and the keyword fast path (which may
            // pass through "shake your head") resolve to the same motion.
            val motion = runCatching {
                RobotManager.Motion.valueOf(raw.uppercase().replace(' ', '_').replace('-', '_'))
            }.getOrNull() ?: RobotManager.Motion.fromPhrase(raw)
            ?: return@withContext ToolResult.Failure(
                "I don't have a movement called \"$raw\". I can do: " +
                    RobotManager.Motion.entries.joinToString(", ") { it.name.lowercase() }
            )

            val intensity = args["intensity"]?.toFloatOrNull() ?: 1f
            val result = robotManager.perform(motion, intensity)
                ?: return@withContext ToolResult.Failure(
                    "LOOI is not connected, so I can't ${motion.name.lowercase().replace('_', ' ')}. " +
                        "Open the LOOI controls for its availability. Cozmo is a separate robot."
                )
            ToolResult.Success(result)
        }
}
