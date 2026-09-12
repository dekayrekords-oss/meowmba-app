package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.sin

/** Phases are radians from the existing renderer clock; x/y are screen fractions. */
internal data class FullFlyPose(
    val x: Float = .5f, val y: Float = .45f, val scale: Float = 1f,
    val facing: Float = 1f, val flight: Float = 0f, val walkPhase: Float = 0f,
    val wingPhase: Float = 0f, val armReach: Float = 0f, val holding: String? = null,
    val handDrop: Float = 0f, val handX: Float = .39f,
)

/** Original vector character. Lumen supplies expression; habitat supplies locomotion.
 * No timers, randomness, sensors or neural inference belong in a paint operation.
 * The extra arms are deliberate cartoon anatomy, not a biological fly simulation.
 */
internal class FullFlyPainter {
    private val path = Path()
    private val eyeClip = Path()
    private val ink = Color(0xFF15282D)
    private val shell = Color(0xFF354C50)
    private val shine = Color(0xFF6D8A87)
    private val wingFill = Brush.linearGradient(listOf(Color(0xBBE7FFFF), Color(0x7781B7C0)),
        Offset(.14f, -.10f), Offset(.64f, -.61f))
    private val bodyFill = Brush.linearGradient(listOf(Color(0xFF8EBD62), Color(0xFF4E884E), Color(0xFF294B3D)),
        Offset(-.2f, .1f), Offset(.22f, .5f))
    private val headFill = Brush.radialGradient(listOf(shine, shell, ink), Offset(-.12f, -.36f), .62f)
    private val eyeFill = Brush.radialGradient(listOf(Color(0xFFFFAA74), Color(0xFFF2584D), Color(0xFF9A2941)),
        Offset(-.045f, -.055f), .30f)

    fun draw(scope: DrawScope, frame: LumenFrame, pose: FullFlyPose) = with(scope) {
        if (size.minDimension <= 0f || frame.presence <= .001f) return@with
        fun Float.safe(default: Float) = if (isFinite()) this else default
        val unit = minOf(size.minDimension * .32f * pose.scale.safe(1f).coerceIn(.35f, 1.8f), size.minDimension * .46f)
        // Room for wings, feet and held props, even at corner targets or on a narrow Fold viewport.
        val margin = unit * 1.05f
        val center = Offset((size.width * pose.x.safe(.5f)).coerceIn(margin, size.width - margin),
            (size.height * pose.y.safe(.45f)).coerceIn(unit * .88f, size.height - unit * .73f))
        val flight = pose.flight.safe(0f).coerceIn(0f, 1f)
        val step = sin(pose.walkPhase.safe(0f)) * (1f - flight)
        val reach = pose.armReach.safe(0f).coerceIn(0f, 1f)
        val handDrop = pose.handDrop.safe(0f).coerceIn(0f, .56f)
        val handX = pose.handX.safe(.39f).coerceIn(.25f, .85f)
        translate(center.x, center.y) {
            scale(unit * if (pose.facing < 0f) -1f else 1f, unit, Offset.Zero) {
                // One pair of wings, individually hinged, with restrained streaks during flight.
                for (side in listOf(-1f, 1f)) scale(side, 1f, Offset.Zero) {
                    val flap = sin(pose.wingPhase.safe(0f))
                    rotate(-12f + flap * (7f + flight * 28f), Offset(.12f, -.03f)) {
                        path.reset(); path.moveTo(.12f, -.03f)
                        path.cubicTo(.24f, -.46f, .56f, -.72f, .66f, -.57f)
                        path.cubicTo(.84f, -.30f, .42f, -.04f, .12f, -.03f); path.close()
                        drawPath(path, wingFill)
                        drawPath(path, Color(0xFF9CCCD0), style = Stroke(.012f))
                        for (i in 0..2) {
                            path.reset(); path.moveTo(.16f, -.07f)
                            path.quadraticBezierTo(.38f, -.19f - i * .09f, .63f - i * .075f, -.38f - i * .095f)
                            drawPath(path, Color(0x7794B5BD), style = Stroke(.007f))
                        }
                        path.reset(); path.moveTo(.34f, -.21f); path.lineTo(.43f, -.33f); path.lineTo(.43f, -.45f)
                        drawPath(path, Color(0x6683A7B2), style = Stroke(.006f))
                        if (flight > .2f) drawArc(Color(0x559BD5D9), 230f, 60f, false,
                            Offset(.48f, -.66f), Size(.28f, .25f), style = Stroke(.009f))
                    }
                }
                // Two walking legs with knees and shoes; rear arms remain visibly separate.
                for (side in listOf(-1f, 1f)) {
                    val ankle = Offset(side * (.19f + flight * .07f) + step * side * .10f,
                        .64f - flight * .16f - maxOf(0f, step * side) * .10f)
                    limb(this, Offset(side * .12f, .37f), Offset(side * .18f - step * side * .07f, .52f), ankle, .055f)
                    drawOval(ink, ankle + Offset(-.075f, -.016f), Size(.17f, .065f))
                    drawOval(shell, ankle + Offset(-.06f, -.018f), Size(.14f, .047f))
                    val rearHand = Offset(side * (.40f + flight * .07f), .28f - flight * .18f - side * step * .04f)
                    limb(this, Offset(side * .15f, .18f), Offset(side * .34f, .30f), rearHand, .042f)
                    hand(this, rearHand, .038f)
                }
                drawOval(ink, Offset(-.25f, .02f), Size(.50f, .47f))
                drawOval(bodyFill, Offset(-.226f, .035f), Size(.452f, .432f))
                for (y in listOf(.17f, .285f, .38f)) {
                    path.reset(); path.moveTo(-.208f + (y - .17f) * .24f, y)
                    path.quadraticBezierTo(0f, y + .08f, .208f - (y - .17f) * .24f, y)
                    drawPath(path, Color(0xFF294E40), style = Stroke(.030f, cap = StrokeCap.Round))
                }
                drawOval(Color(0x448DCA82), Offset(-.17f, .06f), Size(.11f, .26f))
                // Front shoulders and hands: right hand anchors the optional habitat prop.
                for (side in listOf(-1f, 1f)) {
                    val held = side > 0 && pose.holding != null
                    val end = if (held || (side > 0 && handDrop > .001f)) Offset(handX, .075f + handDrop) else Offset(side * (.43f + reach * .14f),
                        .075f - reach * .24f + side * step * .05f - flight * .08f)
                    limb(this, Offset(side * .21f, -.01f), Offset(side * .36f + if (side > 0) (handX - .39f) * .5f else 0f, -.055f - reach * .08f + if (side > 0) handDrop * .55f else 0f), end, .051f)
                    if (held) heldProp(this, pose.holding, end)
                    hand(this, end, .043f)
                }
                // Oversized face and short arista antennae keep the silhouette fly-like.
                drawOval(ink, Offset(-.335f, -.47f), Size(.67f, .58f))
                drawOval(headFill, Offset(-.316f, -.452f), Size(.632f, .55f))
                for (side in listOf(-1f, 1f)) scale(side, 1f, Offset.Zero) {
                    rotate(frame.earLag.coerceIn(-30f, 30f) * side * .25f - frame.earLift * .12f, Offset(.10f, -.43f)) {
                        limb(this, Offset(.10f, -.43f), Offset(.12f, -.51f), Offset(.16f, -.56f), .023f)
                        drawOval(shine, Offset(.125f, -.584f), Size(.065f, .051f))
                        for (i in 0..2) drawLine(shine, Offset(.16f + .007f * i, -.56f - .012f * i),
                            Offset(.21f + .008f * i, -.572f - .019f * i), .008f, StrokeCap.Round)
                    }
                }
                eye(this, Offset(-.157f, -.258f), frame.leftOpen, frame)
                eye(this, Offset(.157f, -.258f), frame.rightOpen, frame)
                // Single smiling lip; speech opens it without creating a snout or tube.
                val mouthY = -.005f
                val curve = .035f + frame.smile.coerceIn(0f, 1f) * .024f - frame.concern.coerceIn(0f, 1f) * .045f
                val opening = frame.mouthOpen.coerceIn(0f, 1f) * .090f
                path.reset(); path.moveTo(-.074f, mouthY)
                path.cubicTo(-.036f, mouthY + curve, .036f, mouthY + curve, .074f, mouthY)
                if (opening > .004f) {
                    path.cubicTo(.055f, mouthY + opening + .035f, -.055f, mouthY + opening + .035f, -.074f, mouthY)
                    path.close(); drawPath(path, Color(0xFF142327))
                    drawOval(Color(0xFFC57375), Offset(-.029f, mouthY + opening * .58f), Size(.058f, opening * .32f))
                }
                drawPath(path, Color(0xFFA4BCAF), style = Stroke(.012f, cap = StrokeCap.Round))
            }
        }
    }

    private fun limb(scope: DrawScope, root: Offset, joint: Offset, end: Offset, width: Float) = with(scope) {
        path.reset(); path.moveTo(root.x, root.y); path.lineTo(joint.x, joint.y); path.lineTo(end.x, end.y)
        drawPath(path, ink, style = Stroke(width + .016f, cap = StrokeCap.Round))
        drawPath(path, shell, style = Stroke(width, cap = StrokeCap.Round))
        drawCircle(shine.copy(alpha = .5f), width * .32f, joint + Offset(-.006f, -.006f))
    }

    private fun hand(scope: DrawScope, at: Offset, radius: Float) = with(scope) {
        drawCircle(ink, radius + .008f, at); drawCircle(shell, radius, at)
        drawCircle(shine, radius * .32f, at + Offset(-radius * .22f, -radius * .35f))
        drawLine(ink, at + Offset(.008f, .012f), at + Offset(.030f, -.004f), .008f, StrokeCap.Round)
    }

    private fun eye(scope: DrawScope, at: Offset, openness: Float, f: LumenFrame) = with(scope) {
        translate(at.x, at.y) {
            val open = if (openness.isFinite()) openness.coerceIn(0f, 1f) else 1f
            val ry = .018f + .18f * open
            drawOval(ink, Offset(-.156f, -ry - .012f), Size(.312f, ry * 2 + .024f))
            eyeClip.reset(); eyeClip.addOval(androidx.compose.ui.geometry.Rect(-.143f, -ry, .143f, ry))
            clipPath(eyeClip) {
                drawRect(eyeFill, Offset(-.15f, -.21f), Size(.30f, .42f))
                // Fixed diamond facets: bounded geometry, no per-frame random texture.
                for (i in -5..5) {
                    val d = i * .056f
                    drawLine(Color(0x33872139), Offset(-.20f, d - .20f), Offset(.20f, d + .20f), .004f)
                    drawLine(Color(0x337F1736), Offset(-.20f, d + .20f), Offset(.20f, d - .20f), .004f)
                }
                val pupil = Offset(f.gazeX.coerceIn(-1f, 1f) * .034f, f.gazeY.coerceIn(-1f, 1f) * .033f)
                drawOval(Color(0xFF33292C), pupil + Offset(-.054f, -.066f), Size(.108f, .132f))
                drawCircle(Color(0xFFFFF5D7), .027f, pupil + Offset(-.014f, -.026f))
                drawCircle(Color(0xBFFFFFFF), .011f, pupil + Offset(.020f, .022f))
                drawOval(Color(0x66FFF7DC), Offset(-.103f, -.15f), Size(.042f, .070f))
            }
        }
    }

    private fun heldProp(scope: DrawScope, kind: String?, hand: Offset) = with(scope) {
        translate(hand.x - .025f, hand.y - .14f) {
            if (kind == "book") {
                rotate(-12f, Offset(.02f, .12f)) {
                    drawRoundRect(Color(0xFFBE7E52), Offset.Zero, Size(.16f, .22f), androidx.compose.ui.geometry.CornerRadius(.014f))
                    drawRect(Color(0xFFEDDFC0), Offset(.022f, .02f), Size(.126f, .18f))
                    drawRect(Color(0xFF517F7C), Offset(.012f, .004f), Size(.13f, .202f))
                    drawLine(Color(0xFFE6C486), Offset(.033f, .05f), Offset(.118f, .05f), .012f)
                    drawCircle(Color(0xFFD9B773), .025f, Offset(.077f, .107f))
                }
            } else {
                path.reset(); path.moveTo(0f, 0f); path.lineTo(.14f, 0f); path.lineTo(.12f, .20f); path.lineTo(.02f, .20f); path.close()
                drawPath(path, Color(0x668CD1DC))
                drawPath(path, Color(0xFFCEEDF0), style = Stroke(.008f))
                drawRect(Color(0x887CCCDD), Offset(.024f, .09f), Size(.092f, .10f))
                drawOval(Color(0xBBD0F2F0), Offset(.019f, .075f), Size(.10f, .025f))
                drawLine(Color(0xDDFFFFFF), Offset(.024f, .03f), Offset(.032f, .075f), .008f, StrokeCap.Round)
            }
        }
    }
}
