package com.example.meowmba.core.agent.tools

import com.example.meowmba.ui.companion.CustomAnimationSpec

/**
 * `avatar.describe_animation` — plays a custom animation from a natural
 * language description. Bridges the gap between what the user says ("do a big
 * stretch") and the pre-baked keyframe patterns in [AnimationDescriber].
 *
 * Falls back to [AvatarAnimateTool] when the description is too novel for the
 * pattern engine — but for common moods, gestures, and cat behaviors this
 * provides instant playback with no model call.
 */
class DescribeAnimationTool(
    private val onPlay: (CustomAnimationSpec) -> Unit,
) : Tool {
    override val definition = ToolDefinition(
        id = "avatar.describe_animation",
        displayName = "Describe an animation",
        description = "Generate and play a custom animation from a natural language description. " +
            "Use this when the user asks for an animation that isn't a built-in clip, or " +
            "when they describe what they want the cat to do in plain English. " +
            "Examples: 'do a big stretch', 'look scared', 'purr happily', 'sneeze'. " +
            "You can combine words: 'excited dance', 'sleepy yawn'. " +
            "Returns the name of the played animation.",
        inputSchema = mapOf(
            "description" to "string (natural language description of the desired animation)",
        ),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val description = args["description"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'description' — what animation should I do?")

        val spec = com.example.meowmba.ui.companion.AvatarPerformanceCatalog.resolve(description)?.spec
            ?: AnimationDescriber.describe(description)
            ?: return ToolResult.Failure(
                "I don't know how to animate that. Try words like: " +
                "stretch, jump, bounce, happy, sad, angry, scared, excited, " +
                "curious, sleepy, yawn, purr, sneeze, paw, dance, wave, laugh."
            )

        onPlay(spec)
        return ToolResult.Success("Playing \"${spec.name}\" (${spec.durationMs}ms).")
    }
}
