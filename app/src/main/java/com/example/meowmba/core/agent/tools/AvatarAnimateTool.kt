package com.example.meowmba.core.agent.tools

import com.example.meowmba.ui.companion.CustomAnimationSpec
import com.example.meowmba.ui.companion.CustomAnimationStore
import com.example.meowmba.ui.companion.CustomAnimations
import org.json.JSONArray
import org.json.JSONObject

/**
 * `avatar.animate` — the companion invents and performs its own face animations.
 *
 * The model writes keyframes (sparse pose overrides); [CustomAnimations.parse]
 * validates, clamps, and reports every adjustment; the renderer plays the clip
 * over the state-machine pose and the face returns to normal when it ends.
 *
 * SAFE risk, no confirmation: the worst a bad clip does is look odd for a few
 * seconds — the bounds in [CustomAnimations] keep it inside the face.
 */
class AvatarAnimateTool(
    private val store: CustomAnimationStore?,
    private val onPlay: (CustomAnimationSpec) -> Unit,
) : Tool {
    override val definition = ToolDefinition(
        id = "avatar.animate",
        displayName = "Animate the avatar",
        description = "Invents and plays a custom face animation. 'frames' is a JSON array " +
            "of keyframes; each has \"at\" (milliseconds) plus any of: eye_open (0-1.6), " +
            "pupil_size (0.05-0.9), gaze_up (0-1), head_tilt (-25..25 degrees), " +
            "ear_perk (0.3-1.6), ear_droop (0-40 degrees), happy_eyes (boolean), " +
            "whisker_flutter (boolean), mouth (IDLE|HAPPY|SPEAKING), glow (#RRGGBB). " +
            "Fields left out keep their normal value; numbers interpolate between frames. " +
            "Example: [{\"at\":0,\"ear_perk\":1.5},{\"at\":300,\"ear_perk\":0.6}," +
            "{\"at\":600,\"ear_perk\":1.0}] wiggles the ears for 0.6s. " +
            "Set save=true to keep the animation by name for later. " +
            "To REPLAY, give just the name with no frames — built-ins (" +
            CustomAnimations.BUILT_INS.keys.joinToString(", ") + ") and saved clips play by name.",
        inputSchema = mapOf(
            "name" to "string",
            "frames" to "string (JSON array of keyframes — omit to replay a built-in or saved clip)",
            "save" to "string (\"true\" to persist, optional)",
        ),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val name = args["name"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'name'")
        val framesJson = args["frames"]?.takeIf { it.isNotBlank() }

        // Replay: no frames means "play the clip with this name" — built-ins
        // first, then whatever the companion saved earlier.
        if (framesJson == null) {
            val clip = CustomAnimations.builtIn(name) ?: store?.load(name)
                ?: return ToolResult.Failure(
                    "No animation called \"$name\". Built-ins: " +
                        CustomAnimations.BUILT_INS.keys.joinToString(", ") +
                        " — or invent one with frames."
                )
            onPlay(clip)
            return ToolResult.Success("Playing \"${clip.name}\" (${clip.durationMs}ms).")
        }

        val document = try {
            JSONObject().put("name", name).put("frames", JSONArray(framesJson)).toString()
        } catch (e: Exception) {
            return ToolResult.Failure("'frames' is not a JSON array: ${e.message?.take(80)}")
        }

        return try {
            val (spec, notes) = CustomAnimations.parse(document)
            onPlay(spec)
            val saved = if (args["save"]?.trim()?.lowercase() == "true") {
                store?.let { s -> runCatching { s.save(spec) }.isSuccess } ?: false
            } else {
                false
            }
            val noteText = if (notes.isEmpty()) "" else " Adjusted: ${notes.joinToString("; ")}."
            val savedText = if (saved) " Saved for later." else ""
            ToolResult.Success(
                "Playing \"${spec.name}\" — ${spec.frames.size} frames over " +
                    "${spec.durationMs}ms.$noteText$savedText"
            )
        } catch (e: CustomAnimations.SpecException) {
            ToolResult.Failure("Animation rejected: ${e.message}")
        }
    }
}
