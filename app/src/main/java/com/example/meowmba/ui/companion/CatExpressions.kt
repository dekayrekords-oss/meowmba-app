package com.example.meowmba.ui.companion

/**
 * Named expressions built from the Godot Cat Assistant's channel system
 * ([CatMotion]), not the 60 hand-baked keyframe presets that hit a collision
 * ceiling. Each name composes eye + ear + jaw + body; the engine springs and
 * body math do the rest.
 *
 * Names intentionally match the Godot ACTION_NAMES list (lowercase) so
 * `avatar.animate` and the Avatar card can play them by the same labels.
 */
object CatExpressions {

    data class Named(
        val name: String,
        val expression: ComposedExpression,
    )

    val catalog: List<Named> = listOf(
        Named("slow blink", ComposedExpression(EyeL.CLOSED, EarL.NEUTRAL, JawL.SHUT, Body.DRIFT, 2.4f, 0.4f)),
        Named("quick blink", ComposedExpression(EyeL.CLOSED, EarL.NEUTRAL, JawL.SHUT, Body.DRIFT, 0.45f, 0.2f)),
        Named("purr", ComposedExpression(EyeL.SOFT, EarL.FORWARD, JawL.PULSE, Body.BUZZ, 3.2f, 0.55f)),
        Named("startle", ComposedExpression(EyeL.WIDE, EarL.PERK, JawL.PARTED, Body.RECOIL, 1.5f, 1.1f)),
        Named("yawn", ComposedExpression(EyeL.SQUINT, EarL.BACK, JawL.WIDE, Body.SINK, 2.6f, 0.7f)),
        Named("groom", ComposedExpression(EyeL.SQUINT, EarL.NEUTRAL, JawL.LICK, Body.TIP, 3.4f, 0.5f)),
        Named("stalk", ComposedExpression(EyeL.ALERT, EarL.FORWARD, JawL.SHUT, Body.COIL, 3.2f, 0.9f)),
        Named("chirp", ComposedExpression(EyeL.WIDE, EarL.PERK, JawL.PARTED, Body.BOB, 2.0f, 0.7f)),
        Named("head bonk", ComposedExpression(EyeL.SOFT, EarL.FORWARD, JawL.SHUT, Body.SNAP_FWD, 1.7f, 1.0f)),
        Named("sneeze", ComposedExpression(EyeL.CLOSED, EarL.BACK, JawL.PARTED, Body.SHAKE_H, 1.5f, 1.0f)),
        Named("loaf", ComposedExpression(EyeL.SOFT, EarL.DROOP, JawL.SHUT, Body.SINK, 3.6f, 0.45f)),
        Named("double take", ComposedExpression(EyeL.WIDE, EarL.SPLAY, JawL.PARTED, Body.STAGGER, 1.6f, 0.9f)),
        Named("ear swivel", ComposedExpression(EyeL.NEUTRAL, EarL.SWIVEL, JawL.SHUT, Body.DRIFT, 2.2f, 0.5f)),
        Named("look away", ComposedExpression(EyeL.NARROW, EarL.ONE_BACK, JawL.SHUT, Body.SWAY, 2.0f, 0.6f)),
        Named("sniff", ComposedExpression(EyeL.WIDE, EarL.FORWARD, JawL.PARTED, Body.LEAN_IN, 1.8f, 0.7f)),
        Named("hiss", ComposedExpression(EyeL.HARD, EarL.FLAT, JawL.SNARL, Body.PULL_BACK, 1.6f, 1.0f)),
        Named("growl", ComposedExpression(EyeL.HARD, EarL.BACK, JawL.SNARL, Body.BUZZ, 2.4f, 0.6f)),
        Named("trill", ComposedExpression(EyeL.SOFT, EarL.PERK, JawL.PULSE, Body.WIGGLE, 1.8f, 0.7f)),
        Named("blep", ComposedExpression(EyeL.SOFT, EarL.NEUTRAL, JawL.LICK, Body.DRIFT, 2.6f, 0.35f)),
        Named("lick lips", ComposedExpression(EyeL.SQUINT, EarL.NEUTRAL, JawL.LICK, Body.NOD, 1.1f, 0.4f)),
        Named("head shake", ComposedExpression(EyeL.NARROW, EarL.SPLAY, JawL.SHUT, Body.SHAKE_H, 1.0f, 1.0f)),
        Named("shiver", ComposedExpression(EyeL.WIDE, EarL.BACK, JawL.SHUT, Body.BUZZ, 1.4f, 1.0f)),
        Named("stretch", ComposedExpression(EyeL.SQUINT, EarL.BACK, JawL.WIDE, Body.RISE, 2.8f, 0.8f)),
        Named("wake up", ComposedExpression(EyeL.SLEEPY, EarL.DROOP, JawL.PARTED, Body.RISE, 3.0f, 0.6f)),
        Named("fall asleep", ComposedExpression(EyeL.SLEEPY, EarL.DROOP, JawL.SHUT, Body.SINK, 3.2f, 0.5f)),
        Named("perk up", ComposedExpression(EyeL.ALERT, EarL.PERK, JawL.SHUT, Body.SNAP_FWD, 1.2f, 0.9f)),
        Named("pounce", ComposedExpression(EyeL.ALERT, EarL.FORWARD, JawL.SHUT, Body.COIL, 1.9f, 1.15f)),
        Named("recoil", ComposedExpression(EyeL.WIDE, EarL.FLAT, JawL.PARTED, Body.RECOIL, 0.9f, 1.1f)),
        Named("crouch", ComposedExpression(EyeL.ALERT, EarL.BACK, JawL.SHUT, Body.SINK, 2.2f, 0.85f)),
        Named("zoomies", ComposedExpression(EyeL.WIDE, EarL.PERK, JawL.PARTED, Body.WIGGLE, 1.3f, 1.2f)),
        Named("wink", ComposedExpression(EyeL.SOFT, EarL.NEUTRAL, JawL.SHUT, Body.TIP, 1.0f, 0.4f)),
        Named("smug", ComposedExpression(EyeL.SQUINT, EarL.FORWARD, JawL.PURSE, Body.TIP, 2.6f, 0.45f)),
        Named("confused", ComposedExpression(EyeL.WOUNDED, EarL.ONE_BACK, JawL.PARTED, Body.TIP, 2.2f, 0.6f)),
        Named("sad", ComposedExpression(EyeL.WOUNDED, EarL.DROOP, JawL.SHUT, Body.SINK, 3.0f, 0.4f)),
        Named("plead", ComposedExpression(EyeL.WOUNDED, EarL.FORWARD, JawL.PARTED, Body.LEAN_IN, 2.8f, 0.55f)),
        Named("excited", ComposedExpression(EyeL.ALERT, EarL.PERK, JawL.PULSE, Body.BOB, 1.8f, 1.0f)),
        Named("content sigh", ComposedExpression(EyeL.SOFT, EarL.DROOP, JawL.PARTED, Body.SINK, 2.8f, 0.35f)),
        Named("disgust", ComposedExpression(EyeL.SQUINT, EarL.BACK, JawL.SNEER, Body.PULL_BACK, 1.6f, 0.7f)),
        Named("sneer", ComposedExpression(EyeL.HARD, EarL.ONE_BACK, JawL.SNEER, Body.TIP, 2.0f, 0.5f)),
        Named("bunt", ComposedExpression(EyeL.SOFT, EarL.FORWARD, JawL.SHUT, Body.SNAP_FWD, 1.8f, 0.75f)),
        Named("knead", ComposedExpression(EyeL.SOFT, EarL.NEUTRAL, JawL.PULSE, Body.NOD, 2.8f, 0.55f)),
        Named("squint", ComposedExpression(EyeL.SQUINT, EarL.NEUTRAL, JawL.SHUT, Body.DRIFT, 2.4f, 0.3f)),
        Named("stare", ComposedExpression(EyeL.ALERT, EarL.FORWARD, JawL.SHUT, Body.DRIFT, 3.0f, 0.25f)),
        Named("surprise", ComposedExpression(EyeL.WIDE, EarL.SPLAY, JawL.PARTED, Body.FLOAT_UP, 1.4f, 1.0f)),
        Named("relief", ComposedExpression(EyeL.SOFT, EarL.DROOP, JawL.PARTED, Body.SINK, 2.6f, 0.4f)),
        Named("play wiggle", ComposedExpression(EyeL.WIDE, EarL.PERK, JawL.PARTED, Body.WIGGLE, 1.6f, 1.0f)),
        Named("annoyed flick", ComposedExpression(EyeL.NARROW, EarL.SWIVEL, JawL.SHUT, Body.SHAKE_H, 1.5f, 0.7f)),
        Named("greet", ComposedExpression(EyeL.SOFT, EarL.FORWARD, JawL.PARTED, Body.BOB, 3.0f, 0.6f)),
        Named("settle", ComposedExpression(EyeL.SLEEPY, EarL.DROOP, JawL.SHUT, Body.SINK, 3.2f, 0.35f)),
        // Keyframe built-in names that should also route to channels when possible
        Named("paw lick", ComposedExpression(EyeL.SQUINT, EarL.NEUTRAL, JawL.LICK, Body.TIP, 1.8f, 0.55f)),
        Named("tail wag", ComposedExpression(EyeL.SOFT, EarL.FORWARD, JawL.SHUT, Body.SWAY, 2.0f, 0.7f)),
        Named("zoomies burst", ComposedExpression(EyeL.WIDE, EarL.PERK, JawL.PARTED, Body.WIGGLE, 1.4f, 1.15f)),
    )

    private val byKey: Map<String, ComposedExpression> =
        catalog.associate { normalize(it.name) to it.expression }

    fun names(): List<String> = catalog.map { it.name }

    /**
     * Expressions that only make sense when the companion actually feels that
     * way. Idle used to draw uniformly from all 51, so a calm, idle cat would
     * hiss, recoil, or perform a startle at a fair 1-in-51 every cycle — the
     * literal cause of "sometimes moves without making sense".
     */
    private val NEGATIVE = setOf(
        "hiss", "growl", "sneer", "disgust", "annoyed flick", "sad", "wounded",
        "startle", "recoil", "shiver", "crouch", "look away", "grumpy squint",
    )

    private val HIGH_ENERGY = setOf(
        "zoomies", "zoomies burst", "play wiggle", "excited", "pounce",
        "wiggle pounce", "double take", "surprise", "perk up", "trill",
    )

    private val SLEEPY = setOf(
        "settle", "fall asleep", "content sigh", "loaf", "slow blink", "relief",
        "squint", "sleepy collapse", "purr loaf",
    )

    /**
     * The expressions that suit a mood, for ambient idle performance.
     *
     * Anything not excluded is allowed, so the pool stays wide — the point is
     * to stop the face contradicting itself, not to narrow it to three clips.
     */
    fun idlePoolFor(emotion: CompanionEmotion): List<String> {
        val allowNegative = emotion == CompanionEmotion.ALERT ||
            emotion == CompanionEmotion.CURIOUS
        return catalog.map { it.name }.filter { name ->
            when {
                !allowNegative && name in NEGATIVE -> false
                emotion == CompanionEmotion.SLEEPY && name in HIGH_ENERGY -> false
                emotion == CompanionEmotion.SLEEPY -> true
                name in SLEEPY && emotion == CompanionEmotion.HAPPY -> false
                else -> true
            }
        }
    }

    fun resolve(name: String): ComposedExpression? = byKey[normalize(name)]

    fun normalize(name: String): String =
        name.trim().lowercase()
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
}

/**
 * Maps a [MotionPose] from [CatMotionEngine] onto the fields [AvatarRenderer]
 * already knows how to draw. Not a full port of every Godot channel (per-ear
 * split / lip curl need deeper renderer work) — enough that composed
 * expressions read as the Godot motion system on the live face.
 */
fun MotionPose.applyTo(base: AvatarPose, faceRadius: Float = 200f): AvatarPose {
    val r = faceRadius.coerceAtLeast(1f)
    val lean = (offsetX / (r * 0.35f)).coerceIn(-1f, 1f)
    val bounce = ((-offsetY / (r * 0.25f)) + scaleDelta * 4f).coerceIn(-1f, 1f)
    val perk = (1f + earPitchDeg / 40f).coerceIn(0.3f, 1.65f)
    val droop = ((-earPitchDeg * 0.35f) + (earSplayDeg - 12f).coerceAtLeast(0f) * 0.4f)
        .coerceIn(0f, 40f)
    val tongue = when {
        jawOpen > 0.45f -> ((jawOpen - 0.25f) / 0.75f).coerceIn(0f, 1f)
        else -> 0f
    }
    val mouth = when {
        lipCurl > 0.35f || jawOpen > 0.4f -> MouthMode.SPEAKING
        jawOpen > 0.15f -> MouthMode.HAPPY
        else -> base.mouth
    }
    return base.copy(
        eyeOpen = eyeOpen.coerceIn(0.05f, 1.6f),
        pupilSize = pupilSize.coerceIn(0.05f, 0.95f),
        headTiltDeg = (base.headTiltDeg + headTiltDeg + earSplitDeg * 0.08f).coerceIn(-28f, 28f),
        earPerk = perk,
        earDroopDeg = droop,
        bodyBounce = bounce,
        leanX = lean,
        tongueOut = tongue,
        whiskerFlutter = jawOpen > 0.12f || lipCurl > 0.12f,
        mouth = mouth,
        happyEyes = false,
        sparkle = if (jawOpen > 0.2f && eyeOpen > 1.05f) 0.35f else base.sparkle,
        tailWag = (offsetX / (r * 0.4f)).coerceIn(-1f, 1f) * 0.6f,
    )
}
