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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The "Neo" eye: a realistic cat eye, drawn instead of the capsule eye in
 * [AvatarRenderer] when [AvatarStyle.NEO] is selected. It takes exactly the
 * same arguments as the classic drawEye, so the renderer only branches on the
 * style and nothing upstream — poses, gaze, blinks — has to change.
 *
 * Two things about the host face drive almost every constant here, and getting
 * them wrong is what a first attempt looked like on a device:
 *
 *  - `EYE_COLOR` is `0xFFCFF9FF`, which is very nearly white. Deriving iris
 *    tones from it by lightening blows every stop out to white and the eye
 *    reads as a headlight. The iris therefore carries its own saturated
 *    palette, and only borrows the caller's ALPHA so face dimming still works.
 *  - The caller's eye box is TALLER than it is wide (`eyeW` 0.19s against
 *    `baseEyeH` 0.24s), because the classic eye is an upright capsule. A cat's
 *    eye is the other way round. Building the aperture straight from those
 *    numbers gives an egg, so the width here is derived from the height and
 *    the height is compressed.
 */

private val NEO_RIM = Color(0xFF04100E)
private val NEO_SCLERA = Color(0xFF061512)
private val NEO_PUPIL = Color(0xFF05171B)

// Saturated on purpose: these are what make it read as an iris rather than a
// lamp. Kept in the cyan-green family so it still belongs to this face.
private val NEO_IRIS_HI = Color(0xFF9DF0DF)
private val NEO_IRIS_MID = Color(0xFF32B49B)
private val NEO_IRIS_LOW = Color(0xFF07332F)

internal fun DrawScope.drawEyeNeo(
    side: Float,
    tiltDeg: Float,
    cx: Float,
    eyeCy: Float,
    s: Float,
    eyeDX: Float,
    eyeW: Float,
    baseEyeH: Float,
    eyeH: Float,
    blink: Float,
    pose: AvatarPose,
    pupilSize: Float,
    gazeX: Float,
    gazeY: Float,
    gazeUp: Float,
    eyeColor: Color,
) {
    val ecx = cx + side * eyeDX
    val center = Offset(ecx, eyeCy)
    val a = eyeColor.alpha

    // How open the eye is, 0..1, recovered from the height the caller passed.
    val open = (eyeH / baseEyeH.coerceAtLeast(0.001f)).coerceIn(0f, 1.3f)
    val open01 = open.coerceIn(0f, 1f)

    rotate(tiltDeg, pivot = center) {
        if (pose.happyEyes && blink > 0.5f) {
            // Happy "^^" arcs. Kept identical to the classic renderer so the two
            // styles stay behaviourally the same face.
            drawArc(
                color = eyeColor,
                startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(ecx - eyeW / 2f, eyeCy - baseEyeH * 0.1f),
                size = Size(eyeW, baseEyeH * 0.28f),
                style = Stroke(width = eyeW * 0.3f, cap = StrokeCap.Round),
            )
            return@rotate
        }

        // Width comes from the BASE height so it stays put through a blink, then
        // narrows with closing on its own. Deriving it from the live height
        // instead would shrink the eye in both axes and read as a zoom-out.
        val hw = max(eyeW * 0.52f, baseEyeH * 0.66f * 0.88f) * (0.74f + 0.26f * open01)

        // Height compressed against the caller's upright capsule, so the
        // finished aperture is wider than it is tall like a real cat's.
        val totalH = eyeH * 0.66f

        // The two lids are shaped independently: the upper runs as a near
        // straight chord, the lower bulges. A shared profile can only ever make
        // a leaf or a diamond, never a cat. As the eye closes the asymmetry has
        // to unwind, or the closed lid lands below the eye's centre.
        val closing = 1f - open01
        val upBase = totalH * 0.40f
        val loBase = totalH * 0.60f
        val mid = (upBase + loBase) * 0.5f
        val up = (upBase + (mid - upBase) * closing).coerceAtLeast(totalH * 0.05f)
        val lo = (loBase + (mid - loBase) * closing).coerceAtLeast(totalH * 0.05f)

        // Outer corner rides above the inner one; the slant relaxes as the eye
        // shuts, because a closed lid is a flat line rather than a dagger.
        val slant = Math.toRadians(
            (-side * 8f * (0.30f + 0.70f * open01)).toDouble()
        ).toFloat()

        val aperture = almondPath(center, hw, up, lo, side * 0.05f, slant)

        // Soft bed of light. A filled circle keeps a hard rim however low its
        // alpha, so this is a radial gradient instead.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(eyeColor.copy(alpha = 0.20f * a), Color.Transparent),
                center = center,
                radius = hw * 2.2f,
            ),
            radius = hw * 2.2f,
            center = center,
        )

        clipPath(aperture) {
            // Eyeball kept dark, so the iris is what carries the light.
            drawRect(
                color = NEO_SCLERA.copy(alpha = a),
                topLeft = Offset(ecx - hw * 2f, eyeCy - totalH * 2f),
                size = Size(hw * 4f, totalH * 4f),
            )

            if (blink > 0.3f) {
                // The iris nearly fills the aperture — a small disc floating in
                // white space is a human eye, not a cat's.
                val ir = min(hw * 0.72f, (up + lo) * 0.62f)
                val ix = ecx + gazeX * hw * 0.26f
                val iy = eyeCy + gazeY * (up + lo) * 0.14f - gazeUp * (up + lo) * 0.20f
                val iris = Offset(ix, iy)

                // Bright at the pupil, dark at the limbus. A flat disc reads as
                // a sticker; the gradient is what makes it a volume.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NEO_IRIS_HI.copy(alpha = a),
                            NEO_IRIS_MID.copy(alpha = a),
                            NEO_IRIS_LOW.copy(alpha = a),
                        ),
                        center = iris,
                        radius = ir,
                    ),
                    radius = ir,
                    center = iris,
                )

                // Radial fibres. Invisible individually; collectively they stop
                // the iris looking like flat paint, and they are the single most
                // characteristic feature of a real cat eye.
                val fibres = 34
                for (i in 0 until fibres) {
                    val ang = (i.toFloat() / fibres) * (2f * Math.PI).toFloat() + 0.11f
                    val dx = cos(ang)
                    val dy = sin(ang)
                    drawLine(
                        color = NEO_IRIS_HI.copy(alpha = 0.18f * a),
                        start = Offset(ix + dx * ir * 0.30f, iy + dy * ir * 0.30f),
                        end = Offset(ix + dx * ir * 0.96f, iy + dy * ir * 0.96f),
                        strokeWidth = s * 0.0020f,
                    )
                }

                // Bounce light low in the iris — the strongest "wet sphere" cue.
                drawOval(
                    color = NEO_IRIS_HI.copy(alpha = 0.22f * a),
                    topLeft = Offset(ix - ir * 0.52f, iy + ir * 0.22f),
                    size = Size(ir * 1.04f, ir * 0.42f),
                )

                // Slit pupil: a spindle with knife points, run past both lids so
                // the aperture crops it. A rounded blob reads as a camera stop.
                val pw = ir * (0.09f + 0.42f * pupilSize.coerceIn(0f, 1f))
                drawPath(
                    slitPath(iris, pw, -up * 1.2f, lo * 1.2f),
                    NEO_PUPIL.copy(alpha = a),
                )

                // Shadow cast by the upper lid. A filled oval leaves a hard
                // horizontal edge across the iris that reads as a bar, so this
                // is a gradient that fades out before it reaches the pupil.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                        startY = eyeCy - up,
                        endY = eyeCy + lo * 0.15f,
                    ),
                    topLeft = Offset(ecx - hw * 1.6f, eyeCy - up),
                    size = Size(hw * 3.2f, up + lo * 0.15f),
                )

                // Catchlight: small and high. Oversized, it stops being a
                // reflection and becomes a painted dot.
                drawOval(
                    color = Color.White.copy(alpha = 0.85f * a),
                    topLeft = Offset(ix + side * ir * 0.22f - ir * 0.17f, iy - ir * 0.60f),
                    size = Size(ir * 0.34f, ir * 0.26f),
                )
                drawOval(
                    color = Color(0xFFCCE4FF).copy(alpha = 0.34f * a),
                    topLeft = Offset(ix - side * ir * 0.42f, iy + ir * 0.20f),
                    size = Size(ir * 0.17f, ir * 0.17f),
                )

                // Camera privacy mark, kept identical to the classic renderer.
                if (pose.cameraOffMark) {
                    val xs = max(pw * 0.9f, ir * 0.22f)
                    drawLine(
                        color = eyeColor.copy(alpha = 0.9f * a),
                        start = Offset(ix - xs, iy - xs), end = Offset(ix + xs, iy + xs),
                        strokeWidth = s * 0.008f, cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = eyeColor.copy(alpha = 0.9f * a),
                        start = Offset(ix + xs, iy - xs), end = Offset(ix - xs, iy + xs),
                        strokeWidth = s * 0.008f, cap = StrokeCap.Round,
                    )
                }
            }
        }

        // The rim goes on OVER the contents, so it reads as the wet edge of the
        // lid rather than an outline drawn around a shape. It stays DARK: a
        // light stroke here turns the eye into a sticker, which is exactly what
        // borrowing the near-white EYE_COLOR produced on the first attempt.
        drawPath(aperture, NEO_RIM.copy(alpha = a), style = Stroke(width = hw * 0.20f))
        // A whisper of the face colour along the lid, for the wet highlight only.
        drawPath(
            aperture,
            eyeColor.copy(alpha = 0.22f * a),
            style = Stroke(width = hw * 0.045f),
        )
    }
}

// ── geometry ────────────────────────────────────────────────────────────────

/**
 * Almond aperture. [topFlat] holds the upper lid near full height across its
 * width before dropping to the corners; it must stay above 1 or the crest
 * develops a tent point, because |x| has a kink at zero.
 */
private fun almondPath(
    center: Offset,
    halfWidth: Float,
    upperHeight: Float,
    lowerHeight: Float,
    peakShift: Float,
    slantRad: Float,
    topFlat: Float = 4.2f,
    botRound: Float = 0.60f,
    segments: Int = 44,
): Path {
    val pts = ArrayList<Offset>(segments * 2)
    for (i in 0..segments) {
        val x = -halfWidth + (2f * halfWidth) * i / segments
        val u = (1f - abs(x / halfWidth).pow(topFlat)).coerceIn(0f, 1f)
        pts.add(Offset(x, -upperHeight * u.pow(0.86f) * (1f + peakShift * (x / halfWidth))))
    }
    // Endpoints skipped so the corner vertices are not duplicated.
    for (i in segments - 1 downTo 1) {
        val x = -halfWidth + (2f * halfWidth) * i / segments
        val u = (1f - abs(x / halfWidth).pow(2.1f)).coerceIn(0f, 1f)
        pts.add(Offset(x, lowerHeight * u.pow(botRound)))
    }
    return buildPath(pts, center, slantRad)
}

/** Vertical slit with pointed ends. */
private fun slitPath(center: Offset, halfWidth: Float, top: Float, bottom: Float): Path {
    val steps = 24
    val pts = ArrayList<Offset>(steps * 2)
    fun w(t: Float) = halfWidth * (1f - abs(t * 2f - 1f).pow(1.7f)).coerceAtLeast(0f).pow(0.48f)
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        pts.add(Offset(w(t), top + (bottom - top) * t))
    }
    for (i in steps - 1 downTo 1) {
        val t = i.toFloat() / steps
        pts.add(Offset(-w(t), top + (bottom - top) * t))
    }
    return buildPath(pts, center, 0f)
}

private fun buildPath(pts: List<Offset>, center: Offset, rot: Float): Path {
    val p = Path()
    if (pts.isEmpty()) return p
    val c = if (rot != 0f) cos(rot) else 1f
    val s = if (rot != 0f) sin(rot) else 0f
    pts.forEachIndexed { i, o ->
        val x = center.x + (o.x * c - o.y * s)
        val y = center.y + (o.x * s + o.y * c)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}
