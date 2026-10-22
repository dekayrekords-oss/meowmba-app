package com.example.meowmba.core.agent.tools

import com.example.meowmba.ui.companion.AnimFrame
import com.example.meowmba.ui.companion.CustomAnimationSpec
import com.example.meowmba.ui.companion.MouthMode

/**
 * Keyword-to-pattern engine that maps natural language descriptions to
 * pre-baked animation keyframes. Used by [DescribeAnimationTool] to let
 * the companion play expressive animations without inventing frames at runtime.
 */
object AnimationDescriber {

    private data class Pattern(
        val weight: Float,
        val durationMs: Int,
        val frames: List<AnimFrame>,
    )

    private val patterns: Map<String, Pattern> = mapOf(
        "squirrel" to Pattern(
            weight = 1.35f, durationMs = 3800,
            frames = listOf(
                AnimFrame(atMs = 0, eyeOpen = 1f, headTiltDeg = 0f, tailWag = 0f),
                AnimFrame(atMs = 600, eyeOpen = 1.18f, pupilSize = 0.62f, headTiltDeg = -8f, earPerk = 1.3f),
                AnimFrame(atMs = 1300, headTiltDeg = -5f, bodyBounce = 0.08f, tailWag = 0.65f),
                AnimFrame(atMs = 1950, headTiltDeg = 7f, bodyBounce = 0f, tailWag = -0.25f),
                AnimFrame(atMs = 2700, eyeOpen = 0.80f, mouth = MouthMode.HAPPY, headTiltDeg = 3f, tailWag = 0.15f),
                AnimFrame(atMs = 3800, eyeOpen = 1f, pupilSize = 0.5f, headTiltDeg = 0f, earPerk = 1f, tailWag = 0f, mouth = MouthMode.IDLE),
            ),
        ),
        "raccoon" to Pattern(
            weight = 1.35f, durationMs = 4100,
            frames = listOf(
                AnimFrame(atMs = 0, eyeOpen = 1f, headTiltDeg = 0f, tailWag = 0f),
                AnimFrame(atMs = 750, eyeOpen = 0.84f, headTiltDeg = 9f, pupilSize = 0.58f, earPerk = 1.2f),
                AnimFrame(atMs = 1650, eyeOpen = 1.15f, headTiltDeg = -6f, tailWag = -0.35f),
                AnimFrame(atMs = 2450, eyeOpen = 0.82f, headTiltDeg = -3f, mouth = MouthMode.HAPPY, tailWag = 0.3f),
                AnimFrame(atMs = 3300, eyeOpen = 0.18f, headTiltDeg = 2f, tailWag = 0f),
                AnimFrame(atMs = 4100, eyeOpen = 1f, headTiltDeg = 0f, pupilSize = 0.5f, earPerk = 1f, mouth = MouthMode.IDLE),
            ),
        ),
        "cat" to Pattern(
            weight = 1.35f, durationMs = 5000,
            frames = listOf(
                AnimFrame(atMs = 0, eyeOpen = 1f, pupilSize = 0.5f, headTiltDeg = 0f),
                AnimFrame(atMs = 850, eyeOpen = 1.1f, pupilSize = 0.65f, headTiltDeg = -10f, earPerk = 1.3f),
                AnimFrame(atMs = 1700, eyeOpen = 0.5f, headTiltDeg = -7f, mouth = MouthMode.HAPPY),
                AnimFrame(atMs = 2400, eyeOpen = 0.12f, bodyBounce = 0.08f),
                AnimFrame(atMs = 3100, eyeOpen = 0.86f, headTiltDeg = 5f, bodyBounce = 0f),
                AnimFrame(atMs = 4150, eyeOpen = 0.4f, headTiltDeg = 2f),
                AnimFrame(atMs = 5000, eyeOpen = 1f, headTiltDeg = 0f, pupilSize = 0.5f, mouth = MouthMode.IDLE),
            ),
        ),
        "stretch" to Pattern(
            weight = 1.0f,
            durationMs = 2200,
            frames = listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, earDroopDeg = 0f, headTiltDeg = 0f, mouth = MouthMode.IDLE),
                AnimFrame(atMs = 400, eyeOpen = 0.5f, earDroopDeg = 8f, mouth = MouthMode.IDLE),
                AnimFrame(atMs = 750, eyeOpen = 0.1f, earDroopDeg = 18f, headTiltDeg = 6f, mouth = MouthMode.HAPPY),
                AnimFrame(atMs = 1100, eyeOpen = 0.06f, earDroopDeg = 24f, headTiltDeg = 8f, mouth = MouthMode.SPEAKING),
                AnimFrame(atMs = 1500, eyeOpen = 0.35f, earDroopDeg = 12f, headTiltDeg = 4f, mouth = MouthMode.HAPPY),
                AnimFrame(atMs = 2200, eyeOpen = 1.0f, earDroopDeg = 0f, headTiltDeg = 0f, mouth = MouthMode.IDLE),
            ),
        ),

        "jump" to Pattern(
            weight = 1.2f,
            durationMs = 800,
            frames = listOf(
                AnimFrame(atMs = 0, bodyBounce = 0f, eyeOpen = 1.0f, headTiltDeg = 0f),
                AnimFrame(atMs = 120, bodyBounce = -0.4f, eyeOpen = 1.3f, earPerk = 1.3f),
                AnimFrame(atMs = 280, bodyBounce = 0.9f, eyeOpen = 1.5f, earPerk = 1.5f, headTiltDeg = -3f),
                AnimFrame(atMs = 440, bodyBounce = 0.4f, eyeOpen = 1.3f, earPerk = 1.2f),
                AnimFrame(atMs = 600, bodyBounce = -0.15f, eyeOpen = 1.1f),
                AnimFrame(atMs = 800, bodyBounce = 0f, eyeOpen = 1.0f, earPerk = 1.0f, headTiltDeg = 0f),
            ),
        ),

        "bounce" to Pattern(
            weight = 0.8f,
            durationMs = 1200,
            frames = listOf(
                AnimFrame(atMs = 0, bodyBounce = 0f, headTiltDeg = 0f),
                AnimFrame(atMs = 200, bodyBounce = 0.6f, headTiltDeg = -2f),
                AnimFrame(atMs = 400, bodyBounce = -0.15f, headTiltDeg = 1f),
                AnimFrame(atMs = 600, bodyBounce = 0.5f, headTiltDeg = -1f),
                AnimFrame(atMs = 800, bodyBounce = -0.1f, headTiltDeg = 1f),
                AnimFrame(atMs = 1000, bodyBounce = 0.25f, headTiltDeg = 0f),
                AnimFrame(atMs = 1200, bodyBounce = 0f, headTiltDeg = 0f),
            ),
        ),

        "shake" to Pattern(
            weight = 0.9f,
            durationMs = 1000,
            frames = listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f),
                AnimFrame(atMs = 140, headTiltDeg = -18f),
                AnimFrame(atMs = 300, headTiltDeg = 18f),
                AnimFrame(atMs = 460, headTiltDeg = -12f),
                AnimFrame(atMs = 620, headTiltDeg = 8f),
                AnimFrame(atMs = 800, headTiltDeg = -4f),
                AnimFrame(atMs = 1000, headTiltDeg = 0f),
            ),
        ),

        "nod" to Pattern(
            weight = 0.9f,
            durationMs = 1400,
            frames = listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, eyeOpen = 1.0f),
                AnimFrame(atMs = 250, headTiltDeg = 10f, eyeOpen = 0.8f),
                AnimFrame(atMs = 500, headTiltDeg = 0f, eyeOpen = 1.0f),
                AnimFrame(atMs = 750, headTiltDeg = 10f, eyeOpen = 0.8f),
                AnimFrame(atMs = 1000, headTiltDeg = 0f, eyeOpen = 1.0f),
                AnimFrame(atMs = 1200, headTiltDeg = 6f, eyeOpen = 0.9f),
                AnimFrame(atMs = 1400, headTiltDeg = 0f, eyeOpen = 1.0f),
            ),
        ),

        "tilt" to Pattern(
            weight = 0.8f,
            durationMs = 1200,
            frames = listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, pupilSize = 0.28f, earPerk = 1.0f),
                AnimFrame(atMs = 350, headTiltDeg = 16f, pupilSize = 0.55f, earPerk = 1.2f),
                AnimFrame(atMs = 700, headTiltDeg = 16f, pupilSize = 0.62f, earPerk = 1.25f),
                AnimFrame(atMs = 1200, headTiltDeg = 0f, pupilSize = 0.28f, earPerk = 1.0f),
            ),
        ),

        "happy" to Pattern(
            weight = 1.0f,
            durationMs = 1600,
            frames = listOf(
                AnimFrame(atMs = 0, happyEyes = false, mouth = MouthMode.IDLE, tailWag = 0f, earPerk = 1.0f),
                AnimFrame(atMs = 250, happyEyes = true, mouth = MouthMode.HAPPY, tailWag = 0.8f, earPerk = 1.15f),
                AnimFrame(atMs = 500, tailWag = -0.7f, earPerk = 1.1f),
                AnimFrame(atMs = 750, happyEyes = true, tailWag = 0.9f, earPerk = 1.15f),
                AnimFrame(atMs = 1000, tailWag = -0.6f, earPerk = 1.05f),
                AnimFrame(atMs = 1300, happyEyes = true, tailWag = 0.4f, earPerk = 1.05f),
                AnimFrame(atMs = 1600, happyEyes = false, mouth = MouthMode.IDLE, tailWag = 0f, earPerk = 1.0f),
            ),
        ),

        "sad" to Pattern(
            weight = 1.0f,
            durationMs = 2000,
            frames = listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, earDroopDeg = 0f, headTiltDeg = 0f, pupilSize = 0.28f),
                AnimFrame(atMs = 400, eyeOpen = 0.6f, earDroopDeg = 8f, headTiltDeg = -4f),
                AnimFrame(atMs = 800, eyeOpen = 0.35f, earDroopDeg = 18f, headTiltDeg = -8f, pupilSize = 0.22f),
                AnimFrame(atMs = 1200, eyeOpen = 0.25f, earDroopDeg = 24f, headTiltDeg = -10f),
                AnimFrame(atMs = 1600, eyeOpen = 0.4f, earDroopDeg = 14f, headTiltDeg = -5f),
                AnimFrame(atMs = 2000, eyeOpen = 1.0f, earDroopDeg = 0f, headTiltDeg = 0f, pupilSize = 0.28f),
            ),
        ),

        "angry" to Pattern(
            weight = 1.0f,
            durationMs = 1800,
            frames = listOf(
                AnimFrame(atMs = 0, earPerk = 1.0f, pupilSize = 0.28f, headTiltDeg = 0f, earDroopDeg = 0f),
                AnimFrame(atMs = 300, earPerk = 0.5f, pupilSize = 0.15f, headTiltDeg = -6f, earDroopDeg = 14f),
                AnimFrame(atMs = 700, earPerk = 0.35f, pupilSize = 0.1f, headTiltDeg = -8f, earDroopDeg = 22f),
                AnimFrame(atMs = 1100, earPerk = 0.4f, pupilSize = 0.12f, headTiltDeg = -6f, earDroopDeg = 20f),
                AnimFrame(atMs = 1400, earPerk = 0.6f, pupilSize = 0.18f, headTiltDeg = -3f, earDroopDeg = 10f),
                AnimFrame(atMs = 1800, earPerk = 1.0f, pupilSize = 0.28f, headTiltDeg = 0f, earDroopDeg = 0f),
            ),
        ),

        "scared" to Pattern(
            weight = 1.0f,
            durationMs = 1600,
            frames = listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, pupilSize = 0.28f, earPerk = 1.0f, bodyBounce = 0f),
                AnimFrame(atMs = 200, eyeOpen = 1.6f, pupilSize = 0.75f, earPerk = 1.5f, bodyBounce = 0.3f),
                AnimFrame(atMs = 500, eyeOpen = 1.5f, pupilSize = 0.85f, earPerk = 1.55f, bodyBounce = -0.2f),
                AnimFrame(atMs = 800, eyeOpen = 1.6f, pupilSize = 0.88f, earPerk = 1.6f, bodyBounce = 0.35f),
                AnimFrame(atMs = 1100, eyeOpen = 1.4f, pupilSize = 0.7f, earPerk = 1.3f, bodyBounce = -0.1f),
                AnimFrame(atMs = 1600, eyeOpen = 1.0f, pupilSize = 0.28f, earPerk = 1.0f, bodyBounce = 0f),
            ),
        ),

        "excited" to Pattern(
            weight = 1.1f,
            durationMs = 1400,
            frames = listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, happyEyes = false, bodyBounce = 0f, tailWag = 0f, earPerk = 1.0f),
                AnimFrame(atMs = 180, eyeOpen = 1.5f, happyEyes = true, bodyBounce = 0.7f, tailWag = 0.9f, earPerk = 1.3f),
                AnimFrame(atMs = 360, bodyBounce = -0.2f, tailWag = -0.8f, earPerk = 1.15f),
                AnimFrame(atMs = 540, eyeOpen = 1.5f, happyEyes = true, bodyBounce = 0.7f, tailWag = 0.9f, earPerk = 1.3f),
                AnimFrame(atMs = 720, bodyBounce = -0.15f, tailWag = -0.7f, earPerk = 1.1f),
                AnimFrame(atMs = 1000, bodyBounce = 0.3f, tailWag = 0.4f, earPerk = 1.1f),
                AnimFrame(atMs = 1400, eyeOpen = 1.0f, happyEyes = false, bodyBounce = 0f, tailWag = 0f, earPerk = 1.0f),
            ),
        ),

        "curious" to Pattern(
            weight = 0.9f,
            durationMs = 1600,
            frames = listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, pupilSize = 0.28f, earPerk = 1.0f),
                AnimFrame(atMs = 350, headTiltDeg = 14f, pupilSize = 0.52f, earPerk = 1.25f),
                AnimFrame(atMs = 700, headTiltDeg = 18f, pupilSize = 0.65f, earPerk = 1.4f),
                AnimFrame(atMs = 1100, headTiltDeg = 14f, pupilSize = 0.55f, earPerk = 1.2f),
                AnimFrame(atMs = 1600, headTiltDeg = 0f, pupilSize = 0.28f, earPerk = 1.0f),
            ),
        ),

        "sleepy" to Pattern(
            weight = 1.0f,
            durationMs = 2600,
            frames = listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, earDroopDeg = 0f, headTiltDeg = 0f),
                AnimFrame(atMs = 500, eyeOpen = 0.5f, earDroopDeg = 10f, headTiltDeg = 4f),
                AnimFrame(atMs = 1000, eyeOpen = 0.2f, earDroopDeg = 20f, headTiltDeg = 8f),
                AnimFrame(atMs = 1500, eyeOpen = 0.1f, earDroopDeg = 26f, headTiltDeg = 10f),
                AnimFrame(atMs = 2000, eyeOpen = 0.25f, earDroopDeg = 14f, headTiltDeg = 5f),
                AnimFrame(atMs = 2600, eyeOpen = 1.0f, earDroopDeg = 0f, headTiltDeg = 0f),
            ),
        ),

        "yawn" to Pattern(
            weight = 1.2f,
            durationMs = 2000,
            frames = listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, mouth = MouthMode.IDLE, earDroopDeg = 0f),
                AnimFrame(atMs = 350, eyeOpen = 0.65f, mouth = MouthMode.IDLE, earDroopDeg = 6f),
                AnimFrame(atMs = 700, eyeOpen = 0.2f, mouth = MouthMode.SPEAKING, earDroopDeg = 18f),
                AnimFrame(atMs = 1050, eyeOpen = 0.06f, mouth = MouthMode.SPEAKING, earDroopDeg = 28f),
                AnimFrame(atMs = 1400, eyeOpen = 0.3f, mouth = MouthMode.HAPPY, earDroopDeg = 12f),
                AnimFrame(atMs = 2000, eyeOpen = 1.0f, mouth = MouthMode.IDLE, earDroopDeg = 0f),
            ),
        ),

        "purr" to Pattern(
            weight = 1.0f,
            durationMs = 2400,
            frames = listOf(
                AnimFrame(atMs = 0, whiskerFlutter = false, happyEyes = false, sparkle = 0f),
                AnimFrame(atMs = 400, whiskerFlutter = true, happyEyes = true, sparkle = 0.5f),
                AnimFrame(atMs = 900, whiskerFlutter = true, happyEyes = true, sparkle = 0.8f),
                AnimFrame(atMs = 1400, whiskerFlutter = true, happyEyes = true, sparkle = 0.7f),
                AnimFrame(atMs = 1900, whiskerFlutter = true, happyEyes = true, sparkle = 0.4f),
                AnimFrame(atMs = 2400, whiskerFlutter = false, happyEyes = false, sparkle = 0f),
            ),
        ),

        "sneeze" to Pattern(
            weight = 1.0f,
            durationMs = 800,
            frames = listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, eyeOpen = 1.0f, earPerk = 1.0f),
                AnimFrame(atMs = 120, headTiltDeg = -4f, eyeOpen = 1.3f, earPerk = 1.25f),
                AnimFrame(atMs = 220, headTiltDeg = 20f, eyeOpen = 0.06f, earPerk = 0.4f, earDroopDeg = 14f),
                AnimFrame(atMs = 420, headTiltDeg = 8f, eyeOpen = 0.25f, earPerk = 0.7f, earDroopDeg = 6f),
                AnimFrame(atMs = 800, headTiltDeg = 0f, eyeOpen = 1.0f, earPerk = 1.0f, earDroopDeg = 0f),
            ),
        ),

        "paw" to Pattern(
            weight = 1.0f,
            durationMs = 1800,
            frames = listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, eyeOpen = 1.0f, tongueOut = 0f, whiskerFlutter = false),
                AnimFrame(atMs = 300, headTiltDeg = 8f, eyeOpen = 0.55f, tongueOut = 0f, whiskerFlutter = false),
                AnimFrame(atMs = 500, headTiltDeg = 10f, eyeOpen = 0.4f, tongueOut = 0.9f, whiskerFlutter = true),
                AnimFrame(atMs = 700, headTiltDeg = 10f, eyeOpen = 0.4f, tongueOut = 0.3f, whiskerFlutter = true),
                AnimFrame(atMs = 900, headTiltDeg = 10f, eyeOpen = 0.4f, tongueOut = 0.9f, whiskerFlutter = true),
                AnimFrame(atMs = 1100, headTiltDeg = 10f, eyeOpen = 0.4f, tongueOut = 0.3f, whiskerFlutter = true),
                AnimFrame(atMs = 1400, headTiltDeg = 4f, eyeOpen = 0.75f, tongueOut = 0f, whiskerFlutter = false),
                AnimFrame(atMs = 1800, headTiltDeg = 0f, eyeOpen = 1.0f, tongueOut = 0f, whiskerFlutter = false),
            ),
        ),

        "lick" to Pattern(
            weight = 1.0f,
            durationMs = 1800,
            frames = listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, eyeOpen = 1.0f, tongueOut = 0f, whiskerFlutter = false),
                AnimFrame(atMs = 300, headTiltDeg = 8f, eyeOpen = 0.55f, tongueOut = 0f, whiskerFlutter = false),
                AnimFrame(atMs = 500, headTiltDeg = 10f, eyeOpen = 0.4f, tongueOut = 0.9f, whiskerFlutter = true),
                AnimFrame(atMs = 700, headTiltDeg = 10f, eyeOpen = 0.4f, tongueOut = 0.3f, whiskerFlutter = true),
                AnimFrame(atMs = 900, headTiltDeg = 10f, eyeOpen = 0.4f, tongueOut = 0.9f, whiskerFlutter = true),
                AnimFrame(atMs = 1100, headTiltDeg = 10f, eyeOpen = 0.4f, tongueOut = 0.3f, whiskerFlutter = true),
                AnimFrame(atMs = 1400, headTiltDeg = 4f, eyeOpen = 0.75f, tongueOut = 0f, whiskerFlutter = false),
                AnimFrame(atMs = 1800, headTiltDeg = 0f, eyeOpen = 1.0f, tongueOut = 0f, whiskerFlutter = false),
            ),
        ),

        "dance" to Pattern(
            weight = 1.0f,
            durationMs = 1600,
            frames = listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, happyEyes = false, tailWag = 0f, bodyBounce = 0f),
                AnimFrame(atMs = 200, headTiltDeg = -14f, happyEyes = true, tailWag = 0.8f, bodyBounce = 0.5f),
                AnimFrame(atMs = 400, headTiltDeg = 14f, tailWag = -0.7f, bodyBounce = 0.1f),
                AnimFrame(atMs = 600, headTiltDeg = -12f, happyEyes = true, tailWag = 0.8f, bodyBounce = 0.5f),
                AnimFrame(atMs = 800, headTiltDeg = 12f, tailWag = -0.6f, bodyBounce = 0.1f),
                AnimFrame(atMs = 1100, headTiltDeg = -6f, happyEyes = true, tailWag = 0.4f, bodyBounce = 0.3f),
                AnimFrame(atMs = 1600, headTiltDeg = 0f, happyEyes = false, tailWag = 0f, bodyBounce = 0f),
            ),
        ),

        "wave" to Pattern(
            weight = 0.8f,
            durationMs = 1200,
            frames = listOf(
                AnimFrame(atMs = 0, tailWag = 0f, earPerk = 1.0f),
                AnimFrame(atMs = 200, tailWag = 0.8f, earPerk = 1.2f),
                AnimFrame(atMs = 400, tailWag = -0.7f, earPerk = 1.15f),
                AnimFrame(atMs = 600, tailWag = 0.7f, earPerk = 1.2f),
                AnimFrame(atMs = 800, tailWag = -0.5f, earPerk = 1.1f),
                AnimFrame(atMs = 1200, tailWag = 0f, earPerk = 1.0f),
            ),
        ),

        "laugh" to Pattern(
            weight = 1.0f,
            durationMs = 1400,
            frames = listOf(
                AnimFrame(atMs = 0, happyEyes = false, bodyBounce = 0f, mouth = MouthMode.IDLE),
                AnimFrame(atMs = 180, happyEyes = true, bodyBounce = 0.6f, mouth = MouthMode.HAPPY),
                AnimFrame(atMs = 360, bodyBounce = -0.15f),
                AnimFrame(atMs = 540, happyEyes = true, bodyBounce = 0.6f, mouth = MouthMode.HAPPY),
                AnimFrame(atMs = 720, bodyBounce = -0.1f),
                AnimFrame(atMs = 950, happyEyes = true, bodyBounce = 0.3f, mouth = MouthMode.HAPPY),
                AnimFrame(atMs = 1400, happyEyes = false, bodyBounce = 0f, mouth = MouthMode.IDLE),
            ),
        ),

        "cry" to Pattern(
            weight = 1.0f,
            durationMs = 2000,
            frames = listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, earDroopDeg = 0f, headTiltDeg = 0f),
                AnimFrame(atMs = 400, eyeOpen = 0.5f, earDroopDeg = 10f, headTiltDeg = -4f),
                AnimFrame(atMs = 800, eyeOpen = 0.3f, earDroopDeg = 20f, headTiltDeg = -8f),
                AnimFrame(atMs = 1200, eyeOpen = 0.2f, earDroopDeg = 26f, headTiltDeg = -10f),
                AnimFrame(atMs = 1600, eyeOpen = 0.35f, earDroopDeg = 16f, headTiltDeg = -5f),
                AnimFrame(atMs = 2000, eyeOpen = 1.0f, earDroopDeg = 0f, headTiltDeg = 0f),
            ),
        ),

        "glow" to Pattern(
            weight = 0.7f,
            durationMs = 2000,
            frames = listOf(
                AnimFrame(atMs = 0, sparkle = 0f, happyEyes = false),
                AnimFrame(atMs = 400, sparkle = 0.4f, happyEyes = true),
                AnimFrame(atMs = 800, sparkle = 0.8f, happyEyes = true),
                AnimFrame(atMs = 1200, sparkle = 1.0f, happyEyes = true),
                AnimFrame(atMs = 1600, sparkle = 0.6f, happyEyes = true),
                AnimFrame(atMs = 2000, sparkle = 0f, happyEyes = false),
            ),
        ),

        "spin" to Pattern(
            weight = 1.0f,
            durationMs = 1200,
            frames = listOf(
                AnimFrame(atMs = 0, headTiltDeg = 0f, eyeOpen = 1.0f),
                AnimFrame(atMs = 150, headTiltDeg = -20f, eyeOpen = 1.4f),
                AnimFrame(atMs = 300, headTiltDeg = 20f),
                AnimFrame(atMs = 450, headTiltDeg = -22f, eyeOpen = 1.3f),
                AnimFrame(atMs = 600, headTiltDeg = 22f),
                AnimFrame(atMs = 750, headTiltDeg = -18f, eyeOpen = 1.2f),
                AnimFrame(atMs = 900, headTiltDeg = 12f),
                AnimFrame(atMs = 1050, headTiltDeg = -6f),
                AnimFrame(atMs = 1200, headTiltDeg = 0f, eyeOpen = 1.0f),
            ),
        ),

        // Acting like a dog: the cat does the dog things — an eager head tilt,
        // then open-mouthed panting with the tongue out while the tail sweeps.
        // The pant is a jaw/tongue PULSE, not one long open mouth: a held-open
        // mouth reads as a yawn, and it is the pulse that says "dog".
        "dog" to Pattern(
            weight = 1.35f,
            durationMs = 3800,
            frames = listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, pupilSize = 0.28f, headTiltDeg = 0f,
                    earPerk = 1.0f, tongueOut = 0f, tailWag = 0f, bodyBounce = 0f,
                    mouth = MouthMode.IDLE),
                // The "did you say walk?" — big tilt + ears shoot forward, eyes pop.
                AnimFrame(atMs = 300, eyeOpen = 1.45f, pupilSize = 0.65f, headTiltDeg = 18f,
                    earPerk = 1.55f, tongueOut = 0.2f, tailWag = 0.45f, bodyBounce = 0.25f,
                    sparkle = 0.5f),
                // Pant cycle: tongue FULLY out on the exhale, tail hard over.
                AnimFrame(atMs = 650, headTiltDeg = 18f, tongueOut = 1.0f,
                    mouth = MouthMode.SPEAKING, tailWag = 1.0f, bodyBounce = 0.38f,
                    earPerk = 1.5f, eyeOpen = 1.35f, whiskerFlutter = true),
                AnimFrame(atMs = 900, headTiltDeg = 16f, tongueOut = 0.2f,
                    mouth = MouthMode.HAPPY, tailWag = 0.15f, bodyBounce = 0.1f,
                    whiskerFlutter = true),
                AnimFrame(atMs = 1150, headTiltDeg = 16f, tongueOut = 1.0f,
                    mouth = MouthMode.SPEAKING, tailWag = -1.0f, bodyBounce = 0.38f,
                    whiskerFlutter = true),
                AnimFrame(atMs = 1400, headTiltDeg = 16f, tongueOut = 0.2f,
                    mouth = MouthMode.HAPPY, tailWag = -0.15f, bodyBounce = 0.1f,
                    whiskerFlutter = true),
                AnimFrame(atMs = 1650, headTiltDeg = 14f, tongueOut = 1.0f,
                    mouth = MouthMode.SPEAKING, tailWag = 1.0f, bodyBounce = 0.38f,
                    whiskerFlutter = true),
                AnimFrame(atMs = 1900, headTiltDeg = 12f, tongueOut = 0.2f,
                    mouth = MouthMode.HAPPY, tailWag = -0.9f, bodyBounce = 0.1f,
                    whiskerFlutter = true),
                AnimFrame(atMs = 2150, headTiltDeg = 10f, tongueOut = 1.0f,
                    mouth = MouthMode.SPEAKING, tailWag = 0.9f, bodyBounce = 0.35f,
                    whiskerFlutter = true),
                AnimFrame(atMs = 2400, headTiltDeg = 8f, tongueOut = 0.25f,
                    mouth = MouthMode.HAPPY, tailWag = 0.2f, bodyBounce = 0.12f),
                AnimFrame(atMs = 2800, headTiltDeg = 4f, tongueOut = 0.15f,
                    tailWag = 0.1f, bodyBounce = 0f, whiskerFlutter = false),
                AnimFrame(atMs = 3200, eyeOpen = 1.1f, pupilSize = 0.35f, headTiltDeg = 1f,
                    earPerk = 1.1f, tongueOut = 0f, tailWag = 0f, bodyBounce = 0f),
                AnimFrame(atMs = 3800, eyeOpen = 1.0f, pupilSize = 0.28f, headTiltDeg = 0f,
                    earPerk = 1.0f, tongueOut = 0f, tailWag = 0f, bodyBounce = 0f,
                    sparkle = 0f, mouth = MouthMode.IDLE),
            ),
        ),

        // Silly face: deliberately mismatched — one big googly eye, head flopped
        // the other way, tongue lolling. The asymmetry is the joke, so the two
        // sides are driven to OPPOSITE extremes rather than together.
        "silly" to Pattern(
            weight = 1.1f,
            durationMs = 2200,
            frames = listOf(
                AnimFrame(atMs = 0, eyeOpen = 1.0f, pupilSize = 0.28f, pupilGazeUp = 0f,
                    headTiltDeg = 0f, earPerk = 1.0f, tongueOut = 0f),
                AnimFrame(atMs = 300, eyeOpen = 1.55f, pupilSize = 0.7f, pupilGazeUp = 0.6f,
                    headTiltDeg = -12f, earPerk = 1.5f, mouth = MouthMode.HAPPY),
                AnimFrame(atMs = 700, eyeOpen = 0.5f, pupilSize = 0.35f, pupilGazeUp = 0f,
                    headTiltDeg = 16f, earPerk = 0.75f, tongueOut = 0.9f,
                    mouth = MouthMode.SPEAKING, bodyBounce = 0.25f, whiskerFlutter = true),
                AnimFrame(atMs = 1100, eyeOpen = 1.5f, pupilSize = 0.8f, headTiltDeg = -8f,
                    earPerk = 1.45f, tongueOut = 0.2f, bodyBounce = -0.1f, sparkle = 0.4f),
                AnimFrame(atMs = 1500, eyeOpen = 0.6f, pupilSize = 0.3f, pupilGazeUp = 0.4f,
                    headTiltDeg = 10f, earPerk = 0.8f, tongueOut = 0.85f,
                    mouth = MouthMode.SPEAKING, bodyBounce = 0.2f, whiskerFlutter = true),
                AnimFrame(atMs = 1900, eyeOpen = 1.2f, pupilSize = 0.4f, pupilGazeUp = 0f,
                    headTiltDeg = 0f, earPerk = 1.1f, tongueOut = 0.1f, bodyBounce = 0f,
                    sparkle = 0f),
                AnimFrame(atMs = 2200, eyeOpen = 1.0f, pupilSize = 0.28f, headTiltDeg = 0f,
                    earPerk = 1.0f, tongueOut = 0f, bodyBounce = 0f,
                    whiskerFlutter = false, mouth = MouthMode.IDLE),
            ),
        ),
    )

    private val synonyms = mapOf(
        "bop" to "bounce",
        "wiggle" to "shake",
        "drowsy" to "sleepy",
        "frightened" to "scared",
        "pissed" to "angry",
        // "be mad" was routed to the describer without "mad" ever being a
        // keyword or a synonym, so the single most natural way to ask for an
        // angry cat answered "I don't know how to animate that".
        "mad" to "angry",
        "madcat" to "angry",
        "grumpy" to "angry",
        "hissy" to "angry",
        "grin" to "happy",
        "playful" to "excited",
        // Dog: the cat playing at being a dog.
        "puppy" to "dog",
        "kitten" to "cat",
        "pant" to "dog",
        "panting" to "dog",
        "woof" to "dog",
        // Silly: goofy / derpy faces.
        "goofy" to "silly",
        "derp" to "silly",
        "derpy" to "silly",
        "dopey" to "silly",
    )

    /**
     * Converts a natural language description into a [CustomAnimationSpec].
     * Tokenizes the input, matches against known keywords and synonyms,
     * and returns the highest-weighted match. Returns null if nothing matches.
     */
    fun describe(text: String): CustomAnimationSpec? {
        val tokens = text.lowercase()
            .replace(Regex("[^a-z]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val matched = mutableSetOf<String>()
        for (token in tokens) {
            val key = synonyms[token] ?: token
            if (key in patterns) {
                matched.add(key)
            }
        }

        if (matched.isEmpty()) return null

        val winner = matched.maxByOrNull { patterns[it]!!.weight }!!
        val pattern = patterns[winner]!!

        return CustomAnimationSpec(
            name = matched.sorted().joinToString(" "),
            frames = pattern.frames,
        )
    }
}
