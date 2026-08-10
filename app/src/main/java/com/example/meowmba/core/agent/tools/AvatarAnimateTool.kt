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
        description = buildString {
            append(
                "Make the cat avatar PERFORM on its own face right now: tricks, gestures, " +
                "expressions. ALWAYS use this — never a media/image/video tool — when the user " +
                "asks you to do a trick, dance, gesture, or to animate yourself/the cat/it " +
                "(\"lick your paw\", \"show me an animation of you...\", \"do something cute\"). " +
                "Media tools only make FILES; this is the only tool that moves the face.\n\n"
            )
            append(
                "FIELDS per keyframe: at (ms, required), eye_open (0.0-1.6; 1.0 normal, " +
                "0.06 fully closed, 1.6 wide-eyed), pupil_size (0.05-0.9; 0.28 normal, " +
                "0.9 full dilation), gaze_up (0-1), head_tilt (-25 to 25 deg; negative=left), " +
                "ear_perk (0.3-1.6; 1.0 neutral, 1.6 radar-locked, 0.35 flattened), " +
                "ear_droop (0-40 deg; 0 up, 28+ submissive/sleepy), happy_eyes (boolean), " +
                "whisker_flutter (boolean), mouth (IDLE|HAPPY|SPEAKING), glow (#RRGGBB), " +
                "tongue_out (0.0-1.0; 0 hidden, 1 fully extended for licking), " +
                "body_bounce (-1 to 1; -1 squish down, 1 pop up), " +
                "tail_wag (-1 to 1; -1 left, 1 right), " +
                "sparkle (0.0-1.0; particle sparkle intensity). " +
                "Fields left out keep their normal value. Numbers smoothstep-interpolate " +
                "between keyframes; booleans/mouth/glow step at their keyframe.\n\n"
            )
            append(
                "HOW TO INVENT FRAMES — ten annotated examples:\n" +
                "SLOW BLINK (eye_open only, 2s): " +
                "[{\"at\":0,\"eye_open\":1.0},{\"at\":700,\"eye_open\":0.06}," +
                "{\"at\":1300,\"eye_open\":0.06},{\"at\":2000,\"eye_open\":1.0}]\n" +
                "HEADSHAKE (head_tilt oscillation damped, 1.2s): " +
                "[{\"at\":0,\"head_tilt\":0},{\"at\":160,\"head_tilt\":-20}," +
                "{\"at\":360,\"head_tilt\":20},{\"at\":560,\"head_tilt\":-14}," +
                "{\"at\":760,\"head_tilt\":12},{\"at\":960,\"head_tilt\":-6}," +
                "{\"at\":1200,\"head_tilt\":0}]\n" +
                "PAW LICK (multi-field + tongue, 1.8s): " +
                "[{\"at\":0,\"head_tilt\":0,\"eye_open\":1.0,\"tongue_out\":0}," +
                "{\"at\":350,\"head_tilt\":8,\"eye_open\":0.55}," +
                "{\"at\":550,\"head_tilt\":10,\"eye_open\":0.4,\"whisker_flutter\":true,\"tongue_out\":0.9}," +
                "{\"at\":750,\"head_tilt\":10,\"eye_open\":0.4,\"whisker_flutter\":true,\"tongue_out\":0.3}," +
                "{\"at\":950,\"head_tilt\":10,\"eye_open\":0.4,\"whisker_flutter\":true,\"tongue_out\":0.9}," +
                "{\"at\":1400,\"head_tilt\":4,\"eye_open\":0.75,\"whisker_flutter\":false,\"tongue_out\":0}," +
                "{\"at\":1800,\"head_tilt\":0,\"eye_open\":1.0}]\n" +
                "EAR FLATTEN (ear mechanics, 2s): " +
                "[{\"at\":0,\"ear_perk\":1.0,\"ear_droop\":0}," +
                "{\"at\":500,\"ear_perk\":0.6,\"ear_droop\":14}," +
                "{\"at\":1000,\"ear_perk\":0.35,\"ear_droop\":28}," +
                "{\"at\":1500,\"ear_perk\":0.35,\"ear_droop\":28}," +
                "{\"at\":2000,\"ear_perk\":1.0,\"ear_droop\":0}]\n" +
                "HUNTING FOCUS (pupils + glow, 1.6s): " +
                "[{\"at\":0,\"pupil_size\":0.28,\"ear_perk\":1.0}," +
                "{\"at\":380,\"pupil_size\":0.58,\"ear_perk\":1.3,\"head_tilt\":5}," +
                "{\"at\":800,\"pupil_size\":0.85,\"ear_perk\":1.55,\"glow\":\"#D29922\"}," +
                "{\"at\":1200,\"pupil_size\":0.9,\"ear_perk\":1.6}," +
                "{\"at\":1600,\"pupil_size\":0.28,\"ear_perk\":1.0,\"head_tilt\":0}]\n" +
                "PURR (whisker_flutter + happy_eyes + sparkle, 2.4s): " +
                "[{\"at\":0,\"whisker_flutter\":false,\"happy_eyes\":false,\"sparkle\":0}," +
                "{\"at\":400,\"whisker_flutter\":true,\"happy_eyes\":true,\"sparkle\":0.6}," +
                "{\"at\":1200,\"whisker_flutter\":true,\"happy_eyes\":true,\"sparkle\":0.8}," +
                "{\"at\":2000,\"whisker_flutter\":false,\"happy_eyes\":true,\"sparkle\":0.4}," +
                "{\"at\":2400,\"whisker_flutter\":false,\"happy_eyes\":false,\"sparkle\":0}]\n" +
                "SNEEZE (sudden head jerk + eye close, 0.8s): " +
                "[{\"at\":0,\"head_tilt\":0,\"eye_open\":1.0,\"ear_perk\":1.0}," +
                "{\"at\":150,\"head_tilt\":-5,\"eye_open\":1.2,\"ear_perk\":1.3}," +
                "{\"at\":250,\"head_tilt\":18,\"eye_open\":0.06,\"ear_perk\":0.5}," +
                "{\"at\":500,\"head_tilt\":8,\"eye_open\":0.3,\"ear_perk\":0.8}," +
                "{\"at\":800,\"head_tilt\":0,\"eye_open\":1.0,\"ear_perk\":1.0}]\n" +
                "HEAD BONK (affectionate headbutt, 1s): " +
                "[{\"at\":0,\"head_tilt\":0,\"body_bounce\":0,\"ear_perk\":1.0}," +
                "{\"at\":200,\"head_tilt\":-8,\"body_bounce\":-0.3,\"ear_perk\":1.2}," +
                "{\"at\":400,\"head_tilt\":12,\"body_bounce\":0.5,\"ear_perk\":1.4}," +
                "{\"at\":600,\"head_tilt\":5,\"body_bounce\":0.2,\"ear_perk\":1.1}," +
                "{\"at\":1000,\"head_tilt\":0,\"body_bounce\":0,\"ear_perk\":1.0}]\n" +
                "EXCITED BOUNCE (happy bounces, 1.4s): " +
                "[{\"at\":0,\"body_bounce\":0,\"happy_eyes\":false,\"tail_wag\":0}," +
                "{\"at\":180,\"body_bounce\":0.7,\"happy_eyes\":true,\"tail_wag\":0.8}," +
                "{\"at\":350,\"body_bounce\":-0.2,\"tail_wag\":-0.6}," +
                "{\"at\":520,\"body_bounce\":0.7,\"happy_eyes\":true,\"tail_wag\":0.8}," +
                "{\"at\":700,\"body_bounce\":-0.2,\"tail_wag\":-0.6}," +
                "{\"at\":900,\"body_bounce\":0.3,\"happy_eyes\":true,\"tail_wag\":0.4}," +
                "{\"at\":1400,\"body_bounce\":0,\"happy_eyes\":false,\"tail_wag\":0}]\n" +
                "SLEEPY NOD (drowsy head bob, 2.6s): " +
                "[{\"at\":0,\"head_tilt\":0,\"eye_open\":1.0,\"ear_droop\":0}," +
                "{\"at\":600,\"head_tilt\":6,\"eye_open\":0.4,\"ear_droop\":12}," +
                "{\"at\":1200,\"head_tilt\":10,\"eye_open\":0.15,\"ear_droop\":22}," +
                "{\"at\":1600,\"head_tilt\":3,\"eye_open\":0.8,\"ear_droop\":5}," +
                "{\"at\":2000,\"head_tilt\":8,\"eye_open\":0.2,\"ear_droop\":18}," +
                "{\"at\":2600,\"head_tilt\":0,\"eye_open\":1.0,\"ear_droop\":0}]\n" +
                "WAG TAIL (tail_wag + happy, 1.6s): " +
                "[{\"at\":0,\"tail_wag\":0,\"happy_eyes\":false,\"ear_perk\":1.0}," +
                "{\"at\":200,\"tail_wag\":0.9,\"happy_eyes\":true,\"ear_perk\":1.15}," +
                "{\"at\":400,\"tail_wag\":-0.9,\"ear_perk\":1.1}," +
                "{\"at\":600,\"tail_wag\":0.9,\"happy_eyes\":true,\"ear_perk\":1.15}," +
                "{\"at\":800,\"tail_wag\":-0.9,\"ear_perk\":1.1}," +
                "{\"at\":1000,\"tail_wag\":0.5,\"happy_eyes\":true,\"ear_perk\":1.05}," +
                "{\"at\":1600,\"tail_wag\":0,\"happy_eyes\":false,\"ear_perk\":1.0}]\n\n"
            )
            append(
                "Set save=true to keep the animation by name for later. " +
                "To REPLAY a named clip, give just the name with no frames. " +
                "Built-ins (play by name): " +
                CustomAnimations.BUILT_INS.keys.joinToString(", ") + "."
            )
        },
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
        } catch (e: Exception) {
            // The frames argument is authored by a language model, so it is
            // arbitrary text. SpecException only covers what the validator
            // itself rejects — a JSONException from malformed JSON, or a
            // NumberFormatException from "0.5f" or "twenty", sailed past this
            // catch and out of the tool. Every rejection must come back as a
            // Failure the companion can say out loud.
            ToolResult.Failure(
                "Animation rejected: could not read the keyframes " +
                    "(${e.message ?: e::class.java.simpleName})"
            )
        }
    }
}