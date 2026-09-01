package com.example.meowmba.core.robot

import com.example.meowmba.ui.companion.AnimFrame
import com.example.meowmba.ui.companion.Body as ComposedBody
import com.example.meowmba.ui.companion.ComposedExpression
import com.example.meowmba.ui.companion.CustomAnimationSpec
import kotlin.math.abs

/**
 * Drives the body from a composed emotional expression, just as the face does.
 *
 * This is the WALL-E/E.M.O. design grammar: the body mirrors whatever the face
 * is feeling, on the same timeline. The avatar already renders [ComposedExpression]
 * on screen; this map turns the same expression into the body's verb timeline,
 * which [BodyScheduler] plays on the same clock. Face and base therefore cannot
 * disagree about what is being felt.
 *
 * Pure by construction — [profile] is an argument, so the "DESK never drives"
 * invariant holds here exactly as it does in [BodyMotionMap].
 *
 * The mapping is deliberately coarse: an expression is a pose with a character,
 * not a 12-frame clip. Emotion reads in two or three phases — attack, hold,
 * settle — which is plenty for a base that moves in whole-body gestures.
 */
object EmotionMap {

    private const val HAPPY_GLOW = 0xFFD29922L
    private val SINK_GLOW = 0xFF263238L

    fun cues(expr: ComposedExpression, profile: BodyProfile): List<BodyCue> {
        val durationMs = (expr.durationSeconds * 1000f).toInt().coerceAtLeast(500)
        val holdMs = (durationMs * 0.5f).toInt()
        val settleMs = durationMs - 200

        val frames = buildList {
            add(frameFor(expr, atMs = 0, amount = 1f))
            add(frameFor(expr, atMs = holdMs, amount = 0.85f))
            add(settleFrame(expr, atMs = settleMs))
        }

        return BodyMotionMap.cues(
            CustomAnimationSpec("emotion:${label(expr)}", frames),
            profile,
        )
    }

    /**
     * One frame's rapid (but legal) summary of the expression's character.
     *
     * Which face channels the base actually has: gaze (neck pitch), head tilt
     * (turn), body bounce (bob or drive pulse), zest (turn), brightness
     * (headlight). Everything else an expression says is face-only and stays
     * on the face.
     */
    private fun frameFor(expr: ComposedExpression, atMs: Int, amount: Float): AnimFrame {
        val amp = expr.bodyAmp * amount
        return AnimFrame(
            atMs = atMs,
            pupilGazeUp = gazeUp(expr, amp),
            headTiltDeg = headTiltDeg(expr, amp),
            bodyBounce = bodyBounce(expr, amp),
            tailWag = tailWag(expr, amp),
            sparkle = sparkle(expr, amp),
            glowColorHex = glow(expr, amp),
        )
    }

    /** Neutral pose on the same axes, so the base has somewhere to settle. */
    private fun settleFrame(expr: ComposedExpression, atMs: Int): AnimFrame = AnimFrame(
        atMs = atMs,
        pupilGazeUp = 0.5f,
        headTiltDeg = 0f,
        bodyBounce = 0f,
        tailWag = 0f,
    )

    /** -1 fully up, 0 center, +1 down on the neck axis. */
    private fun gazeUp(expr: ComposedExpression, amp: Float): Float = when (expr.body) {
        // Looking up reads as lifted spirits.
        ComposedBody.BOB, ComposedBody.RISE, ComposedBody.ORBIT, ComposedBody.FLOAT_UP, ComposedBody.SNAP_FWD -> (0.5f + 0.3f * amp).coerceIn(0f, 1f)
        // Looking down reads as low spirits or wariness.
        ComposedBody.SINK, ComposedBody.PULL_BACK, ComposedBody.COIL, ComposedBody.STAGGER, ComposedBody.TIP, ComposedBody.LURCH, ComposedBody.RECOIL -> (0.5f - 0.35f * amp).coerceIn(0f, 1f)
        // Leaning toward a subject closes the gap naturally.
        ComposedBody.LEAN_IN -> 0.45f
        else -> 0.5f
    }

    private fun headTiltDeg(expr: ComposedExpression, amp: Float): Float? {
        val tilt = when (expr.body) {
            ComposedBody.LEAN_IN -> -12f * amp
            ComposedBody.PULL_BACK -> 10f * amp
            ComposedBody.SHAKE_H, ComposedBody.RECOIL, ComposedBody.SNAP_FWD, ComposedBody.TIP -> 6f * amp
            else -> 0f
        }
        return if (abs(tilt) < 4f) null else tilt
    }

    private fun bodyBounce(expr: ComposedExpression, amp: Float): Float? {
        val bounce = when (expr.body) {
            ComposedBody.BOB, ComposedBody.RISE, ComposedBody.ORBIT -> 0.55f * amp
            ComposedBody.NOD, ComposedBody.SNAP_FWD -> 0.3f * amp
            ComposedBody.SINK, ComposedBody.COIL, ComposedBody.STAGGER, ComposedBody.TIP, ComposedBody.LURCH -> -0.4f * amp
            else -> 0f
        }
        return if (abs(bounce) < 0.15f) null else bounce
    }

    private fun tailWag(expr: ComposedExpression, amp: Float): Float? {
        val wag = when (expr.body) {
            ComposedBody.WIGGLE, ComposedBody.SHAKE_H, ComposedBody.SHAKE_V, ComposedBody.BUZZ -> 0.9f * amp
            ComposedBody.FLOAT_UP, ComposedBody.ORBIT -> 0.45f * amp
            else -> 0f
        }
        return if (abs(wag) < 0.15f) null else wag
    }

    private fun sparkle(expr: ComposedExpression, amp: Float): Float? {
        val s = when (expr.body) {
            ComposedBody.BOB, ComposedBody.RISE, ComposedBody.FLOAT_UP, ComposedBody.SNAP_FWD, ComposedBody.WIGGLE -> 0.7f * amp
            else -> 0f
        }
        return if (s > 0.5f) s else null
    }

    private fun glow(expr: ComposedExpression, amp: Float): Long? = when (expr.body) {
        ComposedBody.BOB, ComposedBody.RISE, ComposedBody.FLOAT_UP, ComposedBody.SNAP_FWD, ComposedBody.WIGGLE -> if (amp > 0.4f) HAPPY_GLOW else null
        ComposedBody.SINK, ComposedBody.COIL, ComposedBody.STAGGER, ComposedBody.TIP, ComposedBody.LURCH, ComposedBody.PULL_BACK, ComposedBody.RECOIL -> SINK_GLOW
        else -> null
    }

    private fun label(expr: ComposedExpression): String = expr.body.name.lowercase()
}